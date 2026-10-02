// SPDX-License-Identifier: MIT
package it.ratlab.manholes.net;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Server -> client, every tick of a phased pry session: what the HUD shows. {@code phase} 0 = no session (hide the HUD),
 * 1 insert, 2 lever, 3 slide. {@code progress} is the bar of the current phase (0..1). {@code presses} counts the
 * accepted mash presses of this attempt (the client flashes / shakes the bar and pumps the crowbar when it grows).
 * {@code danger}: the lever bar is decaying low after having been higher (the give-up rule is close).
 * 1.5.0: {@code look} (the cover's look id, for the condition label) and {@code offHand} (the hand holding the pry tool,
 * whose stack the HUD draws).
 */
public record PryStatePayload(int phase, float progress, int rust, int presses, boolean danger, String look, boolean offHand) {
    public static final PryStatePayload NONE = new PryStatePayload(0, 0, 0, 0, false, "", false);

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(phase);
        buf.writeFloat(progress);
        buf.writeByte(rust);
        buf.writeVarInt(presses);
        buf.writeBoolean(danger);
        buf.writeUtf(look, 256);
        buf.writeBoolean(offHand);
    }

    public static PryStatePayload decode(FriendlyByteBuf buf) {
        return new PryStatePayload(buf.readByte(), buf.readFloat(), buf.readByte(), buf.readVarInt(), buf.readBoolean(), buf.readUtf(256), buf.readBoolean());
    }
}
