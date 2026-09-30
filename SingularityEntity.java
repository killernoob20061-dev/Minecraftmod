package com.worldeater;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class SingularityEntity extends Projectile {
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.FLOAT);
    private boolean anchored;
    private BlockPos anchor;
    private UUID ownerId;
    private String ownerName = "WorldEater";
    private GameType ownerMode = GameType.SURVIVAL;
    private ExpandingShellCursor cursor;
    private CompoundTag savedCursor;
    private double preciseRadius = 0.5;
    private double feedGrowth;
    private int flightAge, pendingMass;
    private long anchoredAt;
    private BlockPos frontier;
    private int lastConsumedBlocks, lastScannedPositions;
    private int lerpSteps;
    private Vec3 lerpTarget = Vec3.ZERO;

    public SingularityEntity(EntityType<? extends SingularityEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(RADIUS, 0.5F); }
    public float radius() { return entityData.get(RADIUS); }
    public boolean isAnchored() { return anchored; }
    public int currentShell() { return cursor == null ? 0 : cursor.shell(); }
    public int lastConsumedBlocks() { return lastConsumedBlocks; }
    public int lastScannedPositions() { return lastScannedPositions; }

    @Override public void setOwner(Entity owner) {
        super.setOwner(owner);
        if (owner instanceof ServerPlayer player) {
            ownerId = player.getUUID(); ownerName = player.getGameProfile().getName();
            ownerMode = player.gameMode.getGameModeForPlayer();
        }
    }

    @Override public void tick() {
        if (level().isClientSide) { tickVisuals(); return; }
        long deadline = System.nanoTime() + (long) (WorldEaterConfig.TICK_BUDGET_MS.get() * 1_000_000);
        super.tick();
        lastConsumedBlocks = 0; lastScannedPositions = 0;
        ServerLevel server = (ServerLevel) level();
        if (!anchored) { fly(server); return; }
        if (!SingularityAnchors.get(server).contains(getUUID())) { discard(); return; }
        int lifetime = WorldEaterConfig.LIFETIME.get();
        if (lifetime > 0 && server.getGameTime() - anchoredAt >= lifetime) { discard(); return; }
        setDeltaMovement(Vec3.ZERO);
        if (cursor == null) {
            int shell = savedCursor == null ? 1 : Math.clamp(savedCursor.getInt("Shell"), 1, (int) WorldEaterConfig.HARD_MAX_RADIUS);
            cursor = newCursor(server, shell);
            if (savedCursor != null) { cursor.restore(savedCursor); savedCursor = null; }
        }
        ServerPlayer actor = actor(server);
        flushMass(server);
        affectEntities(server, actor, deadline);
        if (actor == null || !mayConsumeBlocks(server, actor)) return;
        double limit = radiusLimit(server);
        if (cursor.finished()) {
            releaseWorkTicket(server);
            if (cursor.shell() >= limit) return;
            cursor = newCursor(server, cursor.shell() + 1);
        }
        // Never let the visual sphere race arbitrarily far ahead of the bounded destruction work.
        preciseRadius = Math.min(Math.min(limit, cursor.shell()),
                preciseRadius + WorldEaterConfig.PASSIVE_GROWTH.get() / 20.0 + feedGrowth);
        feedGrowth = 0;
        entityData.set(RADIUS, (float) preciseRadius);
        if (preciseRadius + 1.0E-6 >= cursor.shell()) consumeShell(server, actor, deadline);
    }

    private void fly(ServerLevel server) {
        Vec3 next = position().add(getDeltaMovement());
        BlockPos nextBlock = BlockPos.containing(next);
        if (!server.hasChunkAt(nextBlock) || !server.isInWorldBounds(nextBlock)
                || !server.getWorldBorder().isWithinBounds(nextBlock)) {
            anchorAt(blockPosition());
            return;
        }
        var hit = server.clip(new ClipContext(position(), next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.BLOCK) { anchorAt(hit.getBlockPos()); return; }
        setPos(next);
        if (++flightAge >= WorldEaterConfig.FLIGHT_TICKS.get()) anchorAt(blockPosition());
    }

    /** Called on impact; the anchor and cursor are independent of the firing player's lifetime. */
    public void anchorAt(BlockPos pos) {
        if (!(level() instanceof ServerLevel server) || anchored) return;
        anchor = pos.immutable(); anchored = true; anchoredAt = server.getGameTime();
        setPos(Vec3.atCenterOf(anchor)); setDeltaMovement(Vec3.ZERO);
        preciseRadius = 1.0; entityData.set(RADIUS, 1.0F);
        cursor = newCursor(server, 1);
        SingularityAnchors.get(server).add(getUUID(), anchor);
        ChunkPos chunk = new ChunkPos(anchor);
        SingularityTickets.CONTROLLER.forceChunk(server, getUUID(), chunk.x, chunk.z, true, true);
    }

    private ExpandingShellCursor newCursor(ServerLevel server, int shell) {
        var border = server.getWorldBorder();
        return new ExpandingShellCursor(anchor, shell, server.getMinBuildHeight(), server.getMaxBuildHeight(),
                (int) Math.ceil(border.getMinX()), (int) Math.ceil(border.getMaxX()) - 1,
                (int) Math.ceil(border.getMinZ()), (int) Math.ceil(border.getMaxZ()) - 1);
    }

    private double radiusLimit(ServerLevel server) {
        double configured = WorldEaterConfig.MAX_RADIUS.get();
        if (configured > 0) return Math.max(1, Math.floor(configured));
        var border = server.getWorldBorder();
        double x = Math.max(Math.abs(border.getMinX() - getX()), Math.abs(border.getMaxX() - getX()));
        double z = Math.max(Math.abs(border.getMinZ() - getZ()), Math.abs(border.getMaxZ() - getZ()));
        double y = Math.max(Math.abs(server.getMinBuildHeight() - getY()), Math.abs(server.getMaxBuildHeight() - getY()));
        return Math.min(WorldEaterConfig.HARD_MAX_RADIUS, Math.ceil(Math.sqrt(x * x + y * y + z * z)));
    }

    private ServerPlayer actor(ServerLevel server) {
        if (ownerId == null) return null;
        ServerPlayer online = server.getServer().getPlayerList().getPlayer(ownerId);
        if (online != null && online.level() == server && online.isAlive()) return online;
        // Cached fake owners support automation/testing; ordinary offline players use their saved UUID for protection hooks.
        if (getOwner() instanceof FakePlayer fake && fake.level() == server) return fake;
        FakePlayer actor = FakePlayerFactory.get(server, new GameProfile(ownerId, ownerName));
        actor.setGameMode(ownerMode);
        actor.setPos(position());
        return actor;
    }

    public static boolean mayConsumeBlocks(ServerLevel level, ServerPlayer actor) {
        return WorldEaterConfig.DESTROY_BLOCKS.get() && !actor.isSpectator()
                && (!WorldEaterConfig.OP_ONLY.get() || level.getServer().isSingleplayer() || actor.hasPermissions(2));
    }

    private void consumeShell(ServerLevel server, ServerPlayer actor, long deadline) {
        while (!cursor.finished() && lastScannedPositions < WorldEaterConfig.SCANS_PER_TICK.get()
                && lastConsumedBlocks < WorldEaterConfig.BLOCKS_PER_TICK.get() && System.nanoTime() < deadline) {
            lastScannedPositions++;
            BlockPos pos = cursor.peek();
            if (pos == null) continue;
            if (!server.isInWorldBounds(pos) || !server.getWorldBorder().isWithinBounds(pos)) { cursor.advance(); continue; }
            if (!prepareChunk(server, pos)) break; // Keep this exact candidate pending across ticks/saves.
            frontier = pos;
            if (System.nanoTime() >= deadline) break;
            BlockState state = server.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()
                    || (WorldEaterConfig.SKIP_UNBREAKABLE.get() && state.getDestroySpeed(server, pos) < 0)
                    || (WorldEaterConfig.SKIP_BLOCK_ENTITIES.get() && state.hasBlockEntity())
                    || !server.mayInteract(actor, pos)
                    || actor.blockActionRestricted(server, pos, actor.gameMode.getGameModeForPlayer())) {
                cursor.advance(); continue;
            }
            if (NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(server, pos, state, actor)).isCanceled()) {
                cursor.advance(); continue;
            }
            if (System.nanoTime() >= deadline) break;
            if (server.getBlockState(pos) == state && server.destroyBlock(pos, WorldEaterConfig.DROP_BLOCKS.get(), actor))
                lastConsumedBlocks++;
            cursor.advance();
        }
        if (lastConsumedBlocks > 0) consumeMass(server, lastConsumedBlocks * WorldEaterConfig.BLOCK_MASS.get());
    }

    private boolean prepareChunk(ServerLevel server, BlockPos pos) {
        if (server.hasChunkAt(pos)) return true;
        if (!SingularityTickets.takeLoadSlot(server)) return false;
        releaseWorkTicket(server);
        ChunkPos chunk = new ChunkPos(pos);
        SingularityTickets.CONTROLLER.forceChunk(server, getUUID(), chunk.x, chunk.z, true, false);
        SingularityAnchors.get(server).setWorkChunk(getUUID(), chunk.toLong());
        return server.hasChunkAt(pos);
    }

    private void releaseWorkTicket(ServerLevel server) {
        SingularityAnchors data = SingularityAnchors.get(server);
        Long value = data.workChunk(getUUID());
        if (value == null) return;
        ChunkPos chunk = new ChunkPos(value);
        SingularityTickets.CONTROLLER.forceChunk(server, getUUID(), chunk.x, chunk.z, false, false);
        data.setWorkChunk(getUUID(), null);
    }

    private void affectEntities(ServerLevel server, ServerPlayer actor, long deadline) {
        if (System.nanoTime() >= deadline) return;
        double pull = radius() * 2.0;
        // Large holes query only a bounded core window or the active frontier, not every entity in an enormous sphere.
        AABB area = frontier != null && tickCount % 2 == 0
                ? new AABB(frontier).inflate(12) : getBoundingBox().inflate(Math.min(pull, 32));
        List<Entity> nearby = new ArrayList<>(64);
        server.getEntities(EntityTypeTest.forClass(Entity.class), area, entity -> entity != this && entity.isAlive()
                && !entity.isSpectator() && !entity.getUUID().equals(ownerId)
                && (entity instanceof LivingEntity || entity instanceof ItemEntity)
                && (actor == null || entity.getRootVehicle() != actor.getRootVehicle())
                && !(entity instanceof Player player && (player.isCreative() || actor == null || !actor.canHarmPlayer(player))), nearby, 64);
        for (Entity entity : nearby) {
            if (System.nanoTime() >= deadline) break;
            Vec3 inward = position().subtract(entity.getBoundingBox().getCenter());
            double distance = inward.length();
            if (distance > pull) continue;
            Vec3 velocity = entity.getDeltaMovement().add(distance < 0.001 ? Vec3.ZERO : inward.scale(0.08 / distance));
            if (velocity.lengthSqr() > 0.64) velocity = velocity.normalize().scale(0.8);
            entity.setDeltaMovement(velocity); entity.hasImpulse = true;
            if (entity instanceof ServerPlayer) entity.hurtMarked = true;
            if (entity instanceof LivingEntity living && distance < Math.max(0.8, radius() * 0.8) && tickCount % 10 == 0) {
                boolean damaged = living.hurt(damageSources().indirectMagic(this, actor), 8.0F);
                if (damaged && !living.isAlive() && !(living instanceof Player)) consumeMass(server, WorldEaterConfig.MOB_MASS.get());
            }
        }
    }

    private void consumeMass(ServerLevel server, int mass) {
        pendingMass = (int) Math.min(WorldEaterConfig.MAX_MASS.get(), (long) pendingMass + mass);
        feedGrowth = Math.min(1, feedGrowth + mass * WorldEaterConfig.GROWTH.get());
        flushMass(server);
    }

    private void flushMass(ServerLevel server) {
        if (ownerId == null || pendingMass == 0) return;
        ServerPlayer player = server.getServer().getPlayerList().getPlayer(ownerId);
        if (player == null && getOwner() instanceof FakePlayer fake) player = fake;
        if (player != null) { Mass.add(player, pendingMass); pendingMass = 0; }
    }

    @Override public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && anchored && level() instanceof ServerLevel server)
            SingularityTickets.release(server, getUUID(), anchor);
        super.remove(reason);
    }

    private void tickVisuals() {
        super.tick();
        if (lerpSteps > 0) { setPos(position().lerp(lerpTarget, 1.0 / lerpSteps)); lerpSteps--; }
        if (tickCount % 4 == 0) {
            double angle = random.nextDouble() * Math.PI * 2;
            double r = radius() * 1.5;
            level().addParticle(ParticleTypes.PORTAL, getX() + Math.cos(angle) * r, getY(), getZ() + Math.sin(angle) * r,
                    -Math.cos(angle) * 0.1, 0.02, -Math.sin(angle) * 0.1);
        }
    }

    @Override public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        lerpTarget = new Vec3(x, y, z); lerpSteps = Math.max(1, steps); setRot(yaw, pitch);
    }
    @Override public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(radius() * 1.6); }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < Math.pow(Math.max(512, radius() * 2.0), 2); }

    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("PreciseRadius", preciseRadius); tag.putDouble("FeedGrowth", feedGrowth);
        tag.putBoolean("Anchored", anchored); tag.putLong("AnchoredAt", anchoredAt);
        tag.putInt("FlightAge", flightAge); tag.putInt("PendingMass", pendingMass);
        if (ownerId != null) tag.putUUID("Shooter", ownerId);
        tag.putString("ShooterName", ownerName); tag.putInt("ShooterMode", ownerMode.getId());
        if (anchor != null) tag.putLong("Anchor", anchor.asLong());
        if (cursor != null) tag.put("ShellCursor", cursor.save());
        else if (savedCursor != null) tag.put("ShellCursor", savedCursor.copy());
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        double saved = tag.contains("PreciseRadius") ? tag.getDouble("PreciseRadius") : 0.5;
        preciseRadius = Double.isFinite(saved) ? Math.clamp(saved, 0.5, WorldEaterConfig.HARD_MAX_RADIUS) : 0.5;
        entityData.set(RADIUS, (float) preciseRadius);
        double growth = tag.getDouble("FeedGrowth"); feedGrowth = Double.isFinite(growth) ? Math.clamp(growth, 0, 1) : 0;
        anchored = tag.getBoolean("Anchored") && tag.contains("Anchor");
        anchor = anchored ? BlockPos.of(tag.getLong("Anchor")) : null;
        anchoredAt = tag.getLong("AnchoredAt"); flightAge = Math.max(0, tag.getInt("FlightAge"));
        pendingMass = Math.clamp(tag.getInt("PendingMass"), 0, 1000000);
        ownerId = tag.hasUUID("Shooter") ? tag.getUUID("Shooter") : (tag.hasUUID("Owner") ? tag.getUUID("Owner") : null);
        if (!tag.getString("ShooterName").isBlank()) ownerName = tag.getString("ShooterName");
        ownerMode = GameType.byId(tag.getInt("ShooterMode"));
        savedCursor = tag.contains("ShellCursor") ? tag.getCompound("ShellCursor").copy() : null;
        cursor = null;
        if (anchored) { setPos(Vec3.atCenterOf(anchor)); setDeltaMovement(Vec3.ZERO); }
    }
}
