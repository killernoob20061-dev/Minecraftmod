package com.worldeater;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class DeathStarItem extends Item {
    public DeathStarItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer server) || player.getCooldowns().isOnCooldown(this))
            return InteractionResultHolder.fail(stack);
        if (OrbitSession.active(server) || level.dimension().equals(OrbitSession.ORBIT)) {
            OrbitSession.returnHome(server);
        } else if (LaserJobs.get(server.getServer()).active(server.getUUID())) {
            server.sendSystemMessage(Component.translatable("message.worldeater.laser_still_running"));
            return InteractionResultHolder.fail(stack);
        } else if (!server.isAlive() || server.isSpectator() || server.isPassenger() || server.isSleeping()) {
            server.sendSystemMessage(Component.translatable("message.worldeater.orbit_unavailable"));
            return InteractionResultHolder.fail(stack);
        } else if (WorldEaterConfig.ORBIT_OP_ONLY.get() && !server.getServer().isSingleplayer() && !server.hasPermissions(2)) {
            server.sendSystemMessage(Component.translatable("message.worldeater.orbit_op_only"));
            return InteractionResultHolder.fail(stack);
        } else {
            long now = server.getServer().getTickCount();
            long confirmedAt = server.getData(OrbitSession.CONFIRMATION);
            if (confirmedAt != Long.MIN_VALUE && now >= confirmedAt && now - confirmedAt <= 200) {
                server.removeData(OrbitSession.CONFIRMATION);
                OrbitSession.enter(server);
            } else {
                server.setData(OrbitSession.CONFIRMATION, now);
                server.sendSystemMessage(Component.translatable("message.worldeater.orbit_confirm", WorldEaterConfig.LASER_RADIUS.get()).withStyle(ChatFormatting.GOLD));
            }
        }
        player.getCooldowns().addCooldown(this, 10);
        return InteractionResultHolder.consume(stack);
    }
}
