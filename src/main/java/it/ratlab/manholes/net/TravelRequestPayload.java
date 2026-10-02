// SPDX-License-Identifier: MIT
package it.ratlab.manholes.net;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/** Client -> server: "take me from this node to that one". The server re-validates everything. */
public record TravelRequestPayload(UUID from, UUID to) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(from);
        buf.writeUUID(to);
    }

    public static TravelRequestPayload decode(FriendlyByteBuf buf) {
        return new TravelRequestPayload(buf.readUUID(), buf.readUUID());
    }
}
