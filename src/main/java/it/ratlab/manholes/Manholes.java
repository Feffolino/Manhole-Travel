// SPDX-License-Identifier: MIT
package it.ratlab.manholes;

import com.mojang.logging.LogUtils;
import it.ratlab.manholes.command.ManholeCommands;
import it.ratlab.manholes.compat.Hooks;
import it.ratlab.manholes.gen.SpawnRuleManager;
import it.ratlab.manholes.gen.WorldGenHandler;
import it.ratlab.manholes.net.ManholeNetworking;
import it.ratlab.manholes.travel.PryHandler;
import it.ratlab.manholes.travel.TravelHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;

@Mod(Manholes.MOD_ID)
public final class Manholes {
    public static final String MOD_ID = "manholes";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Manholes(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, ManholesConfig.SPEC);
        container.registerConfig(ModConfig.Type.STARTUP, ManholesStartupConfig.SPEC); // loaded now, before items
        ModRegistry.register(modBus);
        modBus.addListener(ManholeNetworking::register);
        modBus.addListener(Manholes::addToTabs);

        NeoForge.EVENT_BUS.addListener(SpawnRuleManager::onAddReloadListener);
        NeoForge.EVENT_BUS.addListener(ManholeCommands::register);
        WorldGenHandler.registerEvents(NeoForge.EVENT_BUS);
        PryHandler.registerEvents(NeoForge.EVENT_BUS);
        TravelHandler.registerEvents(NeoForge.EVENT_BUS);
        it.ratlab.manholes.travel.NetworkSync.registerEvents(NeoForge.EVENT_BUS);
        it.ratlab.manholes.travel.HomeManholes.registerEvents(NeoForge.EVENT_BUS);

        // Optional integrations: their classes are only touched after these checks.
        if (ModList.get().isLoaded("kubejs")) {
            Hooks.enableKubeJS();
        }
        if (ModList.get().isLoaded("ftbteams")) {
            Hooks.enableFTBTeams();
        }
        if (ModList.get().isLoaded("ftbchunks")) {
            Hooks.enableFTBChunks();
        }
        if (Boolean.getBoolean("manholes.gametests")) {
            it.ratlab.manholes.test.ManholeGameTests.register(modBus);
        }
        if (FMLEnvironment.dist.isClient()) {
            container.registerConfig(ModConfig.Type.CLIENT, it.ratlab.manholes.client.ManholesClientConfig.SPEC);
            it.ratlab.manholes.client.ManholesClient.init(modBus);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
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
