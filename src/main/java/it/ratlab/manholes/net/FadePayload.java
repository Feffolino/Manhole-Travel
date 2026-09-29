// SPDX-License-Identifier: MIT
package it.ratlab.manholes.net;

import it.ratlab.manholes.Manholes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: start the travel fade ({@code ticks} long), or cancel it ({@code ticks <= 0}). */
public record FadePayload(int ticks) implements CustomPacketPayload {
    public static final Type<FadePayload> TYPE = new Type<>(Manholes.id("fade"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FadePayload> CODEC =
            ByteBufCodecs.VAR_INT.<RegistryFriendlyByteBuf>cast().map(FadePayload::new, FadePayload::ticks);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
