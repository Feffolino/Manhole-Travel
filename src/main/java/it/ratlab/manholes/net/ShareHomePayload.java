// SPDX-License-Identifier: MIT
package it.ratlab.manholes.net;

import it.ratlab.manholes.Manholes;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server (1.5.0): the "Share with team" toggle of a home manhole's popup. The server checks ownership. */
public record ShareHomePayload(UUID node, boolean shared) implements CustomPacketPayload {
    public static final Type<ShareHomePayload> TYPE = new Type<>(Manholes.id("share_home"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShareHomePayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ShareHomePayload::node,
            ByteBufCodecs.BOOL, ShareHomePayload::shared,
            ShareHomePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
