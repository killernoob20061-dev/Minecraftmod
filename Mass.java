package com.worldeater;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class Mass {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, WorldEater.MOD_ID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> VALUE = ATTACHMENTS.register(
            "mass", () -> AttachmentType.builder(() -> 0).serialize(Codec.intRange(0, 1000000)).copyOnDeath().build());

    public static int get(ServerPlayer player) {
        return Mth.clamp(player.getData(VALUE), 0, WorldEaterConfig.MAX_MASS.get());
    }

    public static void add(ServerPlayer player, int amount) {
        long total = (long) get(player) + amount;
        player.setData(VALUE, (int) Math.clamp(total, 0, WorldEaterConfig.MAX_MASS.get()));
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        if (OrbitSession.active(player)) return;
        int before = get(player);
        add(player, -WorldEaterConfig.DECAY.get());
        if (before > 0 || player.isHolding(WorldEater.SINGULARITY_CANNON.get())) show(player);
    }

    /** Vanilla's server-to-client action-bar packet carries the display; attachment state stays server-owned. */
    public static void show(ServerPlayer player) {
        int value = get(player);
        int maximum = WorldEaterConfig.MAX_MASS.get();
        int filled = value == 0 ? 0 : Math.max(1, (int) (10L * value / maximum));
        Component bar = Component.literal("|".repeat(filled)).withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal("|".repeat(10 - filled)).withStyle(ChatFormatting.DARK_GRAY));
        player.displayClientMessage(Component.translatable("message.worldeater.mass", bar, value, maximum), true);
    }

    private Mass() {}
}
