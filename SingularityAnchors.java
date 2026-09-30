package com.worldeater;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent anchor index for ticket validation, active caps, and stopping holes even before entity loading. */
public final class SingularityAnchors extends SavedData {
    private final Map<UUID, BlockPos> anchors = new HashMap<>();
    private final Map<UUID, Long> workChunks = new HashMap<>();

    public static SingularityAnchors get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(SingularityAnchors::new, SingularityAnchors::load), "worldeater_singularities");
    }

    public Map<UUID, BlockPos> entries() { return Map.copyOf(anchors); }
    public boolean contains(UUID id) { return anchors.containsKey(id); }
    public void add(UUID id, BlockPos position) { anchors.put(id, position.immutable()); setDirty(); }
    public Long workChunk(UUID id) { return workChunks.get(id); }
    public void setWorkChunk(UUID id, Long chunk) {
        if (chunk == null) workChunks.remove(id); else workChunks.put(id, chunk);
        setDirty();
    }
    public void remove(UUID id) { workChunks.remove(id); if (anchors.remove(id) != null) setDirty(); }

    private static SingularityAnchors load(CompoundTag tag, HolderLookup.Provider registries) {
        SingularityAnchors result = new SingularityAnchors();
        ListTag entries = tag.getList("Anchors", 10);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (entry.hasUUID("Id")) {
                UUID id = entry.getUUID("Id");
                result.anchors.put(id, BlockPos.of(entry.getLong("Position")));
                if (entry.contains("WorkChunk")) result.workChunks.put(id, entry.getLong("WorkChunk"));
            }
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        anchors.forEach((id, pos) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id); entry.putLong("Position", pos.asLong());
            if (workChunks.containsKey(id)) entry.putLong("WorkChunk", workChunks.get(id));
            entries.add(entry);
        });
        tag.put("Anchors", entries);
        return tag;
    }
}
