// SPDX-License-Identifier: MIT
package it.ratlab.manholes.net;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/** Client -> server (1.5.0): the "Share with team" toggle of a home manhole's popup. The server checks ownership. */
public record ShareHomePayload(UUID node, boolean shared) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(node);
        buf.writeBoolean(shared);
    }

    public static ShareHomePayload decode(FriendlyByteBuf buf) {
        return new ShareHomePayload(buf.readUUID(), buf.readBoolean());
    }
}
