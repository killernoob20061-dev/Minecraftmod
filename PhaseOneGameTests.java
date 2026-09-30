package com.worldeater.gametest;

import com.mojang.authlib.GameProfile;
import com.worldeater.Mass;
import com.worldeater.SingularityEntity;
import com.worldeater.WorldEater;
import com.worldeater.WorldEaterConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Runs only in development; this class and its structure are excluded from the release JAR. */
@GameTestHolder(WorldEater.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PhaseOneGameTests {
    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void serverMechanics(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "worldeater-test")) {
            @Override public boolean hasPermissions(int permissionLevel) { return true; }
        };
        owner.setGameMode(GameType.SURVIVAL);
        owner.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 3, 1))));
        var center = helper.absolutePos(new BlockPos(6, 3, 6));
        List<Runnable> restore = new ArrayList<>();
        Consumer<BlockEvent.BreakEvent> denyCenter = event -> {
            if (event.getLevel() == level && event.getPos().equals(center)) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(denyCenter);
        SingularityEntity hole = new SingularityEntity(WorldEater.SINGULARITY.get(), level);
        try {
            override(WorldEaterConfig.BLOCKS_PER_TICK, 2, restore);
            override(WorldEaterConfig.SCANS_PER_TICK, 512, restore);
            override(WorldEaterConfig.TICK_BUDGET_MS, 10.0, restore);
            override(WorldEaterConfig.MAX_RADIUS, 2.0, restore);
            override(WorldEaterConfig.GROWTH, 0.1, restore);
            override(WorldEaterConfig.BLOCK_MASS, 1, restore);
            override(WorldEaterConfig.MOB_MASS, 25, restore);
            override(WorldEaterConfig.MAX_MASS, 100, restore);
            override(WorldEaterConfig.DECAY, 1, restore);
            override(WorldEaterConfig.LIFETIME, 0, restore);
            override(WorldEaterConfig.DESTROY_BLOCKS, true, restore);
            override(WorldEaterConfig.OP_ONLY, true, restore);
            override(WorldEaterConfig.DROP_BLOCKS, false, restore);
            override(WorldEaterConfig.SKIP_UNBREAKABLE, true, restore);
            override(WorldEaterConfig.SKIP_BLOCK_ENTITIES, true, restore);

            Mass.add(owner, 1000);
            helper.assertTrue(Mass.get(owner) == 100, "Mass must clamp at the configured maximum");
            owner.tickCount = 20;
            Mass.onPlayerTick(new PlayerTickEvent.Post(owner));
            helper.assertTrue(Mass.get(owner) == 99, "Mass should decay once per second");
            CompoundTag playerSave = owner.saveWithoutId(new CompoundTag());
            var restored = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "worldeater-restore"));
            restored.load(playerSave);
            helper.assertTrue(Mass.get(restored) == 99, "Mass attachment must survive player save/load");
            Mass.add(owner, -1000);
            helper.assertTrue(Mass.get(owner) == 0, "Mass must not become negative");

            var nonOp = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "worldeater-nonop")) {
                @Override public boolean hasPermissions(int permissionLevel) { return false; }
            };
            if (!level.getServer().isSingleplayer())
                helper.assertTrue(!SingularityEntity.mayConsumeBlocks(level, nonOp), "Non-operators must not consume blocks");
            helper.assertTrue(SingularityEntity.mayConsumeBlocks(level, owner), "Operator should be allowed to consume blocks");

            // A small sphere of stone with three independently protected blocks.
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -1, -2), center.offset(2, 1, 2)))
                level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(center.east(), Blocks.BEDROCK.defaultBlockState());
            level.setBlockAndUpdate(center.west(), Blocks.CHEST.defaultBlockState());
            hole.setOwner(owner);
            hole.anchorAt(center);
            int consumed = 0;
            for (int tick = 0; tick < 30; tick++) {
                hole.tick();
                helper.assertTrue(hole.lastConsumedBlocks() <= 2, "Per-tick removal cap exceeded");
                helper.assertTrue(hole.lastScannedPositions() <= 512, "Candidate scan cap exceeded");
                helper.assertTrue(hole.radius() <= 2.0, "Radius exceeded maximum");
                consumed += hole.lastConsumedBlocks();
            }
            helper.assertTrue(consumed > 0, "Singularity must actually consume blocks");
            helper.assertTrue(Mass.get(owner) == consumed, "Only successfully consumed blocks award Mass");
            helper.assertTrue(hole.radius() > 1.0F, "Consumption must grow the singularity");
            helper.assertTrue(level.getBlockState(center).is(Blocks.STONE), "Canceled block break was ignored");
            helper.assertTrue(level.getBlockState(center.east()).is(Blocks.BEDROCK), "Bedrock was removed");
            helper.assertTrue(level.getBlockState(center.west()).is(Blocks.CHEST), "Protected block entity was removed");

            // Disable terrain work and test a mob kill and pull without conflating Mass sources.
            WorldEaterConfig.DESTROY_BLOCKS.set(false);
            int massBefore = Mass.get(owner);
            Chicken chicken = helper.spawn(EntityType.CHICKEN, 6.7F, 3.4F, 6.5F);
            chicken.setNoAi(true);
            ItemEntity item = new ItemEntity(level, center.getX() + 3.0, center.getY() + 0.5,
                    center.getZ() + 0.5, new ItemStack(Items.DIRT));
            item.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(item);
            for (int tick = 0; tick < 20; tick++) hole.tick();
            helper.assertTrue(!chicken.isAlive(), "A mob in the core should be consumed");
            helper.assertTrue(Mass.get(owner) == Math.min(100, massBefore + 25), "Mob must award Mass exactly once");
            helper.assertTrue(item.getDeltaMovement().x < 0, "Dropped item should be pulled toward the core");
            helper.assertTrue(item.isAlive(), "Dropped items must remain recoverable");
            helper.assertTrue(hole.lastConsumedBlocks() == 0, "Disabled block consumption must remove nothing");
            item.discard();

            CompoundTag entitySave = hole.saveWithoutId(new CompoundTag());
            SingularityEntity loaded = new SingularityEntity(WorldEater.SINGULARITY.get(), level);
            loaded.load(entitySave);
            helper.assertTrue(loaded.radius() == hole.radius(), "Singularity radius should persist");
            loaded.setOwner(owner);
            for (int tick = 0; tick < 250; tick++) loaded.tick();
            helper.assertTrue(!loaded.isRemoved() && loaded.isAnchored(), "Persistent singularity must outlive the old 200-tick expiry");
            helper.assertTrue(loaded.getDeltaMovement().equals(Vec3.ZERO), "Anchored singularity must remain stationary");
            loaded.discard();
            helper.assertTrue(!com.worldeater.SingularityAnchors.get(level).contains(loaded.getUUID()), "Removal must clear the anchor index");

            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(WorldEater.SINGULARITY_CANNON.get()));
            int beforeShot = countHoles(helper);
            var cannon = WorldEater.SINGULARITY_CANNON.get();
            cannon.use(level, owner, InteractionHand.MAIN_HAND);
            helper.assertTrue(countHoles(helper) == beforeShot + 1, "Cannon should spawn exactly one projectile");
            cannon.use(level, owner, InteractionHand.MAIN_HAND);
            helper.assertTrue(countHoles(helper) == beforeShot + 1, "Cooldown must prevent repeated fire");
        } finally {
            hole.discard();
            level.getAllEntities().forEach(entity -> {
                if (entity instanceof SingularityEntity s && s.getOwner() == owner) s.discard();
            });
            NeoForge.EVENT_BUS.unregister(denyCenter);
            restore.forEach(Runnable::run);
        }
        helper.succeed();
    }

    private static int countHoles(GameTestHelper helper) {
        int count = 0;
        for (var entity : helper.getLevel().getAllEntities()) if (entity instanceof SingularityEntity && entity.isAlive()) count++;
        return count;
    }

    private static <T> void override(ModConfigSpec.ConfigValue<T> value, T replacement, List<Runnable> restore) {
        T original = value.get();
        restore.add(() -> value.set(original));
        value.set(replacement);
    }
}
