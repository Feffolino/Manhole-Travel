// SPDX-License-Identifier: MIT
package it.ratlab.manholes.net;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> server: the mash key was pressed once during the LEVER phase. Carries nothing: the server counts presses
 * itself, caps them per second and ignores any that don't belong to a running LEVER phase.
 */
public record MashPressPayload() {
    public static final MashPressPayload INSTANCE = new MashPressPayload();

    public void encode(FriendlyByteBuf buf) {}

    public static MashPressPayload decode(FriendlyByteBuf buf) {
        return INSTANCE;
    }
}
