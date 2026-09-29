// SPDX-License-Identifier: MIT
package it.ratlab.manholes.net;

import it.ratlab.manholes.Manholes;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: "take me from this node to that one". The server re-validates everything. */
public record TravelRequestPayload(UUID from, UUID to) implements CustomPacketPayload {
    public static final Type<TravelRequestPayload> TYPE = new Type<>(Manholes.id("travel_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TravelRequestPayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, TravelRequestPayload::from,
            UUIDUtil.STREAM_CODEC, TravelRequestPayload::to,
            TravelRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
