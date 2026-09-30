package com.worldeater;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Durable shot; tickets are transient, while geometry and cursor checkpoints survive restart. */
public final class LaserShot implements AutoCloseable {
    public enum Stage { ACQUIRE, CRATER, WAVE, FINALE, DONE }
    private final UUID owner;
    private final String name;
    private final GameType originalMode;
    private final ResourceLocation dimension;
    private final BlockPos station, planetCenter;
    private BlockPos target;
    private final Vec3 hit;
    private final int radius, planetRadius;
    private final double speed, craterRadius;
    private Stage stage = Stage.ACQUIRE;
    private int age, finaleAge, lastBlocks, lastScans;
    private long removed;
    private double wave;
    private boolean paused;
    private final PlanetChunkLease lease = new PlanetChunkLease();
    private ExpandingShellCursor shell;
    private ShockwaveCursor terrain;
    private CompoundTag savedCursor;

    public LaserShot(ServerPlayer player, ResourceLocation dimension, LaserAim.Target aim, CompoundTag planet, int radius, double speed) {
        owner = player.getUUID(); name = player.getGameProfile().getName(); this.dimension = dimension;
        originalMode = GameType.byId(player.getData(OrbitSession.STATE).getInt("GameMode"));
        station = BlockPos.of(player.getData(OrbitSession.STATE).getLong("Station"));
        planetCenter = BlockPos.of(planet.getLong("Center")); planetRadius = planet.getInt("Radius");
        target = new BlockPos(aim.x(), 0, aim.z()); hit = aim.hit();
        this.radius = Math.clamp(radius, 4, WorldEaterConfig.LASER_HARD_MAX_RADIUS); this.speed = speed;
        craterRadius = Math.clamp((double) planetRadius * radius / Math.max(1, planet.getInt("ScanRadius")), 4, planetRadius * 0.8);
    }
    private LaserShot(CompoundTag tag) {
        owner = tag.getUUID("Owner"); name = tag.getString("Name"); originalMode = GameType.byId(tag.getInt("Mode"));
        dimension = ResourceLocation.parse(tag.getString("Dimension"));
        station = BlockPos.of(tag.getLong("Station")); planetCenter = BlockPos.of(tag.getLong("PlanetCenter"));
        planetRadius = Math.clamp(tag.getInt("PlanetRadius"), 1, 100);
        target = BlockPos.of(tag.getLong("Target")); hit = new Vec3(tag.getDouble("HitX"), tag.getDouble("HitY"), tag.getDouble("HitZ"));
        radius = Math.clamp(tag.getInt("Radius"), 4, WorldEaterConfig.LASER_HARD_MAX_RADIUS);
        speed = Math.clamp(tag.getDouble("Speed"), 1, 256); craterRadius = Math.clamp(tag.getDouble("CraterRadius"), 1, planetRadius);
        stage = Stage.values()[Math.clamp(tag.getInt("Stage"), 0, Stage.values().length - 1)];
        age = Math.max(0, tag.getInt("Age")); finaleAge = Math.max(0, tag.getInt("FinaleAge"));
        wave = Math.clamp(tag.getDouble("Wave"), 0, radius); removed = Math.max(0, tag.getLong("Removed")); paused = tag.getBoolean("Paused");
        if (tag.contains("Cursor")) savedCursor = tag.getCompound("Cursor").copy();
    }
    public static LaserShot load(CompoundTag tag) { return new LaserShot(tag); }
    public UUID owner() { return owner; }
    public int radius() { return radius; }
    public Stage stage() { return stage; }
    public int lastBlocks() { return lastBlocks; }
    public int lastScans() { return lastScans; }
    public boolean ticketHeld() { return lease.held(); }
    public boolean paused() { return paused; }
    public void pause() { paused = true; close(); }
    public void resume() { paused = false; }
    public Vec3 muzzle() { return Vec3.atCenterOf(station.offset(0, -2, -14)); }
    public void tick(MinecraftServer server, int blockBudget, int scanBudget, long deadline) {
        lastBlocks = 0; lastScans = 0;
        if (paused || stage == Stage.DONE) return;
        ServerLevel source = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        ServerLevel orbit = server.getLevel(OrbitSession.ORBIT);
        if (source == null || source == orbit || orbit == null) throw new IllegalStateException("Shot dimension unavailable");
        ServerPlayer viewer = server.getPlayerList().getPlayer(owner);
        age++;
        if (viewer != null && stage.ordinal() <= Stage.CRATER.ordinal() && age <= 40 && age % 4 == 0) LaserControl.beam(viewer, muzzle(), hit);
        if (stage == Stage.WAVE) wave = Math.min(radius, wave + speed / 20);
        if (stage == Stage.FINALE) {
            finaleAge++;
            if (viewer != null && finaleAge <= 40 && finaleAge % 10 == 0) LaserControl.explosion(viewer, Vec3.atCenterOf(planetCenter), planetRadius);
        }
        var actor = FakePlayerFactory.get(source, new GameProfile(owner, name));
        actor.setGameMode(originalMode);
        while (lastBlocks < blockBudget && lastScans++ < scanBudget && System.nanoTime() < deadline) {
            if (stage == Stage.ACQUIRE) {
                var chunk = lease.request(source, new ChunkPos(target));
                if (chunk == null) break;
                target = new BlockPos(target.getX(), Math.max(source.getMinBuildHeight(), chunk.getHeight(Heightmap.Types.WORLD_SURFACE, target.getX(), target.getZ())), target.getZ());
                change(Stage.CRATER);
            } else if (stage == Stage.CRATER || stage == Stage.FINALE) {
                if (shell == null) {
                    shell = PlanetProjection.shell(planetCenter, planetRadius);
                    if (savedCursor != null) { shell.restore(savedCursor); savedCursor = null; }
                }
                BlockPos pos = shell.peek();
                if (pos == null) {
                    if (!shell.finished()) continue;
                    if (stage == Stage.CRATER) change(Stage.WAVE);
                    else if (finaleAge >= 40) {
                        change(Stage.DONE);
                        var data = OrbitStations.get(orbit).planet(owner);
                        data.putBoolean("Complete", false); data.putBoolean("Spent", true);
                        data.put("Shapes", new net.minecraft.nbt.ListTag());
                        OrbitStations.get(orbit).savePlanet(owner, data);
                    } else break;
                } else {
                    if (stage == Stage.CRATER && Vec3.atCenterOf(pos).distanceToSqr(hit) > craterRadius * craterRadius) { shell.advance(); continue; }
                    if (lease.request(orbit, new ChunkPos(pos)) == null) break;
                    if (!orbit.getBlockState(pos).isAir()) { orbit.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE); lastBlocks++; }
                    shell.advance();
                }
            } else if (stage == Stage.WAVE) {
                if (terrain == null) {
                    terrain = new ShockwaveCursor(target, radius);
                    if (savedCursor != null) { terrain.restore(savedCursor); savedCursor = null; }
                }
                if (terrain.finished()) { change(Stage.FINALE); continue; }
                if (terrain.distance() > wave) break;
                var chunk = lease.request(source, terrain.chunk());
                if (chunk == null) break;
                BlockPos pos = terrain.peek(chunk, source.getMinBuildHeight(), source.getMaxBuildHeight());
                if (pos == null) continue;
                var state = chunk.getBlockState(pos);
                actor.setPos(Vec3.atCenterOf(pos));
                if (state.isAir() || !source.getWorldBorder().isWithinBounds(pos)
                        || WorldEaterConfig.SKIP_UNBREAKABLE.get() && state.getDestroySpeed(source, pos) < 0
                        || WorldEaterConfig.SKIP_BLOCK_ENTITIES.get() && state.hasBlockEntity()
                        || !source.mayInteract(actor, pos) || actor.blockActionRestricted(source, pos, originalMode)
                        || NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(source, pos, state, actor)).isCanceled()) {
                    terrain.advance(); continue;
                }
                if (System.nanoTime() >= deadline) break;
                if (source.getBlockState(pos) == state) {
                    if (state.hasBlockEntity()) source.removeBlockEntity(pos);
                    // Keep vanilla heightmap + lighting updates. Suppress neighbor cascades and drops; no direct section writes.
                    if (source.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS)) {
                        lastBlocks++; removed++;
                    }
                }
                terrain.advance();
            } else break;
        }
        // The counter is a cap on processed candidates, not the final failed loop-condition check.
        lastScans = Math.min(lastScans, scanBudget);
    }
    private void change(Stage next) { close(); stage = next; shell = null; terrain = null; savedCursor = null; }
    public Component progress() {
        if (paused) return Component.translatable("message.worldeater.laser_paused");
        return Component.translatable("message.worldeater.laser_progress", Component.translatable("message.worldeater.laser_stage." + stage.name().toLowerCase(java.util.Locale.ROOT)),
                stage.ordinal() >= Stage.FINALE.ordinal() ? 100 : terrain == null ? 0 : terrain.progress(), removed);
    }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Owner", owner); tag.putString("Name", name); tag.putInt("Mode", originalMode.getId()); tag.putString("Dimension", dimension.toString());
        tag.putLong("Station", station.asLong()); tag.putLong("PlanetCenter", planetCenter.asLong()); tag.putInt("PlanetRadius", planetRadius);
        tag.putLong("Target", target.asLong()); tag.putDouble("HitX", hit.x); tag.putDouble("HitY", hit.y); tag.putDouble("HitZ", hit.z);
        tag.putInt("Radius", radius); tag.putDouble("Speed", speed); tag.putDouble("CraterRadius", craterRadius); tag.putInt("Stage", stage.ordinal());
        tag.putInt("Age", age); tag.putInt("FinaleAge", finaleAge); tag.putDouble("Wave", wave); tag.putLong("Removed", removed); tag.putBoolean("Paused", paused);
        if (terrain != null) tag.put("Cursor", terrain.save()); else if (shell != null) tag.put("Cursor", shell.save());
        else if (savedCursor != null) tag.put("Cursor", savedCursor.copy());
        return tag;
    }
    @Override public void close() { lease.close(); }
}
