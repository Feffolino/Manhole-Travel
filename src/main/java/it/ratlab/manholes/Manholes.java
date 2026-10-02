// SPDX-License-Identifier: MIT
package it.ratlab.manholes;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
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
