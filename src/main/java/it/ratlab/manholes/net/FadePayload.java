// SPDX-License-Identifier: MIT
package it.ratlab.manholes.net;

import net.minecraft.network.FriendlyByteBuf;

/** Server -> client: start the travel fade ({@code ticks} long), or cancel it ({@code ticks <= 0}). */
public record FadePayload(int ticks) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(ticks);
    }

    public static FadePayload decode(FriendlyByteBuf buf) {
        return new FadePayload(buf.readVarInt());
    }
}
