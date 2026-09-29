// SPDX-License-Identifier: MIT
package it.ratlab.manholes.compat.kubejs;

import it.ratlab.manholes.api.ManholesAPI;
import it.ratlab.manholes.api.NodeView;
import it.ratlab.manholes.data.ManholeData;
import it.ratlab.manholes.data.NodeRecord;
import it.ratlab.manholes.gen.SpawnRuleManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The {@code Manholes} binding of server scripts. Node ids are strings: the full uuid or a unique prefix (short id).
 */
public final class ManholesBindingJS {
    @Nullable
    private static NodeRecord node(MinecraftServer server, String nodeId) {
        return ManholesAPI.resolve(server, nodeId);
    }

    @Nullable
    private static ServerPlayer sp(Player player) {
        return player instanceof ServerPlayer s ? s : null;
    }

    /** Adds the node to the player's team network (as if pried, without the pried event). */
    public boolean open(Player player, String nodeId) {
        ServerPlayer p = sp(player);
        NodeRecord r = p == null ? null : node(p.server, nodeId);
        return r != null && ManholesAPI.open(p, r);
    }

    /** Removes the node from the player's team network. */
    public boolean close(Player player, String nodeId) {
        ServerPlayer p = sp(player);
        NodeRecord r = p == null ? null : node(p.server, nodeId);
        return r != null && ManholesAPI.close(p, r);
    }

    /**
     * Sets the player's team alias of a node (what the travel screen, the map icons and the arrival message show for
     * that team). An empty string clears it. Plain text, max 32 characters. Scripts are trusted: the node does not have to
     * be in the network. Returns true if the alias changed.
     */
    public boolean rename(Player player, String nodeId, String name) {
        ServerPlayer p = sp(player);
        NodeRecord r = p == null ? null : node(p.server, nodeId);
        return r != null && ManholesAPI.setAlias(p, r, name);
    }

    public boolean isOpen(Player player, String nodeId) {
        ServerPlayer p = sp(player);
        NodeRecord r = p == null ? null : node(p.server, nodeId);
        return r != null && ManholesAPI.isOpen(p, r.id);
    }

    /** Nodes in the player's team network. */
    public List<NodeView> nodes(Player player) {
        List<NodeView> out = new ArrayList<>();
        ServerPlayer p = sp(player);
        if (p != null) {
            for (NodeRecord r : ManholesAPI.network(p)) {
                out.add(new NodeView(r, p.registryAccess()));
            }
        }
        return out;
    }

    /** Node of the manhole at pos, or null. */
    @Nullable
    public NodeView nodeAt(Level level, BlockPos pos) {
        return level instanceof ServerLevel sl ? NodeView.of(ManholesAPI.nodeAt(sl, pos), sl.registryAccess()) : null;
    }

    /** Any node by id, or null. */
    @Nullable
    public NodeView get(Level level, String nodeId) {
        return level instanceof ServerLevel sl ? NodeView.of(node(sl.getServer(), nodeId), sl.registryAccess()) : null;
    }

    /** Every node in the registry. */
    public List<NodeView> all(Level level) {
        List<NodeView> out = new ArrayList<>();
        if (level instanceof ServerLevel sl) {
            for (NodeRecord r : ManholeData.get(sl.getServer()).nodes()) {
                out.add(new NodeView(r, sl.registryAccess()));
            }
        }
        return out;
    }

    /** Scripted travel with the normal fade (no cost, no membership check). */
    public boolean travel(Player player, String nodeId) {
        ServerPlayer p = sp(player);
        NodeRecord r = p == null ? null : node(p.server, nodeId);
        return r != null && ManholesAPI.travel(p, r);
    }

    /** Places a real network manhole: {@code Manholes.place(level, pos, {name, ruleId, open:false, facing:'north'})}. */
    @Nullable
    public NodeView place(Level level, BlockPos pos, @Nullable Map<?, ?> options) {
        if (!(level instanceof ServerLevel sl)) {
            return null;
        }
        String name = null;
        String ruleId = null;
        boolean open = false;
        Direction facing = null;
        if (options != null) {
            Object n = options.get("name");
            if (n != null) {
                name = n instanceof CharSequence ? n.toString() : JsonConvert.toJson(n).toString();
            }
            Object r = options.get("ruleId");
            if (r != null) {
                ruleId = r.toString();
            }
            Object o = options.get("open");
            open = o instanceof Boolean b ? b : o != null && Boolean.parseBoolean(o.toString());
            Object f = options.get("facing");
            if (f != null) {
                facing = Direction.byName(f.toString());
            }
        }
        return NodeView.of(ManholesAPI.place(sl, pos, name, ruleId, open, facing), sl.registryAccess());
    }

    public NodeView place(Level level, BlockPos pos) {
        return place(level, pos, null);
    }

    /**
     * Swaps the cover at pos for another cover block ({@code 'manholes:hatch'}, or a look id like {@code 'cave'}),
     * keeping its node, name, facing and open state.
     */
    public boolean setBlock(Level level, BlockPos pos, String blockId) {
        return level instanceof ServerLevel sl && ManholesAPI.setBlock(sl, pos, blockId);
    }

    /** @deprecated since 1.4.0: use {@link #setBlock} (logs a warning once). */
    @Deprecated
    public boolean setLook(Level level, BlockPos pos, String look) {
        return level instanceof ServerLevel sl && ManholesAPI.setLook(sl, pos, look);
    }

    /**
     * 1.5.0: "share with team" of the home manhole at pos (scripts are trusted: no owner check). False if there's no
     * home manhole there.
     */
    public boolean setShared(Level level, BlockPos pos, boolean shared) {
        return level instanceof ServerLevel sl && ManholesAPI.setShared(sl, pos, shared);
    }

    /** Removes the manhole at pos (and its node). */
    public boolean remove(Level level, BlockPos pos) {
        return level instanceof ServerLevel sl && ManholesAPI.remove(sl, pos);
    }

    /** Ids of the active spawn rules. */
    public List<String> ruleIds() {
        List<String> out = new ArrayList<>();
        SpawnRuleManager.ids().forEach(r -> out.add(r.toString()));
        return out;
    }
}
