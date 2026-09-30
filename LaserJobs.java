package com.worldeater;

import com.mojang.logging.LogUtils;
import java.util.LinkedHashMap;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** One durable shot per owner; work persists independently of the orbit player's lifecycle. */
public final class LaserJobs extends SavedData {
    private final LinkedHashMap<UUID, LaserShot> shots = new LinkedHashMap<>();
    public static LaserJobs get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(LaserJobs::new, LaserJobs::load), "worldeater_superlaser");
    }
    public boolean active(UUID owner) { return shots.containsKey(owner); }
    public LaserShot shot(UUID owner) { return shots.get(owner); }
    public void add(LaserShot shot) {
        if (active(shot.owner())) throw new IllegalStateException("A shot is already active for this player");
        shots.put(shot.owner(), shot); setDirty();
    }
    public int stopAll() {
        int count = shots.size(); shots.values().forEach(LaserShot::close); shots.clear(); setDirty(); return count;
    }
    public static void onTick(ServerTickEvent.Post event) {
        var server = event.getServer(); var data = get(server);
        if (data.shots.isEmpty()) return;
        if (server.getTickCount() % 20 == 0) data.shots.forEach((id, shot) -> {
            var player = server.getPlayerList().getPlayer(id);
            if (player != null) player.displayClientMessage(shot.progress(), true);
        });
        var selected = data.shots.entrySet().stream().filter(entry -> !entry.getValue().paused()).findFirst().orElse(null);
        if (selected == null) return;
        UUID id = selected.getKey(); LaserShot shot = selected.getValue();
        try {
            shot.tick(server, WorldEaterConfig.LASER_BLOCKS.get(), WorldEaterConfig.LASER_SCANS.get(),
                    System.nanoTime() + (long) (WorldEaterConfig.LASER_TIME_BUDGET.get() * 1_000_000));
            data.shots.remove(id);
            if (shot.stage() == LaserShot.Stage.DONE) {
                shot.close();
                var player = server.getPlayerList().getPlayer(id);
                if (player != null) {
                    player.sendSystemMessage(Component.translatable("message.worldeater.laser_complete"));
                    if (OrbitSession.active(player)) OrbitSession.returnHome(player);
                }
            } else data.shots.put(id, shot);
        } catch (RuntimeException error) {
            shot.pause();
            LogUtils.getLogger().error("Superlaser shot paused for {}", id, error);
            var player = server.getPlayerList().getPlayer(id);
            if (player != null) player.sendSystemMessage(Component.translatable("message.worldeater.laser_paused"));
        }
        data.setDirty();
    }
    public static void onStop(ServerStoppingEvent event) {
        var data = get(event.getServer()); data.shots.values().forEach(LaserShot::close); data.setDirty();
    }
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("worldeater")
                .then(Commands.literal("stoplaser").requires(source -> source.hasPermission(2)).executes(context -> {
                    int count = get(context.getSource().getServer()).stopAll();
                    context.getSource().sendSuccess(() -> Component.translatable("message.worldeater.laser_stopped", count), true);
                    return count;
                }))
                .then(Commands.literal("resumelaser").requires(source -> source.hasPermission(2)).executes(context -> {
                    var data = get(context.getSource().getServer());
                    int count = 0;
                    for (var shot : data.shots.values()) if (shot.paused()) { shot.resume(); count++; }
                    data.setDirty(); final int resumed = count;
                    context.getSource().sendSuccess(() -> Component.translatable("message.worldeater.laser_resumed", resumed), true);
                    return count;
                })));
    }
    public static LaserJobs load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new LaserJobs(); ListTag shots = tag.getList("Shots", 10);
        for (int i = 0; i < shots.size(); i++) {
            try { var shot = LaserShot.load(shots.getCompound(i)); data.shots.put(shot.owner(), shot); }
            catch (RuntimeException error) { LogUtils.getLogger().error("Invalid saved superlaser job; skipped", error); }
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag(); shots.values().forEach(shot -> list.add(shot.save())); tag.put("Shots", list); return tag;
    }
}
