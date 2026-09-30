package com.worldeater;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/** One transient FULL (non-ticking) chunk ticket; poll without blocking the server for generation. */
public final class PlanetChunkLease implements AutoCloseable {
    private static final TicketType<UUID> TYPE = TicketType.create("worldeater_planet_work", UUID::compareTo);
    private final UUID id = UUID.randomUUID();
    private ServerLevel level;
    private ChunkPos chunk;
    private int requestedAt;
    public LevelChunk request(ServerLevel requestedLevel, ChunkPos requestedChunk) {
        if (level != requestedLevel || !requestedChunk.equals(chunk)) {
            close();
            level = requestedLevel; chunk = requestedChunk;
            requestedAt = level.getServer().getTickCount();
            level.getChunkSource().addRegionTicket(TYPE, chunk, 0, id);
            return null; // Let the distance manager schedule/load this chunk in a later tick.
        }
        if (level.getServer().getTickCount() - requestedAt > 2400) throw new IllegalStateException("Planet chunk loading timed out");
        return level.getChunkSource().getChunkNow(chunk.x, chunk.z);
    }
    public boolean held() { return level != null; }
    @Override public void close() {
        if (level != null) level.getChunkSource().removeRegionTicket(TYPE, chunk, 0, id);
        level = null; chunk = null;
    }
}
