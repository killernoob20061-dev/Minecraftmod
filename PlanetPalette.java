package com.worldeater;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Fixed opaque palette. No fluids, falling blocks, entities, or light-update bypasses. */
public final class PlanetPalette {
    private static final Block[] BLOCKS = {
        Blocks.WHITE_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE, Blocks.GRAY_CONCRETE, Blocks.BLACK_CONCRETE,
        Blocks.BROWN_CONCRETE, Blocks.RED_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.YELLOW_CONCRETE,
        Blocks.LIME_CONCRETE, Blocks.GREEN_CONCRETE, Blocks.CYAN_CONCRETE, Blocks.LIGHT_BLUE_CONCRETE,
        Blocks.BLUE_CONCRETE, Blocks.PURPLE_CONCRETE, Blocks.MAGENTA_CONCRETE, Blocks.PINK_CONCRETE,
        Blocks.WHITE_WOOL, Blocks.LIGHT_GRAY_WOOL, Blocks.GRAY_WOOL, Blocks.BLACK_WOOL,
        Blocks.BROWN_WOOL, Blocks.RED_WOOL, Blocks.ORANGE_WOOL, Blocks.YELLOW_WOOL,
        Blocks.LIME_WOOL, Blocks.GREEN_WOOL, Blocks.CYAN_WOOL, Blocks.LIGHT_BLUE_WOOL,
        Blocks.BLUE_WOOL, Blocks.PURPLE_WOOL, Blocks.MAGENTA_WOOL, Blocks.PINK_WOOL,
        Blocks.TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.YELLOW_TERRACOTTA, Blocks.GREEN_TERRACOTTA,
        Blocks.BROWN_TERRACOTTA, Blocks.SMOOTH_SANDSTONE, Blocks.STONE, Blocks.SNOW_BLOCK
    };
    public static BlockState match(int rgb) {
        long best = Long.MAX_VALUE;
        Block closest = Blocks.BLUE_CONCRETE;
        for (Block block : BLOCKS) {
            int color = block.defaultBlockState().getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;
            int r = (rgb >> 16 & 255) - (color >> 16 & 255), g = (rgb >> 8 & 255) - (color >> 8 & 255), b = (rgb & 255) - (color & 255);
            long distance = 2L * r * r + 4L * g * g + 3L * b * b;
            if (distance < best) { best = distance; closest = block; }
        }
        return closest.defaultBlockState();
    }
    private PlanetPalette() {}
}
