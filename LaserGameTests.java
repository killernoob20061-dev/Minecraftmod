package com.worldeater.gametest;

import com.worldeater.*;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(WorldEater.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LaserGameTests {
    @GameTest(template = "test_empty", batch = "laser_math", timeoutTicks = 80)
    public static void aimAndResumableSphereCoverage(GameTestHelper helper) {
        Vec3 hit = LaserAim.intersect(new Vec3(0, 0, 200), new Vec3(0, 0, -1), Vec3.ZERO, 80);
        helper.assertTrue(hit != null && hit.distanceToSqr(new Vec3(0, 0, 80)) < 0.0001, "Long-range aim must select the nearest sphere surface");
        helper.assertTrue(LaserAim.intersect(new Vec3(0, 0, 200), new Vec3(0, 0, 1), Vec3.ZERO, 80) == null, "A sphere behind the eye must not be targeted");
        helper.assertTrue(LaserAim.intersect(new Vec3(0, 0, 200), new Vec3(1, 0, 0), Vec3.ZERO, 80) == null, "Missed aim must not produce a target");
        var level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(400, 24, 400));
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-8, -8, -8), center.offset(8, 8, 8)))
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        var cursor = new ShockwaveCursor(center, 8);
        var actual = new HashSet<BlockPos>();
        double lastDistance = -1;
        int iterations = 0;
        while (!cursor.finished()) {
            helper.assertTrue(cursor.distance() >= lastDistance, "Chunk front must progress outward"); lastDistance = cursor.distance();
            var cp = cursor.chunk(); var chunk = level.getChunk(cp.x, cp.z);
            BlockPos pos = cursor.peek(chunk, level.getMinBuildHeight(), level.getMaxBuildHeight());
            if (pos != null) { helper.assertTrue(actual.add(pos), "Checkpoint must not duplicate block candidates"); cursor.advance(); }
            if (++iterations == 80) { var save = cursor.save(); cursor = new ShockwaveCursor(center, 8); cursor.restore(save); }
            helper.assertTrue(iterations < 10000, "Sphere cursor must terminate");
        }
        var expected = new HashSet<BlockPos>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-8, -8, -8), center.offset(8, 8, 8)))
            if (pos.distSqr(center) <= 64) expected.add(pos.immutable());
        helper.assertTrue(actual.equals(expected), "Terrain cursor must cover exactly the sphere after save/load");
        for (int r : new int[] {60, 80, 100}) helper.assertTrue(PlanetProjection.center(new BlockPos(0, 224, 0), r).getY() + r < 224,
                "Entire planet must lie below the bridge at every supported radius");
        helper.succeed();
    }

    @GameTest(template = "test_empty", batch = "laser_fire", timeoutTicks = 1800)
    @SuppressWarnings("removal")
    public static void chargeDestroyRecoverAndFinale(GameTestHelper helper) {
        var source = helper.getLevel(); var player = helper.makeMockServerPlayerInLevel();
        var restore = new java.util.ArrayList<Runnable>();
        config(WorldEaterConfig.OP_ONLY, false, restore); config(WorldEaterConfig.ORBIT_OP_ONLY, false, restore);
        config(WorldEaterConfig.LASER_RADIUS, 8, restore); config(WorldEaterConfig.LASER_CHARGE_TICKS, 20, restore);
        config(WorldEaterConfig.LASER_BLOCKS, 16, restore); config(WorldEaterConfig.LASER_SCANS, 512, restore);
        config(WorldEaterConfig.LASER_SPEED, 256.0, restore);
        config(WorldEaterConfig.SKIP_UNBREAKABLE, true, restore); config(WorldEaterConfig.SKIP_BLOCK_ENTITIES, true, restore);
        BlockPos target = helper.absolutePos(new BlockPos(600, 30, 600));
        for (BlockPos pos : BlockPos.betweenClosed(target.offset(-9, -2, -9), target.offset(9, 0, 9)))
            source.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        source.setBlock(target.east(), Blocks.BEDROCK.defaultBlockState(), 2);
        source.setBlock(target.west(), Blocks.CHEST.defaultBlockState(), 2);
        source.setBlock(target.south(), Blocks.WATER.defaultBlockState(), 2);
        Consumer<BlockEvent.BreakEvent> deny = event -> {
            if (event.getLevel() == source && event.getPos().equals(target.north())) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(deny);
        AtomicBoolean cleaned = new AtomicBoolean(), resumed = new AtomicBoolean();
        Runnable cleanup = () -> {
            if (cleaned.getAndSet(true)) return;
            LaserJobs.get(source.getServer()).stopAll();
            if (OrbitSession.active(player)) OrbitSession.returnHome(player);
            source.getServer().getPlayerList().remove(player);
            NeoForge.EVENT_BUS.unregister(deny); restore.forEach(Runnable::run);
        };
        helper.runAtTickTime(1798, cleanup);
        try {
            prepareVisitor(helper, player, target);
            var trigger = WorldEater.SUPERLASER_TRIGGER.get();
            var stack = player.getMainHandItem();
            helper.assertTrue(LaserControl.canCharge(player, false), "Ready planet and permission should allow charge");
            trigger.use(player.level(), player, InteractionHand.MAIN_HAND);
            trigger.onUseTick(player.level(), player, stack, trigger.getUseDuration(stack, player) - 10);
            player.releaseUsingItem();
            helper.assertTrue(!LaserJobs.get(source.getServer()).active(player.getUUID()), "Early release must cancel without firing");
            trigger.use(player.level(), player, InteractionHand.MAIN_HAND);
            for (int elapsed = 0; elapsed <= 20; elapsed++)
                trigger.onUseTick(player.level(), player, stack, trigger.getUseDuration(stack, player) - elapsed);
            helper.assertTrue(LaserJobs.get(source.getServer()).active(player.getUUID()), "Full charge must schedule one shot");
            helper.assertTrue(!LaserControl.fire(player), "A spent globe must reject repeated fire");
        } catch (RuntimeException | AssertionError failure) { cleanup.run(); throw failure; }
        helper.onEachTick(() -> {
            if (cleaned.get()) return;
            try {
                var jobs = LaserJobs.get(source.getServer()); var shot = jobs.shot(player.getUUID());
                if (shot != null) {
                    helper.assertTrue(shot.lastBlocks() <= 16 && shot.lastScans() <= 512, "Global block/scan caps must hold");
                    helper.assertTrue(!shot.paused(), "Valid shot must not pause unexpectedly");
                    if (shot.stage() == LaserShot.Stage.WAVE && shot.save().getLong("Removed") > 0 && !resumed.getAndSet(true)) {
                        var saved = jobs.save(new CompoundTag(), source.registryAccess());
                        jobs.stopAll();
                        helper.assertTrue(!shot.ticketHeld(), "Stopping old runtime for reload must release its lease");
                        var loaded = LaserJobs.load(saved, source.registryAccess());
                        jobs.add(loaded.shot(player.getUUID()));
                    }
                } else {
                    helper.assertTrue(resumed.get(), "Test must pass through a mid-wave serialization/reload");
                    helper.assertTrue(source.getBlockState(target).isAir(), "Source target must be removed");
                    helper.assertTrue(source.getBlockState(target.east()).is(Blocks.BEDROCK), "Protected bedrock must remain");
                    helper.assertTrue(source.getBlockState(target.west()).is(Blocks.CHEST), "Protected container must remain");
                    helper.assertTrue(source.getBlockState(target.north()).is(Blocks.STONE), "Canceled protection hook must be respected");
                    helper.assertTrue(source.getBlockState(target.south()).isAir(), "Water must be removed within the blast");
                    helper.assertTrue(source.getBlockState(target.offset(9, 0, 0)).is(Blocks.STONE), "Blocks beyond the radius must remain");
                    helper.assertTrue(!OrbitSession.active(player) && player.serverLevel() == source, "Finale must automatically return the visitor");
                    helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 3, "Finale return must restore inventory exactly once");
                    var orbit = source.getServer().getLevel(OrbitSession.ORBIT);
                    var data = OrbitStations.get(orbit).planet(player.getUUID());
                    helper.assertTrue(!data.getBoolean("Complete") && data.getList("Shapes", 10).isEmpty(), "Finale must retire the planet geometry");
                    helper.assertTrue(orbit.getBlockState(BlockPos.of(data.getLong("Center")).offset(0, 0, 12)).isAir(), "Finale must remove planet surface blocks");
                    cleanup.run(); helper.succeed();
                }
            } catch (RuntimeException | AssertionError failure) { cleanup.run(); throw failure; }
        });
    }

    @GameTest(template = "test_empty", batch = "laser_departure", timeoutTicks = 100)
    @SuppressWarnings("removal")
    public static void departureShutdownAndOperatorStop(GameTestHelper helper) {
        var source = helper.getLevel(); var player = helper.makeMockServerPlayerInLevel();
        var restore = new java.util.ArrayList<Runnable>();
        config(WorldEaterConfig.OP_ONLY, false, restore); config(WorldEaterConfig.ORBIT_OP_ONLY, false, restore);
        config(WorldEaterConfig.LASER_RADIUS, 4, restore);
        try {
            BlockPos target = helper.absolutePos(new BlockPos(800, 30, 800));
            source.setBlock(target, Blocks.STONE.defaultBlockState(), 2);
            prepareVisitor(helper, player, target);
            helper.assertTrue(LaserControl.fire(player), "A ready authorized shot should fire");
            var jobs = LaserJobs.get(source.getServer()); var shot = jobs.shot(player.getUUID());
            shot.tick(source.getServer(), 16, 512, System.nanoTime() + 10_000_000L);
            helper.assertTrue(shot.ticketHeld(), "Shot should acquire a target work ticket");
            OrbitSession.returnHome(player);
            helper.assertTrue(jobs.active(player.getUUID()), "Leaving orbit must not silently cancel a fired blast");
            LaserJobs.onStop(new ServerStoppingEvent(source.getServer()));
            helper.assertTrue(!shot.ticketHeld() && jobs.active(player.getUUID()), "Shutdown releases tickets but retains durable work");
            helper.assertTrue(LaserJobs.load(jobs.save(new CompoundTag(), source.registryAccess()), source.registryAccess()).active(player.getUUID()),
                    "Shutdown checkpoint must remain reloadable");
            var root = source.getServer().getCommands().getDispatcher().getRoot().getChild("worldeater");
            helper.assertTrue(!root.getChild("stoplaser").canUse(player.createCommandSourceStack().withPermission(0)), "Canceling destructive jobs must require operator permission");
            source.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(2), "worldeater stoplaser");
            helper.assertTrue(!jobs.active(player.getUUID()) && !shot.ticketHeld(), "Operator stop must clear job and ticket");
        } finally {
            LaserJobs.get(source.getServer()).stopAll();
            if (OrbitSession.active(player)) OrbitSession.returnHome(player);
            source.getServer().getPlayerList().remove(player); restore.forEach(Runnable::run);
        }
        helper.succeed();
    }

    private static void prepareVisitor(GameTestHelper helper, ServerPlayer player, BlockPos target) {
        var source = helper.getLevel();
        BlockPos home = helper.absolutePos(new BlockPos(3, 2, 3));
        source.setBlockAndUpdate(home.below(), Blocks.STONE.defaultBlockState());
        player.connection.teleport(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0, 0);
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent(); player.getInventory().selected = 0;
        player.getInventory().setItem(0, new ItemStack(WorldEater.DEATH_STAR.get()));
        player.getInventory().setItem(5, new ItemStack(Items.DIAMOND, 3));
        helper.assertTrue(OrbitSession.enter(player), "Test visitor must enter orbit");
        OrbitPlanets.cancel(player);
        var orbit = player.serverLevel(); var stations = OrbitStations.get(orbit);
        BlockPos center = PlanetProjection.center(stations.station(player.getUUID()), 12);
        var cursor = PlanetProjection.shell(center, 12);
        while (!cursor.finished()) { var pos = cursor.peek(); if (pos != null) { orbit.setBlock(pos, Blocks.GREEN_CONCRETE.defaultBlockState(), 2); cursor.advance(); } }
        CompoundTag planet = new CompoundTag(); planet.putBoolean("Complete", true); planet.putInt("Radius", 12);
        planet.putLong("Center", center.asLong()); planet.putInt("ScanRadius", 16);
        planet.putString("SourceDimension", source.dimension().location().toString());
        planet.putInt("SourceX", target.getX()); planet.putInt("SourceZ", target.getZ());
        var direction = Vec3.atCenterOf(center).subtract(player.getEyePosition()).normalize();
        player.setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
        player.setXRot((float) -Math.toDegrees(Math.asin(direction.y)));
        var aim = LaserAim.target(player.getEyePosition(), player.getLookAngle(), planet);
        planet.putInt("SourceX", planet.getInt("SourceX") + target.getX() - aim.x());
        planet.putInt("SourceZ", planet.getInt("SourceZ") + target.getZ() - aim.z());
        CompoundTag shape = new CompoundTag(); shape.putLong("Center", center.asLong()); shape.putInt("Radius", 12);
        ListTag shapes = new ListTag(); shapes.add(shape); planet.put("Shapes", shapes); stations.savePlanet(player.getUUID(), planet);
        player.getInventory().selected = 1;
    }
    private static <T> void config(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<T> value, T replacement, java.util.List<Runnable> restore) {
        T old = value.get(); restore.add(() -> value.set(old)); value.set(replacement);
    }
}
