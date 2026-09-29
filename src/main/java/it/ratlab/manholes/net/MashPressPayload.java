// SPDX-License-Identifier: MIT
package it.ratlab.manholes.net;

import it.ratlab.manholes.Manholes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: the mash key was pressed once during the LEVER phase. Carries nothing: the server counts presses
 * itself, caps them per second and ignores any that don't belong to a running LEVER phase.
 */
public record MashPressPayload() implements CustomPacketPayload {
    public static final MashPressPayload INSTANCE = new MashPressPayload();
    public static final Type<MashPressPayload> TYPE = new Type<>(Manholes.id("mash_press"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MashPressPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
