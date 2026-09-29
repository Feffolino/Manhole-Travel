// SPDX-License-Identifier: MIT
package it.ratlab.manholes.client.cover;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The lid-part transform of a look, in block units (model px / 16). Side-neutral (only JOML), so the game tests can
 * check it on a dedicated server.
 * <p>
 * Convention = vanilla block-model element rotation ({@code FaceBakery.applyElementRotation}): a quaternion
 * {@code rotationAxis(angle in radians, unit axis)} (right-hand rule, positive angle), applied about {@code origin}
 * given in model px: {@code v' = origin + R (v - origin)}. Unlike model files, any angle is allowed (a hatch swings
 * ~100 degrees); only the static fallback models are limited to +-45 / 22.5 steps. Vanilla's {@code rescale} is not
 * applied. The translation comes after the rotation: {@code v'' = v' + translate * t}.
 */
public final class LidMath {
    private LidMath() {}

    /** Unit axis of 'x', 'y' or 'z'. */
    public static Vector3f axis(char a) {
        return switch (Character.toLowerCase(a)) {
            case 'x' -> new Vector3f(1, 0, 0);
            case 'y' -> new Vector3f(0, 1, 0);
            default -> new Vector3f(0, 0, 1);
        };
    }

    /**
     * Matrix of a lid part at openness t (0..1): rotate by {@code angle * t} degrees about {@code axis} through
     * {@code originPx}, then translate by {@code translatePx * t}. Positions in block units.
     */
    public static Matrix4f partMatrix(char axis, float angleDeg, Vector3f originPx, Vector3f translatePx, float t) {
        Matrix4f m = new Matrix4f();
        if (translatePx != null) {
            m.translate(translatePx.x * t / 16f, translatePx.y * t / 16f, translatePx.z * t / 16f);
        }
        if (angleDeg != 0f) {
            float ox = originPx.x / 16f;
            float oy = originPx.y / 16f;
            float oz = originPx.z / 16f;
            m.translate(ox, oy, oz);
            m.rotate(new Quaternionf().rotationAxis((float) Math.toRadians(angleDeg * t), axis(axis)));
            m.translate(-ox, -oy, -oz);
        }
        return m;
    }
}
