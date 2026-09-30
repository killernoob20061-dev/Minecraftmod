package com.worldeater;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Shared registrations only; renderers are isolated in the client package. */
@Mod(WorldEater.MOD_ID)
public final class WorldEater {
    public static final String MOD_ID = "worldeater";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final DeferredItem<Item> TEST_ITEM = ITEMS.registerSimpleItem("test_item");
    public static final DeferredItem<SuperlaserTriggerItem> SUPERLASER_TRIGGER = ITEMS.registerItem(
            "superlaser_trigger", SuperlaserTriggerItem::new, new Item.Properties().stacksTo(1).fireResistant());
    public static final DeferredItem<DeathStarItem> DEATH_STAR = ITEMS.registerItem(
            "death_star", DeathStarItem::new, new Item.Properties().stacksTo(1).fireResistant());
    public static final DeferredItem<SingularityCannonItem> SINGULARITY_CANNON = ITEMS.registerItem(
            "singularity_cannon", SingularityCannonItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredHolder<EntityType<?>, EntityType<SingularityEntity>> SINGULARITY = ENTITIES.register(
            "singularity", () -> EntityType.Builder.<SingularityEntity>of(SingularityEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).fireImmune().clientTrackingRange(32).updateInterval(1)
                    .build(MOD_ID + ":singularity"));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WORLD_EATER_TAB =
            CREATIVE_TABS.register("world_eater", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.worldeater"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> SINGULARITY_CANNON.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(TEST_ITEM.get());
                        output.accept(SINGULARITY_CANNON.get());
                        output.accept(DEATH_STAR.get());
                        output.accept(SUPERLASER_TRIGGER.get());
                    })
                    .build());

    public WorldEater(IEventBus modEventBus, ModContainer container) {
        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        ENTITIES.register(modEventBus);
        OrbitSession.registerAttachments();
        Mass.ATTACHMENTS.register(modEventBus);
        container.registerConfig(ModConfig.Type.SERVER, WorldEaterConfig.SPEC);
        modEventBus.addListener(SingularityTickets::register);
        NeoForge.EVENT_BUS.addListener(Mass::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(SingularityTickets::onLevelTick);
        NeoForge.EVENT_BUS.addListener(SingularityTickets::onUnload);
        NeoForge.EVENT_BUS.addListener(SingularityTickets::registerCommands);
        NeoForge.EVENT_BUS.addListener(OrbitSession::registerCommands);
        NeoForge.EVENT_BUS.addListener(OrbitSession::onTick);
        NeoForge.EVENT_BUS.addListener(OrbitSession::onLogin);
        NeoForge.EVENT_BUS.addListener(OrbitSession::onRespawn);
        NeoForge.EVENT_BUS.addListener(OrbitSession::onDimensionChange);
        NeoForge.EVENT_BUS.addListener(OrbitSession::onToss);
        NeoForge.EVENT_BUS.addListener(OrbitSession::onDrops);
        NeoForge.EVENT_BUS.addListener(OrbitSession::onExperienceDrop);
        NeoForge.EVENT_BUS.addListener(OrbitPlanets::onTick);
        NeoForge.EVENT_BUS.addListener(OrbitPlanets::onLogout);
        NeoForge.EVENT_BUS.addListener(OrbitPlanets::onStop);
        NeoForge.EVENT_BUS.addListener(LaserControl::onTick);
        NeoForge.EVENT_BUS.addListener(LaserJobs::onTick);
        NeoForge.EVENT_BUS.addListener(LaserJobs::onStop);
        NeoForge.EVENT_BUS.addListener(LaserJobs::registerCommands);
    }
}
