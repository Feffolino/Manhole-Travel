// SPDX-License-Identifier: MIT
package it.ratlab.manholes.net;

import it.ratlab.manholes.Manholes;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: open the travel screen. The first entry is the current node. */
public record OpenTravelScreenPayload(UUID current, List<Entry> entries, boolean crossDimension) implements CustomPacketPayload {
    public static final Type<OpenTravelScreenPayload> TYPE = new Type<>(Manholes.id("open_travel_screen"));

    /**
     * One node as the player sees it. {@code name} is the owner's alias if one is set, else the node's own name;
     * {@code aliased} says which. {@code rust} is 0..3. {@code look} is the cover's look id. Home manholes (1.5.0):
     * {@code home}, {@code ownerName} ("" = unowned / unknown), {@code mine} (the viewer owns it, so he may toggle
     * sharing) and {@code shared} (effective: unowned homes count as shared).
     */
    public record Entry(UUID id, Component name, ResourceLocation dimension, BlockPos pos, int rust, boolean aliased, String look,
            boolean home, String ownerName, boolean mine, boolean shared) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.of(
                (buf, e) -> {
                    UUIDUtil.STREAM_CODEC.encode(buf, e.id);
                    ComponentSerialization.STREAM_CODEC.encode(buf, e.name);
                    ResourceLocation.STREAM_CODEC.encode(buf, e.dimension);
                    BlockPos.STREAM_CODEC.encode(buf, e.pos);
                    ByteBufCodecs.VAR_INT.encode(buf, e.rust);
                    ByteBufCodecs.BOOL.encode(buf, e.aliased);
                    ByteBufCodecs.STRING_UTF8.encode(buf, e.look);
                    ByteBufCodecs.BOOL.encode(buf, e.home);
                    ByteBufCodecs.STRING_UTF8.encode(buf, e.ownerName);
                    ByteBufCodecs.BOOL.encode(buf, e.mine);
                    ByteBufCodecs.BOOL.encode(buf, e.shared);
                },
                buf -> new Entry(UUIDUtil.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf),
                        ResourceLocation.STREAM_CODEC.decode(buf), BlockPos.STREAM_CODEC.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf)));

        public Entry withName(Component newName, boolean isAlias) {
            return new Entry(id, newName, dimension, pos, rust, isAlias, look, home, ownerName, mine, shared);
        }

        public Entry withShared(boolean nowShared) {
            return new Entry(id, name, dimension, pos, rust, aliased, look, home, ownerName, mine, nowShared);
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenTravelScreenPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                UUIDUtil.STREAM_CODEC.encode(buf, p.current);
                buf.writeVarInt(p.entries.size());
                for (Entry e : p.entries) {
                    Entry.CODEC.encode(buf, e);
                }
                buf.writeBoolean(p.crossDimension);
            },
            buf -> {
                UUID current = UUIDUtil.STREAM_CODEC.decode(buf);
                int n = buf.readVarInt();
                List<Entry> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    list.add(Entry.CODEC.decode(buf));
                }
                return new OpenTravelScreenPayload(current, list, buf.readBoolean());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
