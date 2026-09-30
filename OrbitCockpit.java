package com.worldeater;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Chamfered command bridge with a clear forward observation deck and recessed lighting. */
public final class OrbitCockpit {
    public static Vec3 spawn(BlockPos origin) { return Vec3.atBottomCenterOf(origin.offset(0, 1, -8)); }
    public static boolean contains(BlockPos origin, Vec3 pos) {
        double x = Math.abs(pos.x - origin.getX() - 0.5), z = Math.abs(pos.z - origin.getZ() - 0.5);
        return x < 13.4 && z < 15.4 && x + z < 25.4 && pos.y >= origin.getY() + 1 && pos.y < origin.getY() + 12;
    }
    public static void build(ServerLevel level, BlockPos origin) {
        // Migrate the previous bridge at Y=96; preserve the dimension's existing height and planet metadata.
        BlockPos old = new BlockPos(origin.getX(), 96, origin.getZ());
        if (level.getBlockState(old.offset(0, 0, -8)).is(Blocks.LIME_CONCRETE)
                || level.getBlockState(old.offset(0, 0, 5)).is(Blocks.LIME_CONCRETE)) {
            for (BlockPos pos : BlockPos.betweenClosed(old.offset(-14, 0, -16), old.offset(14, 12, 16)))
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
        // This envelope also clears the entire Phase 3 cabin on the next visit.
        for (int x = -14; x <= 14; x++) for (int z = -16; z <= 16; z++) for (int y = 0; y <= 12; y++) {
            boolean inside = Math.abs(x) + Math.abs(z) <= 26;
            boolean edge = Math.abs(x) == 14 || Math.abs(z) == 16 || Math.abs(x) + Math.abs(z) == 26;
            BlockState state = Blocks.AIR.defaultBlockState();
            if (inside) {
                if (y == 0) {
                    state = (z <= -7 ? Blocks.GLASS : Math.abs(x) <= 2 ? Blocks.SMOOTH_STONE : Blocks.POLISHED_BLACKSTONE).defaultBlockState();
                    if (z >= -8 && z <= 12 && Math.abs(x) == 3)
                        state = (z % 3 == 0 ? Blocks.SEA_LANTERN : Blocks.CYAN_CONCRETE).defaultBlockState();
                } else if (y == 12) {
                    state = (z < -3 ? Blocks.GLASS : Blocks.GRAY_CONCRETE).defaultBlockState();
                    if (z >= -3 && (Math.abs(x) == 6 || z == 8)) state = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
                    if (z >= 0 && Math.abs(x) == 5 && z % 4 == 0) state = Blocks.SEA_LANTERN.defaultBlockState();
                } else if (edge) {
                    boolean window = z < -5 && y < 11;
                    state = (window ? Blocks.GLASS : Blocks.GRAY_CONCRETE).defaultBlockState();
                    if (!window && (y <= 2 || y >= 10)) state = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
                    if (!window && y >= 3 && y <= 9 && (z % 5 == 0 || x % 5 == 0)) state = Blocks.SMOOTH_QUARTZ.defaultBlockState();
                    if (!window && y == 4 && (z % 5 == 0 || x % 5 == 0)) state = Blocks.SEA_LANTERN.defaultBlockState();
                }
            }
            level.setBlock(origin.offset(x, y, z), state, 3);
        }
        // Low forward instrument rail: leaves the centre and view of the planet unobstructed.
        for (int x = -9; x <= 9; x++) if (Math.abs(x) >= 3) {
            put(level, origin, x, 1, -10, Blocks.POLISHED_BLACKSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        }
        // Symmetric side consoles; lights beneath colored glass suggest status screens.
        for (int side : new int[] {-1, 1}) {
            for (int z = -6; z <= 4; z++) {
                put(level, origin, side * 10, 1, z, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
                put(level, origin, side * 11, 1, z, Blocks.SEA_LANTERN.defaultBlockState());
                put(level, origin, side * 11, 2, z, (z % 4 == 0 ? Blocks.LIME_STAINED_GLASS : Blocks.CYAN_STAINED_GLASS).defaultBlockState());
                put(level, origin, side * 10, 2, z, Blocks.POLISHED_BLACKSTONE_STAIRS.defaultBlockState()
                        .setValue(StairBlock.FACING, side < 0 ? Direction.WEST : Direction.EAST));
            }
            for (int z : new int[] {-4, 1}) chair(level, origin.offset(side * 8, 0, z));
        }
        // Raised rear command dais, broad stairs and a central command chair.
        for (int x = -5; x <= 5; x++) for (int z = 7; z <= 13; z++)
            put(level, origin, x, 1, z, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        for (int x = -4; x <= 4; x++) put(level, origin, x, 1, 6,
                Blocks.POLISHED_DEEPSLATE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
        chair(level, origin.offset(0, 1, 10));
        for (int x : new int[] {-4, 4}) {
            put(level, origin, x, 2, 10, Blocks.SEA_LANTERN.defaultBlockState());
            put(level, origin, x, 3, 10, Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
        }
        // Tall rear wall display, framed by vertical light strips.
        for (int x = -4; x <= 4; x++) for (int y = 4; y <= 8; y++)
            put(level, origin, x, y, 15, (Math.abs(x) == 4 ? Blocks.SEA_LANTERN :
                    y == 5 || x == 0 ? Blocks.CYAN_CONCRETE : Blocks.BLACK_CONCRETE).defaultBlockState());
        put(level, origin, 0, 0, -8, Blocks.LIME_CONCRETE.defaultBlockState());
        put(level, origin, 0, -1, -14, Blocks.SEA_LANTERN.defaultBlockState());
        put(level, origin, 0, -2, -14, Blocks.LIME_STAINED_GLASS.defaultBlockState());
    }
    private static void chair(ServerLevel level, BlockPos pos) {
        level.setBlock(pos.above(), Blocks.POLISHED_BLACKSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH), 3);
        level.setBlock(pos.offset(0, 1, 1), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 3);
        level.setBlock(pos.offset(0, 2, 1), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 3);
    }
    private static void put(ServerLevel level, BlockPos origin, int x, int y, int z, BlockState state) {
        level.setBlock(origin.offset(x, y, z), state, 3);
    }
    private OrbitCockpit() {}
}
