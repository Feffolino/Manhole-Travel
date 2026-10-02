// SPDX-License-Identifier: MIT
package it.ratlab.manholes;

import com.mojang.logging.LogUtils;
import it.ratlab.manholes.command.ManholeCommands;
import it.ratlab.manholes.compat.Hooks;
import it.ratlab.manholes.gen.WorldGenHandler;
import it.ratlab.manholes.net.ManholeNetworking;
import it.ratlab.manholes.travel.HomeManholes;
import it.ratlab.manholes.travel.NetworkSync;
import it.ratlab.manholes.travel.PryHandler;
import it.ratlab.manholes.travel.TravelHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(Manholes.MOD_ID)
public final class Manholes {
    public static final String MOD_ID = "manholes";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Manholes() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ManholesConfig.SPEC);
        ManholesStartupConfig.load();
        ModRegistry.register(modBus);
        modBus.addListener(Manholes::addToTabs);

        ManholeNetworking.init();

        MinecraftForge.EVENT_BUS.addListener(ManholeCommands::register);
        HomeManholes.registerEvents(MinecraftForge.EVENT_BUS);
        WorldGenHandler.registerEvents(MinecraftForge.EVENT_BUS);
        TravelHandler.registerEvents(MinecraftForge.EVENT_BUS);
        PryHandler.registerEvents(MinecraftForge.EVENT_BUS);
        NetworkSync.registerEvents(MinecraftForge.EVENT_BUS);

        if (ModList.get().isLoaded("kubejs")) {
            Hooks.enableKubeJS();
        }
        if (ModList.get().isLoaded("ftbteams")) {
            Hooks.enableFTBTeams();
        }
        if (ModList.get().isLoaded("ftbchunks")) {
            Hooks.enableFTBChunks();
        }

        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
            ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, it.ratlab.manholes.client.ManholesClientConfig.SPEC);
            it.ratlab.manholes.client.ManholesClient.init(modBus);
        }
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    private static void addToTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModRegistry.HOME_MANHOLE_ITEM.get());
            event.accept(ModRegistry.HATCH_ITEM.get());
            event.accept(ModRegistry.GRATE_ITEM.get());
            event.accept(ModRegistry.CAVE_HOLE_ITEM.get());
            event.accept(ModRegistry.CITY_MANHOLE_ITEM.get());
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModRegistry.CROWBAR.get());
        }
    }
}
