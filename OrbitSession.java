package com.worldeater;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Saved return transaction. The original inventory is stored, never copied into the orbit world. */
public final class OrbitSession {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(WorldEater.MOD_ID, "orbit");
    public static final ResourceKey<Level> ORBIT = ResourceKey.create(Registries.DIMENSION, ID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CompoundTag>> STATE = Mass.ATTACHMENTS.register(
            "orbit_return", () -> AttachmentType.builder(() -> new CompoundTag()).serialize(CompoundTag.CODEC).copyOnDeath().build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> CONFIRMATION = Mass.ATTACHMENTS.register(
            "orbit_confirmation", () -> AttachmentType.builder(() -> Long.MIN_VALUE).build());
    private static final Set<UUID> TRANSFERRING = new HashSet<>();

    public static void registerAttachments() { /* Forces attachment declarations before registry events. */ }
    public static boolean active(ServerPlayer player) { return player.getData(STATE).getBoolean("Active"); }

    public static CompoundTag capture(ServerPlayer player, BlockPos station) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Active", true);
        tag.putString("Dimension", player.level().dimension().location().toString());
        tag.putDouble("X", player.getX()); tag.putDouble("Y", player.getY()); tag.putDouble("Z", player.getZ());
        tag.putFloat("Yaw", player.getYRot()); tag.putFloat("Pitch", player.getXRot());
        tag.putLong("Station", station.asLong());
        tag.putInt("GameMode", player.gameMode.getGameModeForPlayer().getId());
        tag.put("Inventory", player.getInventory().save(new ListTag()));
        tag.putInt("SelectedSlot", player.getInventory().selected);
        tag.putFloat("Health", player.getHealth());
        tag.putFloat("Absorption", player.getAbsorptionAmount());
        tag.putInt("Air", player.getAirSupply());
        tag.putInt("Fire", player.getRemainingFireTicks());
        tag.putInt("XPLevel", player.experienceLevel);
        tag.putInt("XPTotal", player.totalExperience);
        tag.putFloat("XPProgress", player.experienceProgress);
        tag.putInt("Mass", Mass.get(player));
        player.getFoodData().addAdditionalSaveData(tag);
        player.getAbilities().addSaveData(tag);
        ListTag effects = new ListTag();
        player.getActiveEffects().forEach(effect -> effects.add(effect.save()));
        tag.put("Effects", effects);
        return tag;
    }

    public static boolean enter(ServerPlayer player) {
        if (active(player)) return false;
        ServerLevel orbit = player.getServer().getLevel(ORBIT);
        if (orbit == null) {
            player.sendSystemMessage(Component.translatable("message.worldeater.orbit_missing"));
            return false;
        }
        BlockPos station = OrbitStations.get(orbit).station(player.getUUID());
        OrbitCockpit.build(orbit, station);
        player.closeContainer(); // Return any carried/crafting stack to inventory before snapshotting.
        player.setData(STATE, capture(player, station));
        player.getServer().getPlayerList().saveAll(); // Durable recovery record before temporary state.
        player.getInventory().clearContent();
        player.getInventory().selected = 0;
        player.getInventory().setItem(0, new ItemStack(WorldEater.DEATH_STAR.get()));
        player.getInventory().setItem(1, new ItemStack(WorldEater.SUPERLASER_TRIGGER.get()));
        player.removeAllEffects();
        player.setGameMode(GameType.ADVENTURE);
        player.getAbilities().invulnerable = true;
        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        player.clearFire();
        player.getFoodData().setFoodLevel(20);
        player.setAirSupply(player.getMaxAirSupply());
        player.inventoryMenu.broadcastChanges();
        if (!transfer(player, orbit, OrbitCockpit.spawn(station), 180, 42)) {
            returnHome(player);
            return false;
        }
        player.getServer().getPlayerList().saveAll();
        player.sendSystemMessage(Component.translatable("message.worldeater.orbit_entered"));
        OrbitPlanets.start(player);
        return true;
    }

    /** Restoring overwrites each saved field, so interrupted recovery cannot add duplicate items/XP. */
    public static void restoreState(ServerPlayer player, CompoundTag tag) {
        player.closeContainer();
        player.getInventory().load(tag.getList("Inventory", 10));
        player.getInventory().selected = Mth.clamp(tag.getInt("SelectedSlot"), 0, 8);
        player.setGameMode(GameType.byId(tag.getInt("GameMode")));
        player.getAbilities().loadSaveData(tag);
        player.onUpdateAbilities();
        player.removeAllEffects();
        ListTag effects = tag.getList("Effects", 10);
        for (int i = 0; i < effects.size(); i++) {
            MobEffectInstance effect = MobEffectInstance.load(effects.getCompound(i));
            if (effect != null) player.addEffect(effect);
        }
        player.setHealth(Math.max(1, tag.getFloat("Health")));
        player.setAbsorptionAmount(tag.getFloat("Absorption"));
        player.getFoodData().readAdditionalSaveData(tag);
        player.setAirSupply(tag.getInt("Air"));
        player.setRemainingFireTicks(tag.getInt("Fire"));
        player.setExperienceLevels(tag.getInt("XPLevel"));
        player.totalExperience = tag.getInt("XPTotal");
        player.experienceProgress = tag.getFloat("XPProgress");
        player.setData(Mass.VALUE, tag.getInt("Mass"));
        player.fallDistance = 0;
        player.setDeltaMovement(Vec3.ZERO);
        player.inventoryMenu.broadcastChanges();
    }

    public static boolean returnHome(ServerPlayer player) {
        if (!player.isAlive() || TRANSFERRING.contains(player.getUUID())) return false;
        CompoundTag tag = player.getData(STATE).copy();
        boolean saved = tag.getBoolean("Active");
        if (!saved && !player.level().dimension().equals(ORBIT)) {
            player.sendSystemMessage(Component.translatable("message.worldeater.orbit_no_session"));
            return false;
        }
        ServerLevel destination = null;
        if (saved) {
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
            if (dimension != null) destination = player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        }
        boolean fallback = destination == null || destination.dimension().equals(ORBIT);
        if (fallback) destination = player.getServer().overworld();
        Vec3 requested = fallback ? Vec3.atBottomCenterOf(destination.getSharedSpawnPos())
                : new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
        Vec3 safe = safeReturn(destination, requested);
        if (!transfer(player, destination, safe, saved ? tag.getFloat("Yaw") : 0, saved ? tag.getFloat("Pitch") : 0)) {
            player.sendSystemMessage(Component.translatable("message.worldeater.orbit_transfer_blocked"));
            return false;
        }
        if (saved) restoreState(player, tag);
        OrbitPlanets.cancel(player);
        player.removeData(STATE);
        player.removeData(CONFIRMATION);
        player.getServer().getPlayerList().saveAll();
        player.sendSystemMessage(Component.translatable("message.worldeater.orbit_returned"));
        return true;
    }

    /** Never materialize inside terrain, fluids, or a destroyed floor. Build only a 5 x 5 rescue pad. */
    public static Vec3 safeReturn(ServerLevel level, Vec3 requested) {
        BlockPos feet = BlockPos.containing(requested);
        if (Double.isFinite(requested.x) && Double.isFinite(requested.y) && Double.isFinite(requested.z)
                && level.getWorldBorder().isWithinBounds(feet) && feet.getY() > level.getMinBuildHeight()
                && feet.getY() + 2 < level.getMaxBuildHeight() && safeFeet(level, feet)) return requested;
        int x = Mth.floor(Mth.clamp(requested.x, level.getWorldBorder().getMinX() + 4, level.getWorldBorder().getMaxX() - 4));
        int z = Mth.floor(Mth.clamp(requested.z, level.getWorldBorder().getMinZ() + 4, level.getWorldBorder().getMaxZ() - 4));
        int y = Mth.clamp(Math.max(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 2, level.getSeaLevel() + 8),
                level.getMinBuildHeight() + 4, level.getMaxBuildHeight() - 5);
        BlockPos floor = new BlockPos(x, y - 1, z);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            level.setBlockAndUpdate(floor.offset(dx, 0, dz), Blocks.BEDROCK.defaultBlockState());
            for (int dy = 1; dy <= 3; dy++) level.setBlockAndUpdate(floor.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
        }
        return Vec3.atBottomCenterOf(floor.above());
    }
    private static boolean safeFeet(ServerLevel level, BlockPos feet) {
        var floor = level.getBlockState(feet.below());
        return level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()
                && floor.isFaceSturdy(level, feet.below(), Direction.UP)
                && !floor.is(Blocks.MAGMA_BLOCK) && !floor.is(Blocks.CACTUS) && !floor.is(Blocks.CAMPFIRE)
                && !floor.is(Blocks.SOUL_CAMPFIRE);
    }
    private static boolean transfer(ServerPlayer player, ServerLevel level, Vec3 pos, float yaw, float pitch) {
        TRANSFERRING.add(player.getUUID());
        try {
            player.teleportTo(level, pos.x, pos.y, pos.z, yaw, pitch);
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0;
            return player.serverLevel() == level && player.position().distanceToSqr(pos) < 4;
        } finally { TRANSFERRING.remove(player.getUUID()); }
    }

    private static void requestRecovery(ServerPlayer player) {
        if (active(player)) {
            CompoundTag tag = player.getData(STATE).copy();
            tag.putBoolean("Recover", true);
            player.setData(STATE, tag);
        }
    }
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) requestRecovery(player);
    }
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) requestRecovery(player);
    }
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !TRANSFERRING.contains(player.getUUID())) requestRecovery(player);
    }
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive() || TRANSFERRING.contains(player.getUUID())) return;
        boolean orbit = player.level().dimension().equals(ORBIT);
        if (active(player)) {
            CompoundTag tag = player.getData(STATE);
            if (!orbit || tag.getBoolean("Recover")) {
                if (player.tickCount % 20 == 0) returnHome(player);
            } else {
                BlockPos station = BlockPos.of(tag.getLong("Station"));
                if (!OrbitCockpit.contains(station, player.position()))
                    transfer(player, player.serverLevel(), OrbitCockpit.spawn(station), 180, 42);
            }
        } else if (orbit && player.tickCount % 20 == 0) returnHome(player);
    }
    public static void onToss(ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && active(player)) {
            event.setCanceled(true);
            player.getInventory().add(event.getEntity().getItem().copy());
            player.inventoryMenu.broadcastChanges();
        }
    }
    public static void onDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && active(player)) event.setCanceled(true);
    }
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && active(player)) event.setCanceled(true);
    }
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("worldeater").then(Commands.literal("return")
                .executes(context -> returnHome(context.getSource().getPlayerOrException()) ? 1 : 0)));
    }
    private OrbitSession() {}
}

