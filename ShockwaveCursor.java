package com.worldeater;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/** Outward-sorted chunk plan, then top-down columns clipped to a sphere; never scans sky above terrain. */
public final class ShockwaveCursor {
    private final BlockPos center;
    private final int radius;
    private final List<ChunkPos> chunks = new ArrayList<>();
    private int index, column, y = Integer.MIN_VALUE;
    public ShockwaveCursor(BlockPos center, int radius) {
        this.center = center; this.radius = radius;
        for (int x = Math.floorDiv(center.getX() - radius, 16); x <= Math.floorDiv(center.getX() + radius, 16); x++)
            for (int z = Math.floorDiv(center.getZ() - radius, 16); z <= Math.floorDiv(center.getZ() + radius, 16); z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                if (distanceSquared(chunk) <= (double) radius * radius) chunks.add(chunk);
            }
        chunks.sort(Comparator.comparingDouble(this::distanceSquared).thenComparingInt(p -> p.x).thenComparingInt(p -> p.z));
    }
    private double distanceSquared(ChunkPos chunk) {
        double x = Math.max(chunk.getMinBlockX() - center.getX(), Math.max(0, center.getX() - chunk.getMaxBlockX()));
        double z = Math.max(chunk.getMinBlockZ() - center.getZ(), Math.max(0, center.getZ() - chunk.getMaxBlockZ()));
        return x * x + z * z;
    }
    public boolean finished() { return index >= chunks.size(); }
    public ChunkPos chunk() { return finished() ? null : chunks.get(index); }
    public double distance() { return finished() ? radius : Math.sqrt(distanceSquared(chunk())); }
    public int progress() { return finished() ? 100 : index * 100 / Math.max(1, chunks.size()); }
    public BlockPos peek(LevelChunk chunk, int minY, int maxY) {
        if (finished()) return null;
        if (column >= 256) { index++; column = 0; y = Integer.MIN_VALUE; return null; }
        int x = chunk.getPos().getMinBlockX() + (column & 15), z = chunk.getPos().getMinBlockZ() + (column >> 4);
        double dx = x - center.getX(), dz = z - center.getZ(), remaining = (double) radius * radius - dx * dx - dz * dz;
        if (remaining < 0) { nextColumn(); return null; }
        int reach = (int) Math.floor(Math.sqrt(remaining));
        int bottom = Math.max(minY, center.getY() - reach);
        if (y == Integer.MIN_VALUE) y = Math.min(Math.min(maxY - 1, center.getY() + reach), chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
        if (y < bottom) { nextColumn(); return null; }
        return new BlockPos(x, y, z);
    }
    public void advance() { y--; }
    private void nextColumn() { column++; y = Integer.MIN_VALUE; }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag(); tag.putInt("Index", index); tag.putInt("Column", column); tag.putInt("Y", y); return tag;
    }
    public void restore(CompoundTag tag) {
        index = Math.clamp(tag.getInt("Index"), 0, chunks.size()); column = Math.clamp(tag.getInt("Column"), 0, 256); y = tag.getInt("Y");
    }
}
