package com.worldeater;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3f;

public final class LaserControl {
    private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.2F, 1, 0.1F), 3);
    public static boolean mayFire(ServerPlayer player) {
        return player.getServer().isSingleplayer() || !WorldEaterConfig.OP_ONLY.get() && !WorldEaterConfig.ORBIT_OP_ONLY.get() || player.hasPermissions(2);
    }
    public static boolean canCharge(ServerPlayer player, boolean feedback) {
        boolean valid = player.isAlive() && OrbitSession.active(player) && player.level().dimension().equals(OrbitSession.ORBIT)
                && mayFire(player) && !OrbitPlanets.active(player) && !LaserJobs.get(player.getServer()).active(player.getUUID());
        if (valid) valid = LaserAim.target(player.getEyePosition(), player.getLookAngle(),
                OrbitStations.get(player.serverLevel()).planet(player.getUUID())) != null;
        if (!valid && feedback) player.sendSystemMessage(Component.translatable("message.worldeater.laser_not_ready"));
        return valid;
    }
    public static boolean fire(ServerPlayer player) {
        if (!canCharge(player, true)) return false;
        var stations = OrbitStations.get(player.serverLevel());
        var planet = stations.planet(player.getUUID());
        var aim = LaserAim.target(player.getEyePosition(), player.getLookAngle(), planet);
        var sourceId = ResourceLocation.tryParse(planet.getString("SourceDimension"));
        var source = sourceId == null ? null : player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, sourceId));
        if (source == null || source.dimension().equals(OrbitSession.ORBIT)
                || !source.getWorldBorder().isWithinBounds(new BlockPos(aim.x(), 0, aim.z()))) {
            player.sendSystemMessage(Component.translatable("message.worldeater.laser_invalid_target")); return false;
        }
        LaserShot shot = new LaserShot(player, source.dimension().location(), aim, planet,
                WorldEaterConfig.LASER_RADIUS.get(), WorldEaterConfig.LASER_SPEED.get());
        LaserJobs.get(player.getServer()).add(shot);
        planet.putBoolean("Spent", true);
        stations.savePlanet(player.getUUID(), planet);
        player.sendSystemMessage(Component.translatable("message.worldeater.laser_fired", aim.x(), aim.z(), shot.radius()));
        beam(player, shot.muzzle(), aim.hit());
        return true;
    }
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 5 != 0
                || !player.isHolding(WorldEater.SUPERLASER_TRIGGER.get()) || !canCharge(player, false)) return;
        var planet = OrbitStations.get(player.serverLevel()).planet(player.getUUID());
        var aim = LaserAim.target(player.getEyePosition(), player.getLookAngle(), planet);
        marker(player, aim.hit(), Vec3.atCenterOf(BlockPos.of(planet.getLong("Center"))));
        if (!player.isUsingItem()) player.displayClientMessage(Component.translatable("message.worldeater.laser_target", aim.x(), aim.z(),
                WorldEaterConfig.LASER_RADIUS.get()), true);
    }
    public static void marker(ServerPlayer player, Vec3 hit, Vec3 center) {
        Vec3 normal = hit.subtract(center).normalize();
        Vec3 right = normal.cross(Math.abs(normal.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 up = normal.cross(right).normalize();
        for (int i = 0; i < 16; i++) {
            double angle = i * Math.PI / 8;
            Vec3 p = hit.add(normal.scale(0.6)).add(right.scale(Math.cos(angle) * 3)).add(up.scale(Math.sin(angle) * 3));
            dust(player, p);
        }
    }
    public static void beam(ServerPlayer player, Vec3 from, Vec3 to) {
        if (!player.level().dimension().equals(OrbitSession.ORBIT)) return;
        for (int i = 0; i <= 96; i++) dust(player, from.lerp(to, i / 96.0));
    }
    private static void dust(ServerPlayer player, Vec3 pos) {
        player.serverLevel().sendParticles(player, GREEN, true, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
    }
    public static void explosion(ServerPlayer player, Vec3 center, int radius) {
        if (!player.level().dimension().equals(OrbitSession.ORBIT)) return;
        for (int i = 0; i < 24; i++) {
            double angle = i * Math.PI / 12;
            Vec3 p = center.add(Math.cos(angle) * radius * 0.65, Math.sin(angle) * radius * 0.65, radius * 0.4);
            player.serverLevel().sendParticles(player, ParticleTypes.EXPLOSION, true, p.x, p.y, p.z, 2, 2, 2, 2, 0);
        }
    }
    private LaserControl() {}
}
