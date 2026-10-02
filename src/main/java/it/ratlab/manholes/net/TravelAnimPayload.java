// SPDX-License-Identifier: MIT
package it.ratlab.manholes.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/**
 * Server -> client: play the climb animation. Positions are feet positions; the client adds the eye height and only
 * moves the camera (the server already placed the player).
 * <ul>
 * <li>{@code DESCENT}: camera from {@code from} (with {@code fromYaw}/{@code fromPitch}) onto {@code cover}, turning to
 * {@code yaw}, pitch down to 80, then 1.5 blocks down with the fade. Lasts {@code ticks}, then stays black for
 * {@code holdTicks} (the trip) until the ASCENT arrives.</li>
 * <li>{@code ASCENT}: fade in 1.5 blocks below {@code cover} looking up (-70), rise, step off to {@code to} with
 * {@code yaw}, level the head. Lasts {@code ticks}.</li>
 * </ul>
 */
public record TravelAnimPayload(int kind, Vec3 from, float fromYaw, float fromPitch, Vec3 cover, float yaw, Vec3 to,
        int ticks, int holdTicks) {
    public static final int DESCENT = 1;
    public static final int ASCENT = 2;

    private static Vec3 vec(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    private static void vec(FriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x);
        buf.writeDouble(v.y);
        buf.writeDouble(v.z);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(kind);
        vec(buf, from);
        buf.writeFloat(fromYaw);
        buf.writeFloat(fromPitch);
        vec(buf, cover);
        buf.writeFloat(yaw);
        vec(buf, to);
        buf.writeVarInt(ticks);
        buf.writeVarInt(holdTicks);
    }

    public static TravelAnimPayload decode(FriendlyByteBuf buf) {
        return new TravelAnimPayload(buf.readByte(), vec(buf), buf.readFloat(), buf.readFloat(), vec(buf), buf.readFloat(),
                vec(buf), buf.readVarInt(), buf.readVarInt());
    }
}
