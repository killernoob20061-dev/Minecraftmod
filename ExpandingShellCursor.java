package com.worldeater;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** Resumable, chunk-ordered spherical shell. No radius-cubed allocation or rescan of the empty core. */
public final class ExpandingShellCursor {
    private final BlockPos origin;
    private final int minY, maxY, minX, maxX, minZ, maxZ;
    private final int shell;
    private final int firstChunkX, lastChunkX, firstChunkZ, lastChunkZ;
    private int chunkX, chunkZ, column, y = Integer.MIN_VALUE;
    private boolean finished;

    public ExpandingShellCursor(BlockPos origin, int shell, int minY, int maxY, int minX, int maxX, int minZ, int maxZ) {
        this.origin = origin;
        this.shell = shell;
        this.minY = minY; this.maxY = maxY;
        this.minX = minX; this.maxX = maxX; this.minZ = minZ; this.maxZ = maxZ;
        firstChunkX = Math.floorDiv(Math.max(minX, origin.getX() - shell), 16);
        lastChunkX = Math.floorDiv(Math.min(maxX, origin.getX() + shell), 16);
        firstChunkZ = Math.floorDiv(Math.max(minZ, origin.getZ() - shell), 16);
        lastChunkZ = Math.floorDiv(Math.min(maxZ, origin.getZ() + shell), 16);
        chunkX = firstChunkX; chunkZ = firstChunkZ;
    }

    public int shell() { return shell; }
    public boolean finished() { return finished; }

    /** One bounded scan step. A null result can mean a skipped column/chunk, not just completion. */
    public BlockPos peek() {
        if (finished) return null;
        if (chunkX > lastChunkX) { finished = true; return null; }
        int x0 = chunkX * 16 - origin.getX();
        int x1 = x0 + 15;
        double nearestX = x0 <= 0 && x1 >= 0 ? 0 : Math.min(Math.abs(x0), Math.abs(x1));
        double outerSquared = (double) shell * shell;
        if (nearestX * nearestX > outerSquared) { nextX(); return null; }
        int zReach = (int) Math.floor(Math.sqrt(outerSquared - nearestX * nearestX));
        int startZ = Math.max(firstChunkZ, Math.floorDiv(origin.getZ() - zReach, 16));
        int endZ = Math.min(lastChunkZ, Math.floorDiv(origin.getZ() + zReach, 16));
        if (chunkZ < startZ) { chunkZ = startZ; column = 0; y = Integer.MIN_VALUE; }
        if (chunkZ > endZ) { nextX(); return null; }

        // Skip entire bands of chunks already inside the previous sphere, even at world-scale radii.
        double inner = shell == 1 ? -1 : shell - 1.0;
        double farX = Math.max(Math.abs(x0), Math.abs(x1));
        double farY = Math.max(Math.abs((double) minY - origin.getY()), Math.abs((double) maxY - 1 - origin.getY()));
        double gapSquared = inner * inner - farX * farX - farY * farY;
        if (inner >= 0 && gapSquared >= 0) {
            int gap = (int) Math.floor(Math.sqrt(gapSquared));
            int gapStart = -Math.floorDiv(-(origin.getZ() - gap), 16);
            int gapEnd = Math.floorDiv(origin.getZ() + gap - 15, 16);
            if (chunkZ >= gapStart && chunkZ <= gapEnd) {
                chunkZ = gapEnd + 1; column = 0; y = Integer.MIN_VALUE; return null;
            }
        }
        if (column >= 256) { chunkZ++; column = 0; y = Integer.MIN_VALUE; return null; }
        int x = chunkX * 16 + (column & 15);
        int z = chunkZ * 16 + (column >> 4);
        double dx = (double) x - origin.getX(), dz = (double) z - origin.getZ();
        double horizontal = dx * dx + dz * dz;
        if (x < minX || x > maxX || z < minZ || z > maxZ || horizontal > outerSquared) {
            nextColumn(); return null;
        }
        int outerY = (int) Math.floor(Math.sqrt(outerSquared - horizontal));
        int innerY = inner >= 0 && horizontal <= inner * inner ? (int) Math.floor(Math.sqrt(inner * inner - horizontal)) : -1;
        int low = Math.max(minY, origin.getY() - outerY);
        int high = Math.min(maxY - 1, origin.getY() + outerY);
        if (y == Integer.MIN_VALUE) y = low;
        if (innerY >= 0 && y >= origin.getY() - innerY && y <= origin.getY() + innerY) y = origin.getY() + innerY + 1;
        if (y > high) { nextColumn(); return null; }
        return new BlockPos(x, y, z);
    }

    public void advance() { y++; }
    private void nextColumn() { column++; y = Integer.MIN_VALUE; }
    private void nextX() { chunkX++; chunkZ = firstChunkZ; column = 0; y = Integer.MIN_VALUE; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Shell", shell); tag.putInt("ChunkX", chunkX); tag.putInt("ChunkZ", chunkZ);
        tag.putInt("Column", column); tag.putInt("Y", y); tag.putBoolean("Finished", finished);
        return tag;
    }

    public void restore(CompoundTag tag) {
        chunkX = Math.clamp(tag.getInt("ChunkX"), firstChunkX, lastChunkX + 1);
        chunkZ = Math.clamp(tag.getInt("ChunkZ"), firstChunkZ, lastChunkZ + 1);
        column = Math.clamp(tag.getInt("Column"), 0, 256);
        int savedY = tag.getInt("Y");
        y = savedY == Integer.MIN_VALUE ? savedY : Math.clamp(savedY, minY, maxY);
        finished = tag.getBoolean("Finished");
    }
}
