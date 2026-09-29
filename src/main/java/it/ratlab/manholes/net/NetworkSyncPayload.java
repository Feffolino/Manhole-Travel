// SPDX-License-Identifier: MIT
package it.ratlab.manholes.net;

import it.ratlab.manholes.Manholes;
import it.ratlab.manholes.net.OpenTravelScreenPayload.Entry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: the nodes in the player's network (all dimensions), for map icons. Sent on login and on changes. */
public record NetworkSyncPayload(List<Entry> nodes) implements CustomPacketPayload {
    public static final Type<NetworkSyncPayload> TYPE = new Type<>(Manholes.id("network_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkSyncPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.nodes.size());
                for (Entry e : p.nodes) {
                    Entry.CODEC.encode(buf, e);
                }
            },
            buf -> {
                int n = buf.readVarInt();
                List<Entry> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    list.add(Entry.CODEC.decode(buf));
                }
                return new NetworkSyncPayload(list);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
