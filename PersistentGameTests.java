package com.worldeater.gametest;

import com.mojang.authlib.GameProfile;
import com.worldeater.*;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(WorldEater.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PersistentGameTests {
    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void shellCoverageAndResume(GameTestHelper helper) {
        for (BlockPos center : new BlockPos[]{BlockPos.ZERO, new BlockPos(-17, 3, 15)}) {
            for (int radius = 1; radius <= 20; radius++) {
                var cursor = cursor(center, radius);
                var actual = new HashSet<BlockPos>();
                int steps = 0;
                while (!cursor.finished()) {
                    BlockPos pos = cursor.peek();
                    if (pos != null) {
                        helper.assertTrue(actual.add(pos), "Shell returned a duplicate block");
                        cursor.advance();
                    }
                    if (++steps == 200) {
                        CompoundTag state = cursor.save();
                        cursor = cursor(center, radius);
                        cursor.restore(state);
                    }
                    helper.assertTrue(steps < 100000, "Shell iterator did not terminate");
                }
                var expected = new HashSet<BlockPos>();
                for (int x = Math.max(-32, center.getX() - radius); x <= Math.min(32, center.getX() + radius); x++)
                    for (int y = -8; y < 9; y++)
                        for (int z = Math.max(-32, center.getZ() - radius); z <= Math.min(32, center.getZ() + radius); z++) {
                            BlockPos pos = new BlockPos(x, y, z);
                            double d = pos.distSqr(center);
                            if (d <= radius * radius && (radius == 1 || d > (radius - 1) * (radius - 1))) expected.add(pos);
                        }
                helper.assertTrue(actual.equals(expected), "Shell has missing or out-of-range blocks at radius " + radius + " center " + center);
            }
        }
        // Large radii must remain incremental, without overflow or radius-cubed allocations.
        var large = new ExpandingShellCursor(BlockPos.ZERO, 30000001, -64, 320, -29999984, 29999983, -29999984, 29999983);
        for (int i = 0; i < 2000 && !large.finished(); i++) if (large.peek() != null) large.advance();
        helper.succeed();
    }

    private static ExpandingShellCursor cursor(BlockPos origin, int radius) {
        return new ExpandingShellCursor(origin, radius, -8, 9, -32, 32, -32, 32);
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void impactPersistenceAndTicketCleanup(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "worldeater-impact")) {
            @Override public boolean hasPermissions(int permission) { return true; }
        };
        BlockPos target = helper.absolutePos(new BlockPos(6, 3, 6));
        level.setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());
        var hole = new SingularityEntity(WorldEater.SINGULARITY.get(), level);
        hole.setOwner(owner);
        hole.setPos(Vec3.atCenterOf(target.west()));
        hole.setDeltaMovement(new Vec3(0.18, 0, 0));
        for (int i = 0; i < 8 && !hole.isAnchored(); i++) hole.tick();
        helper.assertTrue(hole.isAnchored(), "Projectile must settle on block impact");
        helper.assertTrue(SingularityAnchors.get(level).contains(hole.getUUID()), "Impact must register a persistent anchor");
        Vec3 anchoredPosition = hole.position();
        CompoundTag entityTag = hole.saveWithoutId(new CompoundTag());
        var loaded = new SingularityEntity(WorldEater.SINGULARITY.get(), level);
        loaded.load(entityTag);
        helper.assertTrue(loaded.isAnchored() && loaded.position().equals(anchoredPosition), "Save/load must retain the impact location");
        helper.assertTrue(loaded.saveWithoutId(new CompoundTag()).getCompound("ShellCursor").equals(entityTag.getCompound("ShellCursor")),
                "Save/load must retain the exact next shell candidate");
        boolean opOnly = WorldEaterConfig.OP_ONLY.get();
        int lifetime = WorldEaterConfig.LIFETIME.get();
        double limit = WorldEaterConfig.MAX_RADIUS.get(), growth = WorldEaterConfig.PASSIVE_GROWTH.get();
        try {
            WorldEaterConfig.OP_ONLY.set(false);
            WorldEaterConfig.LIFETIME.set(0);
            WorldEaterConfig.MAX_RADIUS.set(0.0);
            WorldEaterConfig.PASSIVE_GROWTH.set(4.0);
            // No live/cached owner is attached to the restored entity. It must still feed and pass the old radius cap.
            for (int i = 0; i < 300; i++) loaded.tick();
            helper.assertTrue(!loaded.isRemoved(), "Owner logout must not delete a persistent black hole");
            helper.assertTrue(loaded.radius() > 6, "World-eating mode must expand beyond the old six-block cap");
            helper.assertTrue(loaded.saveWithoutId(new CompoundTag()).getInt("PendingMass") > 0,
                    "Mass earned for an offline owner must remain pending in saved entity state");
        } finally {
            WorldEaterConfig.OP_ONLY.set(opOnly); WorldEaterConfig.LIFETIME.set(lifetime);
            WorldEaterConfig.MAX_RADIUS.set(limit); WorldEaterConfig.PASSIVE_GROWTH.set(growth);
        }
        // Exercise an actual remote work ticket and its release, without loading a whole region.
        ChunkPos work = new ChunkPos(target.offset(96, 0, 0));
        SingularityTickets.CONTROLLER.forceChunk(level, hole.getUUID(), work.x, work.z, true, false);
        SingularityAnchors.get(level).setWorkChunk(hole.getUUID(), work.toLong());
        helper.assertTrue(level.hasChunk(work.x, work.z), "Work ticket must load its target chunk");
        helper.assertTrue(SingularityAnchors.get(level).workChunk(hole.getUUID()) != null, "Work ticket must be tracked for cleanup");
        loaded.discard();
        hole.discard();
        helper.assertTrue(!SingularityAnchors.get(level).contains(hole.getUUID()), "Discard must remove the anchor");
        helper.assertTrue(SingularityAnchors.get(level).workChunk(hole.getUUID()) == null, "Discard must clear the work-ticket record");
        // add=true returning true proves the prior ticket was actually removed from NeoForge's tracker.
        helper.assertTrue(SingularityTickets.CONTROLLER.forceChunk(level, hole.getUUID(), work.x, work.z, true, false), "Work ticket leaked after removal");
        SingularityTickets.CONTROLLER.forceChunk(level, hole.getUUID(), work.x, work.z, false, false);
        ChunkPos core = new ChunkPos(target);
        helper.assertTrue(SingularityTickets.CONTROLLER.forceChunk(level, hole.getUUID(), core.x, core.z, true, true), "Core ticket leaked after removal");
        SingularityTickets.CONTROLLER.forceChunk(level, hole.getUUID(), core.x, core.z, false, true);
        var stoppable = new SingularityEntity(WorldEater.SINGULARITY.get(), level);
        stoppable.setOwner(owner);
        stoppable.setPos(Vec3.atCenterOf(target));
        level.addFreshEntity(stoppable);
        stoppable.anchorAt(target);
        helper.assertTrue(SingularityTickets.stopAll(level) >= 1 && stoppable.isRemoved(), "Stop command must remove active holes");
        helper.assertTrue(SingularityAnchors.get(level).entries().isEmpty(), "Stop command must clear saved anchors");
        helper.succeed();
    }
}
