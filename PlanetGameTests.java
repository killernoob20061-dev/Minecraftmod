package com.worldeater.gametest;

import com.worldeater.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(WorldEater.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlanetGameTests {
    @GameTest(template = "test_empty", batch = "planet", timeoutTicks = 80)
    public static void projectionAndSurfaceSampling(GameTestHelper helper) {
        BlockPos center = new BlockPos(0, 128, -160);
        helper.assertTrue(PlanetProjection.sampleIndex(center.offset(-80, 0, 0), center, 80, 33) == 16 * 33, "West limb must map to west edge");
        helper.assertTrue(PlanetProjection.sampleIndex(center.offset(0, 80, 0), center, 80, 33) == 16, "North-up map must put north at top pole");
        helper.assertTrue(PlanetProjection.sampleIndex(center.offset(0, 0, 80), center, 80, 33) == 16 * 33 + 16, "Front centre must show launch point");
        helper.assertTrue(Math.abs(PlanetProjection.squareAxis(Math.sqrt(0.5), Math.sqrt(0.5)) - 1) < 0.0001,
                "Square corners must map to the diagonal rim rather than being cropped away");
        helper.assertTrue(PlanetProjection.sampleIndex(center.offset(56, 57, 3), center, 80, 33) == 32,
                "Northeast scan corner must be represented on the front hemisphere");
        helper.assertTrue(PlanetProjection.sampleCoordinate(-500, 1500, 0, 33) == -2000
                && PlanetProjection.sampleCoordinate(-500, 1500, 32, 33) == 1000, "Scan must span the configured square, including negative coordinates");
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 4, 4));
        level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
        level.setBlockAndUpdate(pos.above(), Blocks.GLASS.defaultBlockState());
        helper.assertTrue(PlanetBuildJob.sampleColor(level, level.getChunkAt(pos), pos.getX(), pos.getZ()) == MapColor.WATER.col,
                "Top map-colored surface must include water and skip map-transparent glass");
        helper.assertTrue(PlanetPalette.match(MapColor.COLOR_RED.col).is(Blocks.RED_CONCRETE), "Exact palette map color must not drift to another hue");
        helper.succeed();
    }

    @GameTest(template = "test_empty", batch = "planet_build", timeoutTicks = 2200)
    @SuppressWarnings("removal")
    public static void completePlanetAndReplaceInterruptedBuild(GameTestHelper helper) {
        var source = helper.getLevel();
        var orbit = source.getServer().getLevel(OrbitSession.ORBIT);
        var player = helper.makeMockServerPlayerInLevel();
        BlockPos launch = helper.absolutePos(new BlockPos(2048, 80, 2048));
        for (int z = 0; z < 9; z++) for (int x = 0; x < 9; x++) {
            var block = z < 4 ? (x < 4 ? Blocks.RED_CONCRETE : Blocks.BLUE_CONCRETE)
                    : (x < 4 ? Blocks.SMOOTH_SANDSTONE : Blocks.GREEN_CONCRETE);
            source.setBlockAndUpdate(new BlockPos(PlanetProjection.sampleCoordinate(launch.getX(), 16, x, 9), launch.getY(),
                    PlanetProjection.sampleCoordinate(launch.getZ(), 16, z, 9)), block.defaultBlockState());
        }
        player.connection.teleport(launch.getX() + 0.5, launch.getY() + 1, launch.getZ() + 0.5, 0, 0);
        helper.assertTrue(OrbitSession.enter(player), "Planet test visitor must enter orbit");
        OrbitPlanets.cancel(player); // Drive a deterministic fixture scan while retaining a real active visit.
        BlockPos station = OrbitStations.get(orbit).station(player.getUUID());
        var current = new AtomicReference<>(new PlanetBuildJob(player, source, orbit, station, launch.getX(), launch.getZ(), 16, 9, 15));
        var phase = new AtomicInteger();
        BlockPos oldMarker = PlanetProjection.center(station, 15).offset(0, 0, 15);
        var closed = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable cleanup = () -> {
            if (closed.getAndSet(true)) return;
            current.get().close();
            OrbitSession.returnHome(player);
            source.getServer().getPlayerList().remove(player);
        };
        helper.runAtTickTime(2198, cleanup);
        helper.onEachTick(() -> {
            if (closed.get()) return;
            try {
                var job = current.get();
                job.tick(256, System.nanoTime() + 3_000_000L);
                helper.assertTrue(job.lastUpdates() <= 256, "Planet updates exceeded per-tick cap");
                if (phase.get() == 0 && job.stage() == PlanetBuildJob.Stage.BUILD && job.lastUpdates() > 0) {
                    orbit.setBlockAndUpdate(oldMarker, Blocks.RED_CONCRETE.defaultBlockState());
                    job.close();
                    helper.assertTrue(!job.ticketHeld(), "Canceling a partial build must release its work ticket");
                    helper.assertTrue(!OrbitStations.get(orbit).planet(player.getUUID()).getList("Shapes", 10).isEmpty(), "Partial geometry must remain indexed for cleanup");
                    current.set(new PlanetBuildJob(player, source, orbit, station, launch.getX(), launch.getZ(), 16, 9, 80));
                    phase.set(1);
                } else if (phase.get() == 1 && job.stage() == PlanetBuildJob.Stage.DONE) {
                    helper.assertTrue(!job.ticketHeld(), "Completed build must release work ticket");
                    var data = OrbitStations.get(orbit).planet(player.getUUID());
                    helper.assertTrue(data.getBoolean("Complete") && data.getIntArray("Colors").length == 81 && data.getInt("Radius") == 80,
                            "Completed planet must persist sampled colors and source/geometry metadata");
                    helper.assertTrue(orbit.getBlockState(oldMarker).isAir(), "Previous partial globe must be cleared when radius changes");
                    BlockPos center = PlanetProjection.center(station, 80);
                    helper.assertTrue(orbit.getBlockState(center.offset(-40, 40, 56)).is(Blocks.RED_CONCRETE), "Northwest red terrain must paint upper-left front hemisphere");
                    helper.assertTrue(orbit.getBlockState(center.offset(40, 40, 56)).is(Blocks.BLUE_CONCRETE), "Northeast blue terrain must paint upper-right front hemisphere");
                    helper.assertTrue(orbit.getBlockState(center.offset(40, -40, 56)).is(Blocks.GREEN_CONCRETE), "Southeast green terrain must paint lower-right front hemisphere");
                    helper.assertTrue(orbit.getBlockState(center).isAir(), "Planet must have a hollow interior");
                    helper.assertTrue(source.getBlockState(launch).is(Blocks.GREEN_CONCRETE), "Sampling must not consume source terrain");
                    var serialized = OrbitStations.get(orbit).save(new net.minecraft.nbt.CompoundTag(), orbit.registryAccess());
                    helper.assertTrue(serialized.getList("Stations", 10).size() > 0, "Planet metadata must participate in world SavedData serialization");
                    cleanup.run();
                    helper.succeed();
                }
            } catch (RuntimeException | AssertionError failure) {
                cleanup.run();
                throw failure;
            }
        });
    }
}
