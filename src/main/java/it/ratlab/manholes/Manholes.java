// SPDX-License-Identifier: MIT
package it.ratlab.manholes;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Manholes.MOD_ID)
public final class Manholes {
    public static final String MOD_ID = "manholes";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Manholes() {
        LOGGER.info("Manhole Travel initialized on Forge 1.20.1");
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
