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

/** Stable, separate cockpits leave room for each player's later block planet. */
public final class OrbitStations extends SavedData {
    private final Map<UUID, Integer> stations = new HashMap<>();
    private final Map<UUID, CompoundTag> planets = new HashMap<>();
    public CompoundTag planet(UUID player) { return planets.getOrDefault(player, new CompoundTag()).copy(); }
    public void savePlanet(UUID player, CompoundTag planet) { planets.put(player, planet.copy()); setDirty(); }
    public static OrbitStations get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(OrbitStations::new, OrbitStations::load), "worldeater_orbit_stations");
    }
    public BlockPos station(UUID player) {
        Integer slot = stations.get(player);
        if (slot == null) {
            slot = stations.size();
            if (slot >= 4096) throw new IllegalStateException("Orbit station capacity reached");
            stations.put(player, slot);
            setDirty();
        }
        return new BlockPos((slot % 64) * 1024, 224, (slot / 64) * 1024);
    }
    private static OrbitStations load(CompoundTag tag, HolderLookup.Provider registries) {
        OrbitStations result = new OrbitStations();
        ListTag entries = tag.getList("Stations", 10);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            int slot = entry.getInt("Slot");
            if (entry.hasUUID("Player") && slot >= 0 && slot < 4096)
                result.stations.put(entry.getUUID("Player"), slot);
            if (entry.hasUUID("Player") && entry.contains("Planet")) result.planets.put(entry.getUUID("Player"), entry.getCompound("Planet").copy());
        }
        return result;
    }
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        stations.forEach((id, slot) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", id);
            entry.putInt("Slot", slot);
            if (planets.containsKey(id)) entry.put("Planet", planets.get(id).copy());
            entries.add(entry);
        });
        tag.put("Stations", entries);
        return tag;
    }
}
