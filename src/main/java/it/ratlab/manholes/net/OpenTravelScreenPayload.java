// SPDX-License-Identifier: MIT
package it.ratlab.manholes.net;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: open the travel screen. The first entry is the current node. */
public record OpenTravelScreenPayload(UUID current, List<Entry> entries, boolean crossDimension) {
    /**
     * One node as the player sees it. {@code name} is the owner's alias if one is set, else the node's own name;
     * {@code aliased} says which. {@code rust} is 0..3. {@code look} is the cover's look id. Home manholes (1.5.0):
     * {@code home}, {@code ownerName} ("" = unowned / unknown), {@code mine} (the viewer owns it, so he may toggle
     * sharing) and {@code shared} (effective: unowned homes count as shared).
     */
    public record Entry(UUID id, Component name, ResourceLocation dimension, BlockPos pos, int rust, boolean aliased, String look,
            boolean home, String ownerName, boolean mine, boolean shared) {

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeComponent(name);
            buf.writeResourceLocation(dimension);
            buf.writeBlockPos(pos);
            buf.writeVarInt(rust);
            buf.writeBoolean(aliased);
            buf.writeUtf(look);
            buf.writeBoolean(home);
            buf.writeUtf(ownerName);
            buf.writeBoolean(mine);
            buf.writeBoolean(shared);
        }

        public static Entry decode(FriendlyByteBuf buf) {
            return new Entry(
                    buf.readUUID(),
                    buf.readComponent(),
                    buf.readResourceLocation(),
                    buf.readBlockPos(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readUtf(),
                    buf.readBoolean(),
                    buf.readUtf(),
                    buf.readBoolean(),
                    buf.readBoolean()
            );
        }

        public Entry withName(Component newName, boolean isAlias) {
            return new Entry(id, newName, dimension, pos, rust, isAlias, look, home, ownerName, mine, shared);
        }

        public Entry withShared(boolean nowShared) {
            return new Entry(id, name, dimension, pos, rust, aliased, look, home, ownerName, mine, nowShared);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(current);
        buf.writeVarInt(entries.size());
        for (Entry e : entries) {
            e.encode(buf);
        }
        buf.writeBoolean(crossDimension);
    }

    public static OpenTravelScreenPayload decode(FriendlyByteBuf buf) {
        UUID current = buf.readUUID();
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(Entry.decode(buf));
        }
        return new OpenTravelScreenPayload(current, list, buf.readBoolean());
    }
}
