package com.worldeater;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Server ray/sphere intersection, independent of vanilla block reach and cockpit glass. */
public final class LaserAim {
    public record Target(Vec3 hit, int x, int z) {}
    public static Vec3 intersect(Vec3 eye, Vec3 direction, Vec3 center, double radius) {
        if (direction.lengthSqr() < 1.0E-8 || radius <= 0) return null;
        Vec3 ray = direction.normalize(), offset = eye.subtract(center);
        double b = offset.dot(ray), discriminant = b * b - offset.lengthSqr() + radius * radius;
        if (discriminant < 0) return null;
        double t = -b - Math.sqrt(discriminant);
        if (t < 0) t = -b + Math.sqrt(discriminant);
        return t < 0 ? null : eye.add(ray.scale(t));
    }
    public static Target target(Vec3 eye, Vec3 direction, CompoundTag planet) {
        int radius = planet.getInt("Radius");
        if (!planet.getBoolean("Complete") || planet.getBoolean("Spent") || radius < 1 || radius > 100) return null;
        Vec3 center = Vec3.atCenterOf(BlockPos.of(planet.getLong("Center")));
        Vec3 hit = intersect(eye, direction, center, radius);
        if (hit == null) return null;
        double x = (hit.x - center.x) / radius, y = (hit.y - center.y) / radius;
        int scan = Mth.clamp(planet.getInt("ScanRadius"), 16, 3000);
        return new Target(hit, planet.getInt("SourceX") + (int) Math.round(PlanetProjection.squareAxis(x, y) * scan),
                planet.getInt("SourceZ") - (int) Math.round(PlanetProjection.squareAxis(y, x) * scan));
    }
    private LaserAim() {}
}
