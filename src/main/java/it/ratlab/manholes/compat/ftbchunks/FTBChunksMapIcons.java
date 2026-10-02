// SPDX-License-Identifier: MIT
package it.ratlab.manholes.compat.ftbchunks;

import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.client.event.MapIconEvent;
import dev.ftb.mods.ftbchunks.api.client.icon.MapIcon;
import dev.ftb.mods.ftbchunks.api.client.icon.MapType;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import it.ratlab.manholes.client.MapIcons;
import it.ratlab.manholes.client.ClientNetwork;
import it.ratlab.manholes.net.OpenTravelScreenPayload.Entry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * FTB Chunks (client, optional): every node the player can travel to gets an icon on the large map and on the minimap
 * of its dimension, picked by the node's look ({@link MapIcons}); hovering shows its name (the team alias if one is
 * set). Only class-loaded when FTB Chunks is installed. Uses the public API only: {@link MapIconEvent} and
 * {@code FTBChunksClientAPI.requestMinimapIconRefresh()}.
 * <p>
 * Size (1.5.0, checked against the decompiled 2101.1.22): the large map ({@code RegionMapPanel.alignWidgets}) sizes
 * each icon widget {@code max(isZoomDependant ? 0 : 6, regionTileSize / 128 * getIconScale)} px, where
 * {@code regionTileSize = zoom * 2} (zoom 256 = 1 block per px) and hands that size to {@code draw} as {@code w, h}.
 * With {@code getIconScale = 4} the widget is 16 px at 1:1, 8 at 1:2, 6 (FTB's floor) further out, 32 / 64 zoomed in;
 * {@code draw} uses it clamped to 6..24 px, centred. The minimap ({@code FTBChunksClient}) draws every icon into a 1x1
 * slot scaled to {@code minimapSize / 16 * getIconScale}; there {@code draw} undoes the pose scale and draws a fixed
 * 12 px (8 px when clamped to the rim). {@code isIconOnEdge = false}: centred on the node, also on the minimap rim.
 */
public final class FTBChunksMapIcons {
    private static final float LARGE_MAP_ICON_SCALE = 4f;
    private static final float LARGE_MAP_MIN_PX = 6f;
    private static final float LARGE_MAP_MAX_PX = 24f;
    private static final float MINIMAP_PX = 12f;
    private static final float MINIMAP_EDGE_PX = 8f;

    private FTBChunksMapIcons() {}

    public static void init() {
        MapIconEvent.LARGE_MAP.register(FTBChunksMapIcons::addIcons);
        MapIconEvent.MINIMAP.register(FTBChunksMapIcons::addIcons);
        ClientNetwork.onChange = FTBChunksMapIcons::refresh;
    }

    private static void addIcons(MapIconEvent event) {
        for (Entry e : ClientNetwork.nodes()) {
            if (e.dimension().equals(event.getDimension().location())) {
                event.add(new ManholeIcon(e));
            }
        }
    }

    private static void refresh() {
        try {
            FTBChunksAPI.clientApi().requestMinimapIconRefresh();
        } catch (RuntimeException ignored) {
            // client API not initialised yet (no world): the next map open collects the icons anyway
        }
    }

    /** On-screen size of the large-map icon for the widget size FTB Chunks gave it. */
    static float largeMapPx(int w, int h) {
        return Math.max(LARGE_MAP_MIN_PX, Math.min(LARGE_MAP_MAX_PX, Math.min(w, h)));
    }

    private static final class ManholeIcon extends MapIcon.SimpleMapIcon {
        private final Component name;

        ManholeIcon(Entry e) {
            super(Vec3.atCenterOf(e.pos()), Icon.getIcon(MapIcons.texture(e.look())));
            this.name = e.name();
        }

        @Override
        public void addTooltip(TooltipList list) {
            list.add(name);
        }

        @Override
        public boolean isIconOnEdge(MapType mapType, boolean outsideVisibleArea) {
            return false;
        }

        @Override
        public boolean isZoomDependant(MapType mapType) {
            return false; // keep FTB's 6 px floor
        }

        @Override
        public double getIconScale(MapType mapType) {
            return mapType == MapType.LARGE_MAP ? LARGE_MAP_ICON_SCALE : 1.0;
        }

        @Override
        public void draw(MapType mapType, GuiGraphics g, int x, int y, int w, int h, boolean outsideVisibleArea, int iconAlpha) {
            float size;
            if (mapType == MapType.MINIMAP) {
                // The minimap draws into a 1x1 slot scaled up: undo that scale for a fixed on-screen size.
                org.joml.Matrix4f m = g.pose().last().pose();
                float poseScale = Math.max(1e-4f, (float) Math.sqrt(m.m00() * m.m00() + m.m01() * m.m01()));
                size = (outsideVisibleArea ? MINIMAP_EDGE_PX : MINIMAP_PX) / poseScale;
            } else {
                size = largeMapPx(w, h); // FTB already scaled w / h with the zoom
            }
            g.pose().pushPose();
            g.pose().translate(x + w / 2f, y + h / 2f, 0);
            // FTB Library's image icon maps the whole texture (UV 0..1) onto the rectangle, so any square texture size
            // fills it; the 16-unit rectangle is only the drawing frame, scaled to `size` px (nearest filtering).
            g.pose().scale(size / 16f, size / 16f, 1f);
            icon.draw(g, -8, -8, 16, 16);
            g.pose().popPose();
        }
    }
}
