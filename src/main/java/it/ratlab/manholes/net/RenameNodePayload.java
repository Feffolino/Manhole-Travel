// SPDX-License-Identifier: MIT
package it.ratlab.manholes.net;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> server: rename a node of the player's network from the travel screen. Stored as a per-owner alias;
 * an empty name clears it. The server checks membership and clamps the length.
 */
public record RenameNodePayload(UUID node, String name) {
    public static final int MAX_LENGTH = 32;

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(node);
        buf.writeUtf(name, MAX_LENGTH * 4);
    }

    public static RenameNodePayload decode(FriendlyByteBuf buf) {
        return new RenameNodePayload(buf.readUUID(), buf.readUtf(MAX_LENGTH * 4));
    }
}
