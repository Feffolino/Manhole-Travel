// SPDX-License-Identifier: MIT
package it.ratlab.manholes.client;

import it.ratlab.manholes.Manholes;
import it.ratlab.manholes.net.ManholeNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client entry point, only called on the physical client. */
public final class ManholesClient {
    private ManholesClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterGuiOverlaysEvent e) -> {
            e.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "pry_hud", (gui, g, pt, w, h) -> PryHud.render(g, pt));
            e.registerAboveAll("travel_fade", (gui, g, pt, w, h) -> FadeOverlay.render(g, pt));
        });
        PryHud.init();
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerBlockEntityRenderer(it.ratlab.manholes.ModRegistry.MANHOLE_BE.get(),
                        it.ratlab.manholes.client.cover.ManholeCoverRenderer::new));
        it.ratlab.manholes.client.cover.CoverEvents.register(modBus);
        it.ratlab.manholes.block.ManholeBlockEntity.clientAnimStarted = it.ratlab.manholes.client.cover.CoverLooks::onAnimStarted;
        ManholeNetworking.clientPryState = PryHud::onState;
        TravelUi.init();
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) {
                FadeOverlay.tick();
            }
        });
        modBus.addListener((RegisterClientReloadListenersEvent e) ->
                e.registerReloadListener((ResourceManagerReloadListener) rm -> MapIcons.clear()));
        ManholeNetworking.clientOpenScreen = p -> Minecraft.getInstance().setScreen(new TravelScreen(p));
        ManholeNetworking.clientFade = p -> {
            if (p.ticks() <= 0) {
                TravelCamera.stop();
            }
            FadeOverlay.start(p.ticks());
        };
        ManholeNetworking.clientTravelAnim = TravelCamera::onPayload;
        TravelCamera.init();
        ClientNetwork.init();
        ManholeNetworking.clientNetworkSync = ClientNetwork::onSync;
        if (ModList.get().isLoaded("ftbchunks")) {
            modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() -> {
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
