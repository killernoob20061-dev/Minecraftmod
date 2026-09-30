package com.worldeater.client;

import com.worldeater.WorldEater;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@Mod(value = WorldEater.MOD_ID, dist = Dist.CLIENT)
public final class WorldEaterClient {
    public WorldEaterClient(IEventBus bus, ModContainer container) {
        bus.addListener(WorldEaterClient::registerRenderers);
        bus.addListener((net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent event) ->
                event.register(com.worldeater.OrbitSession.ID, new OrbitEffects()));
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(WorldEater.SINGULARITY.get(), SingularityRenderer::new);
    }

}
