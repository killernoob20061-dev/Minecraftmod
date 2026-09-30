package com.worldeater;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** One job advances per server tick, round-robin, so configured build/time budgets are global. */
public final class OrbitPlanets {
    private static final Map<MinecraftServer, LinkedHashMap<UUID, PlanetBuildJob>> JOBS = new WeakHashMap<>();
    public static void start(ServerPlayer player) {
        cancel(player);
        var snapshot = player.getData(OrbitSession.STATE);
        var dimension = ResourceLocation.tryParse(snapshot.getString("Dimension"));
        var source = dimension == null ? null : player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        try {
            if (source == null) throw new IllegalStateException("Source dimension unavailable");
            var job = new PlanetBuildJob(player, source, player.serverLevel(), BlockPos.of(snapshot.getLong("Station")),
                    net.minecraft.util.Mth.floor(snapshot.getDouble("X")), net.minecraft.util.Mth.floor(snapshot.getDouble("Z")),
                    WorldEaterConfig.PLANET_SCAN_RADIUS.get(), WorldEaterConfig.PLANET_GRID.get(), WorldEaterConfig.PLANET_RADIUS.get());
            JOBS.computeIfAbsent(player.getServer(), ignored -> new LinkedHashMap<>()).put(player.getUUID(), job);
        } catch (RuntimeException error) {
            LogUtils.getLogger().error("Could not start orbit planet job for {}", player.getUUID(), error);
            player.sendSystemMessage(Component.translatable("message.worldeater.planet_failed"));
        }
    }
    public static void cancel(ServerPlayer player) {
        var jobs = JOBS.get(player.getServer());
        if (jobs != null) { var job = jobs.remove(player.getUUID()); if (job != null) job.close(); }
    }
    public static boolean active(ServerPlayer player) {
        var jobs = JOBS.get(player.getServer());
        return jobs != null && jobs.containsKey(player.getUUID());
    }
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) cancel(player);
    }
    public static void onStop(ServerStoppingEvent event) {
        var jobs = JOBS.remove(event.getServer());
        if (jobs != null) jobs.values().forEach(PlanetBuildJob::close);
    }
    public static void onTick(ServerTickEvent.Post event) {
        var jobs = JOBS.get(event.getServer());
        if (jobs == null || jobs.isEmpty()) return;
        // Release invalid sessions promptly, including during death or a command dimension change.
        for (UUID id : new ArrayList<>(jobs.keySet())) {
            var player = event.getServer().getPlayerList().getPlayer(id);
            if (player == null || !player.isAlive() || !OrbitSession.active(player) || !player.level().dimension().equals(OrbitSession.ORBIT)) {
                jobs.remove(id).close();
            }
        }
        if (jobs.isEmpty()) return;
        if (event.getServer().getTickCount() % 20 == 0) jobs.forEach((id, job) ->
                event.getServer().getPlayerList().getPlayer(id).displayClientMessage(job.progress(), true));
        var entry = jobs.entrySet().iterator().next();
        UUID id = entry.getKey(); PlanetBuildJob job = entry.getValue();
        var player = event.getServer().getPlayerList().getPlayer(id);
        jobs.remove(id);
        try {
            job.tick(WorldEaterConfig.PLANET_BLOCK_BUDGET.get(), System.nanoTime() + (long) (WorldEaterConfig.PLANET_TIME_BUDGET.get() * 1_000_000));
            if (job.stage() == PlanetBuildJob.Stage.DONE) job.close(); else jobs.put(id, job);
        } catch (RuntimeException error) {
            job.close();
            LogUtils.getLogger().error("Orbit planet job failed for {}", id, error);
            player.sendSystemMessage(Component.translatable("message.worldeater.planet_failed"));
        }
    }
    private OrbitPlanets() {}
}
