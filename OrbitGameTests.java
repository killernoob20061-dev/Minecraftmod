package com.worldeater.gametest;

import com.worldeater.*;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(WorldEater.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OrbitGameTests {
    @GameTest(template = "test_empty", timeoutTicks = 200)
    @SuppressWarnings("removal")
    public static void orbitRoundTripAndRecovery(GameTestHelper helper) {
        var level = helper.getLevel();
        var orbit = level.getServer().getLevel(OrbitSession.ORBIT);
        helper.assertTrue(orbit != null, "Orbit dimension/biome/type datapack must load");
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        boolean oldOp = WorldEaterConfig.ORBIT_OP_ONLY.get();
        try {
            WorldEaterConfig.ORBIT_OP_ONLY.set(false);
            BlockPos home = helper.absolutePos(new BlockPos(4, 2, 4));
            level.setBlockAndUpdate(home.below(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(home, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(home.above(), Blocks.AIR.defaultBlockState());
            Vec3 homePos = Vec3.atBottomCenterOf(home);
            player.connection.teleport(homePos.x, homePos.y, homePos.z, 37, 12);
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
            player.getInventory().selected = 2;
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(WorldEater.DEATH_STAR.get()));
            player.getInventory().setItem(5, new ItemStack(Items.DIAMOND, 7));
            player.setHealth(13);
            player.getFoodData().setFoodLevel(9);
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1200));
            Mass.add(player, 123);
            player.setExperienceLevels(11);
            player.totalExperience = 210;
            player.experienceProgress = 0.5F;

            var item = WorldEater.DEATH_STAR.get();
            item.use(level, player, InteractionHand.MAIN_HAND);
            helper.assertTrue(!OrbitSession.active(player), "First click must only warn");
            player.getCooldowns().removeCooldown(item);
            player.setData(OrbitSession.CONFIRMATION, (long) level.getServer().getTickCount() - 201);
            item.use(level, player, InteractionHand.MAIN_HAND);
            helper.assertTrue(!OrbitSession.active(player), "Expired confirmation must not enter orbit");
            player.getCooldowns().removeCooldown(item);
            item.use(level, player, InteractionHand.MAIN_HAND);
            helper.assertTrue(OrbitSession.active(player) && player.serverLevel() == orbit, "Second use must teleport to orbit");
            helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE, "Visit should protect cockpit from building/breaking");
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 0, "Original inventory must be held in saved state");
            helper.assertTrue(player.getInventory().countItem(item) == 1, "A return trigger must be provided");
            BlockPos station = OrbitStations.get(orbit).station(player.getUUID());
            BlockPos other = OrbitStations.get(orbit).station(UUID.randomUUID());
            helper.assertTrue(station.distSqr(other) >= 1024 * 1024, "Multiplayer cockpits must be separate");
            helper.assertTrue(orbit.getBlockState(station.offset(0, 3, -16)).is(Blocks.GLASS), "Front viewport must exist");
            helper.assertTrue(orbit.getBlockState(station.offset(0, 0, -12)).is(Blocks.GLASS), "Observation floor must give a clear lower view");
            helper.assertTrue(orbit.getBlockState(station.offset(30, 0, 0)).isAir(), "Orbit generator must be void outside cockpit");

            ItemStack tossed = player.getInventory().removeItem(0, 1);
            var toss = new ItemTossEvent(new ItemEntity(orbit, player.getX(), player.getY(), player.getZ(), tossed), player);
            OrbitSession.onToss(toss);
            helper.assertTrue(toss.isCanceled() && player.getInventory().countItem(item) == 1, "Toss cancellation must preserve exactly one trigger");
            var drops = new LivingDropsEvent(player, player.damageSources().generic(), new ArrayList<>(), false);
            OrbitSession.onDrops(drops);
            var xp = new LivingExperienceDropEvent(player, null, 7);
            OrbitSession.onExperienceDrop(xp);
            helper.assertTrue(drops.isCanceled() && xp.isCanceled(), "Orbit death must not duplicate saved items or XP");

            CompoundTag playerSave = player.saveWithoutId(new CompoundTag());
            var clone = new net.neoforged.neoforge.common.util.FakePlayer(level,
                    new com.mojang.authlib.GameProfile(UUID.randomUUID(), "orbit-save-test"));
            clone.load(playerSave);
            helper.assertTrue(OrbitSession.active(clone), "Return attachment must survive player serialization");
            var deathClone = new net.neoforged.neoforge.common.util.FakePlayer(level,
                    new com.mojang.authlib.GameProfile(UUID.randomUUID(), "orbit-death-test"));
            deathClone.copyAttachmentsFrom(player, true);
            helper.assertTrue(OrbitSession.active(deathClone), "Death cloning must preserve the recovery record");

            var source = player.createCommandSourceStack().withPermission(0);
            var commandRoot = level.getServer().getCommands().getDispatcher().getRoot().getChild("worldeater");
            helper.assertTrue(commandRoot.canUse(source) && commandRoot.getChild("return").canUse(source), "Emergency return must work without operator permissions");
            helper.assertTrue(!commandRoot.getChild("stop").canUse(source), "Stopping all singularities must remain operator-only");
            level.getServer().getCommands().performPrefixedCommand(source, "worldeater return");
            assertRestored(helper, player, homePos);
            helper.assertTrue(!OrbitPlanets.active(player), "Returning early must cancel the scan/build job");
            helper.assertTrue(!OrbitSession.returnHome(player), "Second return must not restore/duplicate again");

            helper.assertTrue(OrbitSession.enter(player), "Second visit should start");
            OrbitSession.onLogin(new PlayerEvent.PlayerLoggedInEvent(player));
            player.tickCount = 20;
            OrbitSession.onTick(new PlayerTickEvent.Post(player));
            assertRestored(helper, player, homePos);

            helper.assertTrue(OrbitSession.enter(player), "Third visit should start");
            player.teleportTo(level, homePos.x + 1, homePos.y, homePos.z, 0, 0);
            OrbitSession.onTick(new PlayerTickEvent.Post(player));
            assertRestored(helper, player, homePos);

            helper.assertTrue(OrbitSession.enter(player), "Fourth visit should start");
            level.setBlockAndUpdate(home.below(), Blocks.AIR.defaultBlockState());
            OrbitSession.onRespawn(new PlayerEvent.PlayerRespawnEvent(player, false));
            OrbitSession.onTick(new PlayerTickEvent.Post(player));
            helper.assertTrue(!OrbitSession.active(player) && player.serverLevel() == level, "Respawn recovery should finish");
            helper.assertTrue(level.getBlockState(player.blockPosition().below()).is(Blocks.BEDROCK), "Destroyed return spot must get a rescue platform");
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 7, "Recovery must preserve exact inventory count");
        } finally {
            WorldEaterConfig.ORBIT_OP_ONLY.set(oldOp);
            if (OrbitSession.active(player)) OrbitSession.returnHome(player);
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    private static void assertRestored(GameTestHelper helper, ServerPlayer player, Vec3 home) {
        helper.assertTrue(!OrbitSession.active(player) && player.serverLevel() == helper.getLevel(), "Return should clear the session");
        helper.assertTrue(player.position().distanceToSqr(home) < 0.001, "Safe original position must be preserved");
        helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 7 && player.getInventory().selected == 2, "Inventory and selected slot must restore");
        helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL && !player.getAbilities().invulnerable, "Original mode/abilities must restore");
        helper.assertTrue(player.getHealth() == 13 && player.getFoodData().getFoodLevel() == 9, "Health and hunger must restore");
        helper.assertTrue(player.getEffect(MobEffects.NIGHT_VISION) != null && player.getEffect(MobEffects.NIGHT_VISION).getDuration() == 1200, "Effects must restore");
        helper.assertTrue(player.experienceLevel == 11 && player.totalExperience == 210 && player.experienceProgress == 0.5F, "XP must restore");
        helper.assertTrue(Mass.get(player) == 123, "Mass must restore");
    }
}
