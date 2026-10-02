// SPDX-License-Identifier: MIT
package it.ratlab.manholes.client;

import it.ratlab.manholes.Manholes;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Per-look map icons (1.5.0): {@code manholes:textures/gui/map_icon_<look>.png} (looks {@code city}, {@code grate},
 * {@code hatch}, {@code cave}, {@code home_manhole}; another namespace's look {@code ns:x} uses
 * {@code ns:textures/gui/map_icon_x.png}). A missing file falls back to {@code map_icon.png}; each look is checked once
 * through the resource manager (the cache is cleared on resource reload). Used by the travel screen and FTB Chunks.
 */
public final class MapIcons {
    public static final ResourceLocation FALLBACK = Manholes.id("textures/gui/map_icon.png");
    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();

    private MapIcons() {}

    public static synchronized ResourceLocation texture(String look) {
        return CACHE.computeIfAbsent(look == null ? "" : look, MapIcons::find);
    }

    private static ResourceLocation find(String look) {
        if (look.isEmpty()) {
            return FALLBACK;
        }
        ResourceLocation id = ResourceLocation.tryParse(look.contains(":") ? look : "manholes:" + look);
        if (id == null) {
            return FALLBACK;
        }
        ResourceLocation tex = ResourceLocation.tryParse(id.getNamespace() + ":textures/gui/map_icon_" + id.getPath() + ".png");
        try {
            if (tex != null && Minecraft.getInstance().getResourceManager().getResource(tex).isPresent()) {
                return tex;
            }
        } catch (RuntimeException ignored) {
            // no resource manager yet
        }
        return FALLBACK;
    }

    /** Resource reload: look again (a resource pack may add or remove icons). */
    public static synchronized void clear() {
        CACHE.clear();
    }
}
