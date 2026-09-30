package com.worldeater.client;

import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;

public final class OrbitEffects extends DimensionSpecialEffects {
    public OrbitEffects() { super(Float.NaN, false, SkyType.NORMAL, false, false); }
    @Override public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) { return Vec3.ZERO; }
    @Override public boolean isFoggyAt(int x, int y) { return false; }
    @Override public float[] getSunriseColor(float time, float partialTick) { return null; }
}
