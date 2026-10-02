// SPDX-License-Identifier: MIT
package it.ratlab.manholes.net;

import it.ratlab.manholes.net.OpenTravelScreenPayload.Entry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

/** Server -> client: the nodes in the player's network (all dimensions), for map icons. Sent on login and on changes. */
public record NetworkSyncPayload(List<Entry> nodes) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(nodes.size());
        for (Entry e : nodes) {
            e.encode(buf);
        }
    }

    public static NetworkSyncPayload decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(Entry.decode(buf));
        }
        return new NetworkSyncPayload(list);
    }
}
