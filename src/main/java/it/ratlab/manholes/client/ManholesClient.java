// SPDX-License-Identifier: MIT
package it.ratlab.manholes.client;

import it.ratlab.manholes.Manholes;
import it.ratlab.manholes.net.ManholeNetworking;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/** Client entry point, only called on the physical client. */
public final class ManholesClient {
    private ManholesClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterGuiLayersEvent e) -> {
            e.registerAbove(VanillaGuiLayers.CROSSHAIR, Manholes.id("pry_hud"), PryHud::render);
            e.registerAboveAll(Manholes.id("travel_fade"), FadeOverlay::render);
        });
        PryHud.init();
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers e) ->
                e.registerBlockEntityRenderer(it.ratlab.manholes.ModRegistry.MANHOLE_BE.get(),
                        it.ratlab.manholes.client.cover.ManholeCoverRenderer::new));
        it.ratlab.manholes.client.cover.CoverEvents.register(modBus);
        it.ratlab.manholes.block.ManholeBlockEntity.clientAnimStarted = it.ratlab.manholes.client.cover.CoverLooks::onAnimStarted;
        ManholeNetworking.clientPryState = PryHud::onState;
        TravelUi.init();
        modBus.addListener((net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent e) ->
                e.registerItem(new CrowbarPose(), it.ratlab.manholes.ModRegistry.CROWBAR.get()));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> FadeOverlay.tick());
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent e) ->
                e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) rm -> MapIcons.clear()));
        ManholeNetworking.clientOpenScreen = p -> Minecraft.getInstance().setScreen(new TravelScreen(p));
        ManholeNetworking.clientFade = p -> {
            if (p.ticks() <= 0) {
                TravelCamera.stop();
            }
            FadeOverlay.start(p.ticks());
        };
        TravelCamera.init();
        ClientNetwork.init();
        ManholeNetworking.clientNetworkSync = ClientNetwork::onSync;
        if (net.neoforged.fml.ModList.get().isLoaded("ftbchunks")) {
            modBus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent e) -> e.enqueueWork(() -> {
                try {
                    it.ratlab.manholes.compat.ftbchunks.FTBChunksMapIcons.init();
                    TravelScreen.terrain = new it.ratlab.manholes.compat.ftbchunks.FTBChunksTerrain(new LoadedChunksTerrain());
                    Manholes.LOGGER.info("FTB Chunks found: opened manholes are shown on its map and minimap, the travel map uses its map textures");
                } catch (Throwable t) {
                    Manholes.LOGGER.error("FTB Chunks map icons failed to load, continuing without them", t);
                }
            }));
        }
        ManholeNetworking.clientTravelAnim = TravelCamera::onPayload;
    }
}
