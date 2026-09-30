package com.worldeater;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public final class SuperlaserTriggerItem extends Item {
    public SuperlaserTriggerItem(Properties properties) { super(properties); }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BOW; }
    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return WorldEaterConfig.LASER_CHARGE_TICKS.get() + 1; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && (!(player instanceof ServerPlayer server) || !LaserControl.canCharge(server, true)))
            return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }
    @Override public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!(entity instanceof ServerPlayer player)) return;
        if (!LaserControl.canCharge(player, false)) { player.stopUsingItem(); return; }
        int elapsed = getUseDuration(stack, entity) - remaining;
        if (elapsed % 5 == 0) player.displayClientMessage(Component.translatable("message.worldeater.laser_charging",
                Math.min(100, elapsed * 100 / WorldEaterConfig.LASER_CHARGE_TICKS.get())), true);
        if (elapsed >= WorldEaterConfig.LASER_CHARGE_TICKS.get()) {
            LaserControl.fire(player);
            player.stopUsingItem();
        }
    }
}
