// SPDX-License-Identifier: MIT
package it.ratlab.manholes.net;

import it.ratlab.manholes.Manholes;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: rename a node of the player's network from the travel screen. Stored as a per-owner alias;
 * an empty name clears it. The server checks membership and clamps the length.
 */
public record RenameNodePayload(UUID node, String name) implements CustomPacketPayload {
    public static final int MAX_LENGTH = 32;
    public static final Type<RenameNodePayload> TYPE = new Type<>(Manholes.id("rename_node"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RenameNodePayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RenameNodePayload::node,
            ByteBufCodecs.stringUtf8(MAX_LENGTH * 4), RenameNodePayload::name,
            RenameNodePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
