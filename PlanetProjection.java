package com.worldeater;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

/** North-up square-to-disk projection; the whole source square fits the front hemisphere. */
public final class PlanetProjection {
    public static BlockPos center(BlockPos station, int radius) { return new BlockPos(station.getX(), radius + 8, station.getZ() - 2 * radius); }
    public static int sampleCoordinate(int origin, int scanRadius, int index, int size) {
        return origin - scanRadius + (int) Math.round(2.0 * scanRadius * index / (size - 1));
    }
    public static int sampleIndex(BlockPos surface, BlockPos center, int planetRadius, int grid) {
        double dx = (surface.getX() - center.getX()) / (double) planetRadius;
        double dy = (surface.getY() - center.getY()) / (double) planetRadius;
        int x = (int) Math.round((squareAxis(dx, dy) + 1) * (grid - 1.0) / 2);
        int z = (int) Math.round((1 - squareAxis(dy, dx)) * (grid - 1.0) / 2);
        return Mth.clamp(z, 0, grid - 1) * grid + Mth.clamp(x, 0, grid - 1);
    }
    // Inverse elliptical-grid mapping. Unlike a circular crop, diagonal rim points include the square's corners.
    public static double squareAxis(double x, double y) {
        double a = 2 + x * x - y * y, b = 2 * Math.sqrt(2) * x;
        return Mth.clamp((Math.sqrt(Math.max(0, a + b)) - Math.sqrt(Math.max(0, a - b))) / 2, -1, 1);
    }
    public static ExpandingShellCursor shell(BlockPos center, int radius) {
        return new ExpandingShellCursor(center, radius, center.getY() - radius, center.getY() + radius + 1,
                center.getX() - radius, center.getX() + radius, center.getZ() - radius, center.getZ() + radius);
    }
    private PlanetProjection() {}
}
