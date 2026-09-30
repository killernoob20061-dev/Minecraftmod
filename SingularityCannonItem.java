package com.worldeater;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class SingularityCannonItem extends Item {
    public SingularityCannonItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer owner) || owner.isSpectator() || player.getCooldowns().isOnCooldown(this))
            return InteractionResultHolder.fail(stack);
        ServerLevel server = owner.serverLevel();
        if (server.dimension().equals(OrbitSession.ORBIT)) {
            player.displayClientMessage(Component.translatable("message.worldeater.orbit_cannon_disabled"), true);
            return InteractionResultHolder.fail(stack);
        }
        int active = SingularityAnchors.get(server).entries().size();
        int owned = 0;
        for (Entity entity : server.getAllEntities()) {
            if (entity instanceof SingularityEntity singularity && singularity.isAlive()) {
                if (!singularity.isAnchored()) active++;
                if (singularity.getOwner() == player) owned++;
            }
        }
        if (owned >= 2 || active >= WorldEaterConfig.MAX_ACTIVE.get()) {
            player.displayClientMessage(Component.translatable("message.worldeater.singularity_limit"), true);
            return InteractionResultHolder.fail(stack);
        }
        Vec3 look = player.getLookAngle();
        Vec3 spawn = player.getEyePosition().add(look.scale(0.3));
        if (!server.hasChunkAt(net.minecraft.core.BlockPos.containing(spawn))) return InteractionResultHolder.fail(stack);
        SingularityEntity singularity = new SingularityEntity(WorldEater.SINGULARITY.get(), server);
        singularity.setOwner(player);
        singularity.setPos(spawn);
        singularity.setDeltaMovement(look.scale(WorldEaterConfig.SPEED.get()));
        if (!server.addFreshEntity(singularity)) return InteractionResultHolder.fail(stack);
        player.getCooldowns().addCooldown(this, WorldEaterConfig.COOLDOWN.get());
        if (!SingularityEntity.mayConsumeBlocks(server, owner))
            player.displayClientMessage(Component.translatable("message.worldeater.block_destruction_disabled"), false);
        return InteractionResultHolder.consume(stack);
    }
}
