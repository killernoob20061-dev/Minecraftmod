package com.worldeater;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public final class SingularityTickets {
    private static final Map<ServerLevel, Integer> LEVEL_AGE = new WeakHashMap<>();
    private static final Map<ServerLevel, Long> NEXT_LOAD = new WeakHashMap<>();
    public static final TicketController CONTROLLER = new TicketController(
            ResourceLocation.fromNamespaceAndPath(WorldEater.MOD_ID, "singularities"), (level, helper) -> {
        Map<UUID, BlockPos> saved = SingularityAnchors.get(level).entries();
        helper.getEntityTickets().forEach((id, tickets) -> {
            BlockPos anchor = saved.get(id);
            if (anchor == null) { helper.removeAllTickets(id); return; }
            long core = new ChunkPos(anchor).toLong();
            for (long chunk : tickets.ticking()) if (chunk != core) helper.removeTicket(id, chunk, true);
            // Work tickets are ephemeral; the saved cursor requests the next batch again after startup.
            for (long chunk : tickets.nonTicking()) helper.removeTicket(id, chunk, false);
            SingularityAnchors.get(level).setWorkChunk(id, null);
        });
        helper.getBlockTickets().keySet().forEach(helper::removeAllTickets);
    });

    public static void register(RegisterTicketControllersEvent event) { event.register(CONTROLLER); }

    /** Throttle new work-chunk requests globally per dimension, not just per hole. */
    public static boolean takeLoadSlot(ServerLevel level) {
        long now = level.getGameTime();
        if (now < NEXT_LOAD.getOrDefault(level, Long.MIN_VALUE)) return false;
        NEXT_LOAD.put(level, now + WorldEaterConfig.CHUNK_LOAD_INTERVAL.get());
        return true;
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        int age = LEVEL_AGE.merge(level, 1, Integer::sum);
        if (age == 20) {
            // Reconcile a saved anchor whose ticket write was interrupted during a previous shutdown.
            SingularityAnchors.get(level).entries().forEach((id, pos) -> {
                ChunkPos chunk = new ChunkPos(pos);
                CONTROLLER.forceChunk(level, id, chunk.x, chunk.z, true, true);
            });
        }
        if (age % 200 != 0) return;
        // Core tickets have had ten seconds to finish loading their saved entity before orphan cleanup.
        SingularityAnchors data = SingularityAnchors.get(level);
        data.entries().forEach((id, pos) -> {
            if (level.getEntity(id) instanceof SingularityEntity) return;
            ChunkPos chunk = new ChunkPos(pos);
            release(level, id, pos);
        });
    }

    public static void onUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) { LEVEL_AGE.remove(level); NEXT_LOAD.remove(level); }
    }

    public static int stopAll(ServerLevel level) {
        SingularityAnchors data = SingularityAnchors.get(level);
        var ids = new java.util.HashSet<>(data.entries().keySet());
        var entities = new ArrayList<SingularityEntity>();
        level.getAllEntities().forEach(entity -> { if (entity instanceof SingularityEntity hole) { entities.add(hole); ids.add(hole.getUUID()); } });
        entities.forEach(SingularityEntity::discard);
        data.entries().forEach((id, pos) -> {
            release(level, id, pos);
        });
        return ids.size();
    }

    public static void release(ServerLevel level, UUID id, BlockPos position) {
        SingularityAnchors data = SingularityAnchors.get(level);
        Long work = data.workChunk(id);
        if (work != null) {
            ChunkPos chunk = new ChunkPos(work);
            CONTROLLER.forceChunk(level, id, chunk.x, chunk.z, false, false);
        }
        ChunkPos core = new ChunkPos(position);
        CONTROLLER.forceChunk(level, id, core.x, core.z, false, true);
        data.remove(id);
    }

    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("worldeater")
                .then(Commands.literal("stop").requires(source -> source.hasPermission(2)).executes(context -> {
                    int count = 0;
                    for (ServerLevel level : context.getSource().getServer().getAllLevels()) count += stopAll(level);
                    final int stopped = count;
                    context.getSource().sendSuccess(() -> Component.translatable("message.worldeater.stopped", stopped), true);
                    return count;
                })));
    }

    private SingularityTickets() {}
}

