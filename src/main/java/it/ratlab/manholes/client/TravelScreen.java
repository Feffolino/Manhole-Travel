// SPDX-License-Identifier: MIT
package it.ratlab.manholes.client;

import it.ratlab.manholes.Manholes;
import it.ratlab.manholes.net.OpenTravelScreenPayload;
import it.ratlab.manholes.net.OpenTravelScreenPayload.Entry;
import it.ratlab.manholes.net.RenameNodePayload;
import it.ratlab.manholes.net.TravelRequestPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Travel screen. The map is the main element: real terrain underneath ({@link #terrain}: FTB Chunks' map when installed,
 * else the loaded chunks), the nodes as {@code map_icon} sprites, the player, a north arrow and a scale bar; wheel = zoom,
 * drag = pan. A slim sidebar lists the nodes. Clicking an icon (or a row, which also centres the map on it) opens a
 * popup next to the icon with the details, a rename pencil and the Travel button. Empty map space or Esc closes the
 * popup, a second Esc closes the screen. Everything shown is what the server sent; the server re-validates requests.
 */
public class TravelScreen extends Screen {
    /** Terrain source; ManholesClient swaps in the FTB Chunks one when that mod is installed. */
    public static MapTerrain terrain = new LoadedChunksTerrain();

    private static final int MARGIN = 8;
    private static final int TOP = 22;
    private static final int ROW_H = 13;
    /** Map icons are 16x16 per look ({@link MapIcons}); drawn 1:1, nearest. */
    private static final int ICON_PX = 16;
    private static final int DOT_HIT = 10;
    private static final int POPUP_W = 158;
    private static final int PANEL = 0xE8101418;
    private static final int BORDER = 0xFF6A747E;
    // Buttons (Travel, rename pencil): plain fills in the dark-panel style, no vanilla sprite.
    private static final int BTN_FILL = 0xFF2B2D31;
    private static final int BTN_BORDER = 0xFF6B6F78;
    private static final int BTN_TEXT = 0xFFE8E8EA;
    private static final int BTN_HOVER_FILL = 0xFF3A3D44;
    private static final int BTN_HOVER_BORDER = 0xFFD9A520;
    private static final int BTN_PRESSED_FILL = 0xFF222327;
    private static final int BTN_DISABLED_FILL = 0xFF1E1F22;
    private static final int BTN_DISABLED_BORDER = 0xFF34363B;
    private static final int BTN_DISABLED_TEXT = 0xFF55575C;

    private final OpenTravelScreenPayload data;
    private final List<Entry> entries;
    private final ResourceKey<Level> dimension;
    private double scroll;
    @Nullable
    private UUID hovered;
    @Nullable
    private UUID selected;
    /** Node whose popup is open (null = none). */
    @Nullable
    private UUID popup;
    @Nullable
    private EditBox rename;
    private int netVersion;

    // map view: world centre and pixels per block
    private double viewX;
    private double viewZ;
    private double scale = 1;
    private boolean fitted;
    private boolean dragging;
    private double pressX;
    private double pressY;
    private boolean pressOnEmptyMap;
    private long lastRowClick;
    @Nullable
    private UUID lastRowClicked;

    // popup geometry of the last frame (for clicks)
    private int popX;
    private int popY;
    private int popH;
    private int travelX0;
    private int travelY0;
    private int travelX1;
    private int travelY1;
    private int pencilX;
    private int pencilY;
    /** Share toggle of an own home manhole (y0 < 0 = not shown this frame). */
    private int shareX0;
    private int shareY0 = -1;
    private int shareX1;
    private int shareY1;
    /** Left button went down on the Travel button (it fires on release over it). */
    private boolean travelPressed;

    public TravelScreen(OpenTravelScreenPayload data) {
        super(Component.translatable("manholes.screen.title"));
        this.data = data;
        this.entries = new ArrayList<>(data.entries());
        this.dimension = ResourceKey.create(Registries.DIMENSION, entries.get(0).dimension());
        this.netVersion = ClientNetwork.version();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private Entry current() {
        return entries.get(0);
    }

    private List<Entry> others() {
        return entries.subList(1, entries.size());
    }

    // ---------------------------------------------------------------- layout

    private int sideW() {
        return Mth.clamp((int) ((width - 3 * MARGIN) * 0.26), 110, 180);
    }

    private int mapLeft() {
        return MARGIN;
    }

    private int mapRight() {
        return width - 2 * MARGIN - sideW();
    }

    private int mapTop() {
        return TOP;
    }

    private int mapBottom() {
        return height - MARGIN;
    }

    private int listLeft() {
        return mapRight() + MARGIN;
    }

    private int listRight() {
        return width - MARGIN;
    }

    private int listTop() {
        return TOP + 2 * ROW_H + 8;
    }

    private int listBottom() {
        return height - MARGIN;
    }

    private int maxScroll() {
        return Math.max(0, others().size() * ROW_H - (listBottom() - listTop()));
    }

    private boolean reachable(Entry e) {
        return data.crossDimension() || e.dimension().equals(current().dimension());
    }

    private boolean onMap(Entry e) {
        return e.dimension().equals(current().dimension());
    }

    private boolean isCurrent(Entry e) {
        return e.id().equals(current().id());
    }

    @Nullable
    private Entry byId(@Nullable UUID id) {
        if (id == null) {
            return null;
        }
        for (Entry e : entries) {
            if (e.id().equals(id)) {
                return e;
            }
        }
        return null;
    }

    @Override
    protected void init() {
        if (!fitted) {
            fitView();
            fitted = true;
            terrain.open(dimension);
        }
        if (rename != null) {
            cancelRename(); // a resize drops an edit in progress
        }
    }

    @Override
    public void removed() {
        terrain.close();
        super.removed();
    }

    /** Frames the current node, the player and every destination in this dimension. */
    private void fitView() {
        Minecraft mc = Minecraft.getInstance();
        double minX = current().pos().getX();
        double maxX = minX;
        double minZ = current().pos().getZ();
        double maxZ = minZ;
        if (mc.player != null) {
            minX = Math.min(minX, mc.player.getX());
            maxX = Math.max(maxX, mc.player.getX());
            minZ = Math.min(minZ, mc.player.getZ());
            maxZ = Math.max(maxZ, mc.player.getZ());
        }
        for (Entry e : others()) {
            if (onMap(e)) {
                minX = Math.min(minX, e.pos().getX());
                maxX = Math.max(maxX, e.pos().getX());
                minZ = Math.min(minZ, e.pos().getZ());
                maxZ = Math.max(maxZ, e.pos().getZ());
            }
        }
        viewX = (minX + maxX) / 2 + 0.5;
        viewZ = (minZ + maxZ) / 2 + 0.5;
        double spanX = Math.max(96, maxX - minX) * 1.3;
        double spanZ = Math.max(96, maxZ - minZ) * 1.3;
        scale = Mth.clamp(Math.min((mapRight() - mapLeft()) / spanX, (mapBottom() - mapTop()) / spanZ), 0.002, 8);
    }

    private double sx(double worldX) {
        return (mapLeft() + mapRight()) / 2.0 + (worldX - viewX) * scale;
    }

    private double sy(double worldZ) {
        return (mapTop() + mapBottom()) / 2.0 + (worldZ - viewZ) * scale; // north (-Z) is up
    }

    private boolean inMap(double mx, double my) {
        return mx >= mapLeft() && mx < mapRight() && my >= mapTop() && my < mapBottom();
    }

    private boolean inList(double mx, double my) {
        return mx >= listLeft() && mx < listRight() && my >= listTop() && my < listBottom();
    }

    private boolean inCurrentRow(double mx, double my) {
        return mx >= listLeft() && mx < listRight() && my >= TOP && my < TOP + 2 * ROW_H + 4;
    }

    private boolean inPopup(double mx, double my) {
        return popup != null && mx >= popX && mx < popX + POPUP_W && my >= popY && my < popY + popH;
    }

    @Nullable
    private Entry dotAt(double mx, double my) {
        if (!inMap(mx, my)) {
            return null;
        }
        Entry best = null;
        double bestD = DOT_HIT * DOT_HIT;
        for (Entry e : entries) {
            if (!onMap(e)) {
                continue;
            }
            double dx = sx(e.pos().getX() + 0.5) - mx;
            double dy = sy(e.pos().getZ() + 0.5) - my;
            double d = dx * dx + dy * dy;
            if (d <= bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    @Nullable
    private Entry rowAt(double mx, double my) {
        if (inCurrentRow(mx, my)) {
            return current();
        }
        if (!inList(mx, my)) {
            return null;
        }
        int idx = (int) ((my - listTop() + scroll) / ROW_H);
        return idx >= 0 && idx < others().size() ? others().get(idx) : null;
    }

    // ---------------------------------------------------------------- sync (renames from the server)

    @Override
    public void tick() {
        super.tick();
        int v = ClientNetwork.version();
        if (v != netVersion) {
            netVersion = v;
            Map<UUID, Entry> fresh = new HashMap<>();
            for (Entry e : ClientNetwork.nodes()) {
                fresh.put(e.id(), e);
            }
            for (int i = 0; i < entries.size(); i++) {
                Entry f = fresh.get(entries.get(i).id());
                if (f != null) {
                    entries.set(i, f); // name, alias flag and (homes) owner / sharing
                }
            }
        }
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No blur: the map is the content. A plain dark veil over the world.
        g.fillGradient(0, 0, width, height, 0xD0080A0C, 0xE0080A0C);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Entry hov = inPopup(mouseX, mouseY) ? null : dotAt(mouseX, mouseY);
        if (hov == null && !inPopup(mouseX, mouseY)) {
            hov = rowAt(mouseX, mouseY);
        }
        hovered = hov == null ? null : hov.id();
        g.drawString(font, title, MARGIN, 8, 0xFFE8E0D0, true);
        renderMap(g, partialTick);
        renderList(g);
        renderPopup(g, mouseX, mouseY, partialTick);
    }

    private void renderMap(GuiGraphics g, float pt) {
        int l = mapLeft();
        int t = mapTop();
        int r = mapRight();
        int b = mapBottom();
        g.fill(l, t, r, b, 0xFF0C0F12);
        double worldLeft = viewX - (r - l) / 2.0 / scale;
        double worldTop = viewZ - (b - t) / 2.0 / scale;
        g.enableScissor(l, t, r, b);
        g.flush();
        terrain.draw(g, dimension, worldLeft, worldTop, scale, l, t, r, b);

        // Faint chunk grid when zoomed in (the terrain is the main reference now).
        if (scale >= 1.5) {
            long step = 16;
            for (long gx = (long) Math.floor(worldLeft / step) * step; sx(gx) < r; gx += step) {
                int x = (int) Math.round(sx(gx));
                g.fill(x, t, x + 1, b, 0x14FFFFFF);
            }
            for (long gz = (long) Math.floor(worldTop / step) * step; sy(gz) < b; gz += step) {
                int y = (int) Math.round(sy(gz));
                g.fill(l, y, r, y + 1, 0x14FFFFFF);
            }
        }

        for (Entry e : others()) {
            if (onMap(e)) {
                drawNode(g, e, false);
            }
        }
        drawNode(g, current(), true);

        // The player, with his heading.
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            double px = sx(Mth.lerp(pt, mc.player.xo, mc.player.getX()));
            double py = sy(Mth.lerp(pt, mc.player.zo, mc.player.getZ()));
            float yaw = mc.player.getViewYRot(pt) * Mth.DEG_TO_RAD;
            double fx = -Mth.sin(yaw);
            double fz = Mth.cos(yaw);
            for (int i = 1; i <= 8; i++) {
                int x = (int) Math.round(px + fx * i);
                int y = (int) Math.round(py + fz * i);
                g.fill(x, y, x + 1, y + 1, 0xFFFFE070);
            }
            int ix = (int) Math.round(px);
            int iy = (int) Math.round(py);
            g.fill(ix - 2, iy - 2, ix + 3, iy + 3, 0xFF000000);
            g.fill(ix - 1, iy - 1, ix + 2, iy + 2, 0xFFFFE070);
        }
        g.disableScissor();

        // North arrow (top right) and scale bar (bottom left), on small dark plates.
        int nx = r - 14;
        int ny = t + 6;
        g.fill(nx - 8, ny - 3, nx + 9, ny + 24, 0xA0000000);
        for (int i = 0; i < 6; i++) {
            g.fill(nx - i / 2, ny + i, nx + 1 + i / 2, ny + i + 1, 0xFFE0E0E0);
        }
        g.fill(nx, ny + 6, nx + 1, ny + 12, 0xFFE0E0E0);
        g.drawCenteredString(font, "N", nx + 1, ny + 14, 0xFFE0E0E0);
        int bar = niceLength(70 / scale);
        int barPx = (int) Math.round(bar * scale);
        int bx = l + 8;
        int by = b - 10;
        g.fill(bx - 4, by - 15, bx + Math.max(barPx, font.width(bar + " m")) + 4, by + 5, 0xA0000000);
        g.fill(bx, by, bx + barPx, by + 2, 0xFFE0E0E0);
        g.fill(bx, by - 3, bx + 1, by + 2, 0xFFE0E0E0);
        g.fill(bx + barPx - 1, by - 3, bx + barPx, by + 2, 0xFFE0E0E0);
        g.drawString(font, Component.translatable("manholes.screen.scale", bar), bx, by - 12, 0xFFE0E0E0);
        g.renderOutline(l, t, r - l, b - t, BORDER);
        if (!others().isEmpty() && others().stream().noneMatch(this::onMap)) {
            g.drawCenteredString(font, Component.translatable("manholes.screen.map_other_dimensions"), (l + r) / 2, t + 6, 0xFFA0A0A0);
        }
    }

    /** 1, 2 or 5 x 10^n blocks, close to {@code blocks}. */
    private static int niceLength(double blocks) {
        double p = Math.pow(10, Math.floor(Math.log10(Math.max(1, blocks))));
        double n = blocks / p;
        double nice = n < 1.5 ? 1 : n < 3.5 ? 2 : n < 7.5 ? 5 : 10;
        return (int) Math.max(1, nice * p);
    }

    private static void ring(GuiGraphics g, int cx, int cy, float radius, int thickness, int color) {
        for (int k = 0; k < thickness; k++) {
            float rr = radius + k;
            int steps = Math.max(24, (int) (rr * 7));
            for (int i = 0; i < steps; i++) {
                double a = i * Math.PI * 2 / steps;
                int x = cx + (int) Math.round(Math.cos(a) * rr);
                int y = cy + (int) Math.round(Math.sin(a) * rr);
                g.fill(x, y, x + 1, y + 1, color);
            }
        }
    }

    private void drawNode(GuiGraphics g, Entry e, boolean isCurrent) {
        int x = (int) Math.round(sx(e.pos().getX() + 0.5));
        int y = (int) Math.round(sy(e.pos().getZ() + 0.5));
        boolean hot = e.id().equals(hovered);
        boolean sel = e.id().equals(selected) || e.id().equals(popup);
        int half = ICON_PX / 2;
        if (sel) {
            ring(g, x, y, half + 2, 2, 0xFFFFD040);
        } else if (isCurrent) {
            ring(g, x, y, half + 1, 1, 0xFF60D060);
        } else if (hot) {
            ring(g, x, y, half + 1, 1, 0xFFFFFFFF);
        }
        blitFull(g, MapIcons.texture(e.look()), x - half, y - half, ICON_PX, ICON_PX);
        if (isCurrent || hot || sel || scale >= 0.35) {
            Component name = e.name();
            int w = font.width(name);
            int lx = x + half + 4;
            g.fill(lx - 2, y - 6, lx + w + 2, y + 5, 0xB0000000);
            g.drawString(font, name, lx, y - 4, isCurrent ? 0xFFA0FFA0 : hot || sel ? 0xFFFFFFFF : 0xFFD0D0D0, false);
        }
    }

    /**
     * Draws a whole texture into a rectangle, whatever its pixel size (16x16, 32x32, ...): UV 0..1 via a 1x1 "texture
     * size", so a replaced icon of another resolution still fills the target exactly.
     */
    static void blitFull(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h) {
        g.blit(tex, x, y, w, h, 0f, 0f, 1, 1, 1, 1);
    }

    /** A flat button: normal / hover / pressed / disabled. */
    private void button(GuiGraphics g, int x0, int y0, int x1, int y1, boolean enabled, boolean hot, boolean pressed) {
        int fill = !enabled ? BTN_DISABLED_FILL : pressed ? BTN_PRESSED_FILL : hot ? BTN_HOVER_FILL : BTN_FILL;
        int border = !enabled ? BTN_DISABLED_BORDER : hot || pressed ? BTN_HOVER_BORDER : BTN_BORDER;
        g.fill(x0, y0, x1, y1, fill);
        g.renderOutline(x0, y0, x1 - x0, y1 - y0, border);
    }

    private static int buttonText(boolean enabled) {
        return enabled ? BTN_TEXT : BTN_DISABLED_TEXT;
    }

    private void renderList(GuiGraphics g) {
        int x = listLeft();
        int w = listRight() - listLeft();
        g.fill(x, TOP, x + w, listBottom(), PANEL);
        g.renderOutline(x, TOP, w, listBottom() - TOP, BORDER);
        // Current node on top.
        boolean curSel = current().id().equals(popup);
        g.fill(x + 1, TOP + 1, x + w - 1, TOP + 2 * ROW_H + 3, curSel ? 0xC0405828 : 0xC0203820);
        g.drawString(font, Component.translatable("manholes.screen.you_are_here"), x + 4, TOP + 3, 0xFF90E090, false);
        g.drawString(font, font.substrByWidth(current().name(), w - 8).getString(), x + 4, TOP + 3 + ROW_H, 0xFFFFFFFF, false);
        g.fill(x + 1, listTop() - 3, x + w - 1, listTop() - 2, 0x40FFFFFF);

        if (others().isEmpty()) {
            int ly = listTop() + 4;
            for (FormattedCharSequence line : font.split(Component.translatable("manholes.screen.empty"), w - 8)) {
                g.drawString(font, line, x + 4, ly, 0xFFA0A0A0, false);
                ly += 10;
            }
            return;
        }
        g.enableScissor(x + 1, listTop(), x + w - 1, listBottom() - 1);
        int y = listTop() - (int) scroll;
        for (Entry e : others()) {
            if (y + ROW_H >= listTop() && y <= listBottom()) {
                boolean ok = reachable(e);
                boolean hot = e.id().equals(hovered);
                boolean sel = e.id().equals(selected) || e.id().equals(popup);
                if (sel || hot) {
                    g.fill(x + 1, y, x + w - 1, y + ROW_H - 1, sel ? 0xC0605020 : 0x80404860);
                }
                String dist = shortDetail(e);
                int dw = font.width(dist);
                g.drawString(font, font.substrByWidth(e.name(), w - 12 - dw).getString(), x + 4, y + 2,
                        ok ? 0xFFFFFFFF : 0xFF707070, false);
                g.drawString(font, dist, x + w - 4 - dw, y + 2, ok ? 0xFFA8A8A8 : 0xFF606060, false);
            }
            y += ROW_H;
        }
        g.disableScissor();
    }

    private double distanceTo(Entry e) {
        Minecraft mc = Minecraft.getInstance();
        double px = mc.player != null ? mc.player.getX() : current().pos().getX();
        double pz = mc.player != null ? mc.player.getZ() : current().pos().getZ();
        double dx = e.pos().getX() + 0.5 - px;
        double dz = e.pos().getZ() + 0.5 - pz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private String dirOf(Entry e) {
        Minecraft mc = Minecraft.getInstance();
        double px = mc.player != null ? mc.player.getX() : current().pos().getX();
        double pz = mc.player != null ? mc.player.getZ() : current().pos().getZ();
        return direction(e.pos().getX() + 0.5 - px, e.pos().getZ() + 0.5 - pz);
    }

    /** Compact right-hand text of a row: "812 m NE", or the dimension's short name. */
    private String shortDetail(Entry e) {
        if (!onMap(e)) {
            return e.dimension().getPath();
        }
        return Math.round(distanceTo(e)) + " m " + dirOf(e).toUpperCase(java.util.Locale.ROOT);
    }

    private static Component dimensionName(ResourceLocation id) {
        return Component.translatableWithFallback("manholes.dimension." + id.getNamespace() + "." + id.getPath(), id.toString());
    }

    /** 8-way compass, north = -Z. */
    static String direction(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north, 90 = east
        int idx = Math.floorMod((int) Math.round(angle / 45.0), 8);
        return new String[] {"n", "ne", "e", "se", "s", "sw", "w", "nw"}[idx];
    }

    // ---------------------------------------------------------------- popup

    private void renderPopup(GuiGraphics g, int mouseX, int mouseY, float pt) {
        Entry e = byId(popup);
        if (e == null) {
            popup = null;
            return;
        }
        boolean cur = isCurrent(e);
        List<Component> lines = new ArrayList<>();
        if (cur) {
            lines.add(Component.translatable("manholes.screen.current_location").withStyle(ChatFormatting.GREEN));
        }
        if (onMap(e)) {
            if (!cur) {
                lines.add(Component.translatable("manholes.screen.distance", Math.round(distanceTo(e)),
                        Component.translatable("manholes.dir." + dirOf(e))).withStyle(ChatFormatting.GRAY));
            }
        } else {
            lines.add(Component.translatable(reachable(e) ? "manholes.screen.other_dimension" : "manholes.screen.unreachable_dimension",
                    dimensionName(e.dimension())).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("manholes.screen.coords", e.pos().getX(), e.pos().getY(), e.pos().getZ())
                .withStyle(ChatFormatting.GRAY));
        lines.add(dimensionName(e.dimension()).copy().withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatableWithFallback("manholes.look." + e.look().replace(':', '.'), e.look())
                .withStyle(ChatFormatting.DARK_GRAY));
        int rust = Mth.clamp(e.rust(), 0, 3);
        ChatFormatting rustColor = rust == 0 ? ChatFormatting.GRAY : rust == 1 ? ChatFormatting.GOLD : ChatFormatting.RED;
        Component condition = ConditionText.of(e.look(), rust);
        if (condition != null) {
            lines.add(condition.copy().withStyle(rustColor));
        }
        if (e.home()) {
            lines.add(Component.translatable("manholes.screen.owner", e.ownerName().isEmpty()
                    ? Component.translatable("manholes.home.owner_unknown") : Component.literal(e.ownerName()))
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable(e.shared() ? "manholes.screen.badge.shared" : "manholes.screen.badge.private")
                    .withStyle(e.shared() ? ChatFormatting.AQUA : ChatFormatting.YELLOW));
        }
        boolean shareBtn = e.home() && e.mine();

        popH = 8 + 12 + lines.size() * 10 + 6 + (shareBtn ? 16 + 4 : 0) + 16 + 6;
        // Anchor: next to the icon when it's on the map, else left of the sidebar row.
        int ax;
        int ay;
        boolean fromRow = !onMap(e);
        if (!fromRow) {
            ax = (int) Math.round(sx(e.pos().getX() + 0.5));
            ay = (int) Math.round(sy(e.pos().getZ() + 0.5));
            popX = ax + ICON_PX / 2 + 8;
            if (popX + POPUP_W > mapRight() - 2) {
                popX = ax - ICON_PX / 2 - 8 - POPUP_W;
            }
        } else {
            ax = listLeft();
            ay = rowY(e) + ROW_H / 2;
            popX = ax - 6 - POPUP_W;
        }
        popY = ay - popH / 2;
        popX = Mth.clamp(popX, 2, width - POPUP_W - 2);
        popY = Mth.clamp(popY, 2, Math.max(2, height - popH - 2));

        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.fill(popX + 2, popY + 2, popX + POPUP_W + 2, popY + popH + 2, 0x60000000); // shadow
        g.fill(popX, popY, popX + POPUP_W, popY + popH, PANEL);
        g.renderOutline(popX, popY, POPUP_W, popH, BORDER);
        // Title row: name (or the edit box) and the pencil.
        pencilX = popX + POPUP_W - 14;
        pencilY = popY + 5;
        if (rename == null) {
            Component name = e.name().copy().withStyle(ChatFormatting.BOLD);
            blitFull(g, MapIcons.texture(e.look()), popX + 5, popY + 4, 16, 16);
            g.drawString(font, font.substrByWidth(name, POPUP_W - 44).getString(), popX + 24, popY + 8, 0xFFFFFFFF, false);
            boolean penHot = mouseX >= pencilX && mouseX < pencilX + 10 && mouseY >= pencilY && mouseY < pencilY + 11;
            button(g, pencilX - 1, pencilY - 1, pencilX + 10, pencilY + 11, true, penHot, false);
            g.drawString(font, "✎", pencilX + 1, pencilY + 1, penHot ? 0xFFD9A520 : BTN_TEXT, false);
        }
        int ly = popY + 8 + 12;
        for (Component line : lines) {
            g.drawString(font, font.substrByWidth(line, POPUP_W - 12).getString(), popX + 6, ly, line.getStyle().getColor() != null
                    ? 0xFF000000 | line.getStyle().getColor().getValue() : 0xFFC0C0C0, false);
            ly += 10;
        }
        // Share toggle (own home manholes only; the server re-checks ownership).
        shareY0 = -1;
        if (shareBtn) {
            shareX0 = popX + 6;
            shareX1 = popX + POPUP_W - 6;
            shareY0 = popY + popH - 6 - 16 - 4 - 16;
            shareY1 = shareY0 + 16;
            boolean sHot = mouseX >= shareX0 && mouseX < shareX1 && mouseY >= shareY0 && mouseY < shareY1;
            button(g, shareX0, shareY0, shareX1, shareY1, true, sHot, false);
            Component sl = Component.translatable(e.shared() ? "manholes.screen.share.on" : "manholes.screen.share.off");
            g.drawString(font, sl, (shareX0 + shareX1 - font.width(sl)) / 2, shareY0 + 4, buttonText(true), false);
        }
        // Travel control.
        travelX0 = popX + 6;
        travelY0 = popY + popH - 6 - 16;
        travelX1 = popX + POPUP_W - 6;
        travelY1 = travelY0 + 16;
        boolean can = canTravel(e);
        boolean tHot = can && mouseX >= travelX0 && mouseX < travelX1 && mouseY >= travelY0 && mouseY < travelY1;
        boolean pressed = can && travelPressed && tHot;
        button(g, travelX0, travelY0, travelX1, travelY1, can, tHot, pressed);
        Component label = Component.translatable(cur ? "manholes.screen.you_are_here" : reachable(e) ? "manholes.screen.travel"
                : "manholes.screen.unreachable").withStyle(can ? ChatFormatting.BOLD : ChatFormatting.RESET);
        // No drop shadow: light text on the dark fill reads cleanly; pressed nudges it 1 px down.
        int lw = font.width(label);
        g.drawString(font, label, (travelX0 + travelX1 - lw) / 2, travelY0 + 4 + (pressed ? 1 : 0), buttonText(can), false);
        if (rename != null) {
            rename.setX(popX + 5);
            rename.setY(popY + 4);
            rename.render(g, mouseX, mouseY, pt);
        }
        g.pose().popPose();
    }

    private int rowY(Entry e) {
        if (isCurrent(e)) {
            return TOP + 3;
        }
        int idx = others().indexOf(e);
        return listTop() - (int) scroll + Math.max(0, idx) * ROW_H;
    }

    private boolean canTravel(Entry e) {
        return !isCurrent(e) && reachable(e);
    }

    private void openPopup(Entry e, boolean centre) {
        cancelRename();
        popup = e.id();
        if (!isCurrent(e)) {
            selected = e.id();
        }
        if (centre && onMap(e)) {
            viewX = e.pos().getX() + 0.5;
            viewZ = e.pos().getZ() + 0.5;
        }
    }

    private void closePopup() {
        cancelRename();
        popup = null;
    }

    private void startRename(Entry e) {
        rename = new EditBox(font, popX + 5, popY + 4, POPUP_W - 12, 14, Component.translatable("manholes.screen.rename"));
        rename.setMaxLength(RenameNodePayload.MAX_LENGTH);
        rename.setValue(e.name().getString());
        rename.setFocused(true);
        rename.setHint(Component.translatable("manholes.screen.rename_hint"));
        setFocused(rename);
    }

    private void confirmRename() {
        Entry e = byId(popup);
        if (rename != null && e != null) {
            String name = rename.getValue().strip();
            PacketDistributor.sendToServer(new RenameNodePayload(e.id(), name));
            if (!name.isEmpty()) {
                int i = entries.indexOf(e);
                entries.set(i, e.withName(Component.literal(name), true)); // optimistic; the sync confirms it
            }
        }
        cancelRename();
    }

    private void cancelRename() {
        if (rename != null) {
            rename = null;
            setFocused(null);
        }
    }

    private void travel(Entry e) {
        if (canTravel(e)) {
            PacketDistributor.sendToServer(new TravelRequestPayload(current().id(), e.id()));
            onClose();
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (rename != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                confirmRename();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cancelRename();
                return true;
            }
            return rename.keyPressed(keyCode, scanCode, modifiers) || true; // swallow keys (e.g. inventory) while typing
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && popup != null) {
            closePopup();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (rename != null) {
            return rename.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (inPopup(mouseX, mouseY)) {
            Entry e = byId(popup);
            if (rename != null) {
                if (rename.isMouseOver(mouseX, mouseY)) {
                    return rename.mouseClicked(mouseX, mouseY, button);
                }
                confirmRename();
            }
            if (e != null && mouseX >= pencilX && mouseX < pencilX + 10 && mouseY >= pencilY && mouseY < pencilY + 11) {
                startRename(e);
            } else if (e != null && e.home() && e.mine() && shareY0 >= 0 && mouseX >= shareX0 && mouseX < shareX1
                    && mouseY >= shareY0 && mouseY < shareY1) {
                boolean now = !e.shared();
                PacketDistributor.sendToServer(new it.ratlab.manholes.net.ShareHomePayload(e.id(), now));
                entries.set(entries.indexOf(e), e.withShared(now)); // optimistic; the next network sync confirms it
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
            } else if (e != null && canTravel(e) && mouseX >= travelX0 && mouseX < travelX1 && mouseY >= travelY0 && mouseY < travelY1) {
                travelPressed = true; // fires on release over the button
            }
            return true;
        }
        if (rename != null) {
            confirmRename(); // clicking elsewhere keeps what was typed
        }
        Entry dot = dotAt(mouseX, mouseY);
        if (dot != null) {
            openPopup(dot, false);
            return true;
        }
        Entry row = rowAt(mouseX, mouseY);
        if (row != null) {
            long now = System.currentTimeMillis();
            boolean dbl = row.id().equals(lastRowClicked) && now - lastRowClick < 300;
            lastRowClick = now;
            lastRowClicked = row.id();
            openPopup(row, true);
            if (dbl) {
                startRename(row);
            }
            return true;
        }
        if (inMap(mouseX, mouseY)) {
            dragging = true;
            pressX = mouseX;
            pressY = mouseY;
            pressOnEmptyMap = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && travelPressed) {
            travelPressed = false;
            Entry e = byId(popup);
            if (e != null && mouseX >= travelX0 && mouseX < travelX1 && mouseY >= travelY0 && mouseY < travelY1) {
                travel(e);
                return true;
            }
        }
        if (button == 0 && pressOnEmptyMap) {
            pressOnEmptyMap = false;
            if (Math.abs(mouseX - pressX) < 3 && Math.abs(mouseY - pressY) < 3) {
                closePopup(); // a click (not a drag) on empty map
            }
        }
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            viewX -= dragX / scale;
            viewZ -= dragY / scale;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inMap(mouseX, mouseY) && !inPopup(mouseX, mouseY)) {
            // Zoom around the cursor.
            double cx = (mapLeft() + mapRight()) / 2.0;
            double cy = (mapTop() + mapBottom()) / 2.0;
            double wx = viewX + (mouseX - cx) / scale;
            double wz = viewZ + (mouseY - cy) / scale;
            scale = Mth.clamp(scale * Math.pow(1.25, scrollY), 0.002, 8);
            viewX = wx - (mouseX - cx) / scale;
            viewZ = wz - (mouseY - cy) / scale;
            return true;
        }
        if (inList(mouseX, mouseY)) {
            scroll = Mth.clamp(scroll - scrollY * ROW_H, 0, maxScroll());
        }
        return true;
    }
}
