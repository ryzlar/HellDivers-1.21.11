package net.ryzlar.entities;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Single source of truth for the stratagem ball's flight.
 * Used by the server (spawning the entity) and the client (trajectory preview),
 * so the preview can never drift from the real throw.
 */
public final class StrategemBallPhysics {

    public static final double GRAVITY = 0.03;
    // Same values vanilla ThrowableProjectile.applyInertia() uses
    public static final double AIR_DRAG = 0.99;
    public static final double WATER_DRAG = 0.8;

    public static final double MIN_VELOCITY = 0.4;  // tap-throw still leaves your hand
    public static final double MAX_VELOCITY = 1.6;
    public static final double SPAWN_FORWARD_OFFSET = 0.5;

    private StrategemBallPhysics() {
    }

    /** Bow-style easing: quick to build up, flattens out towards full charge. */
    public static float chargeCurve(float power) {
        float p = Mth.clamp(power, 0.0f, 1.0f);
        return (p * p + p * 2.0f) / 3.0f;
    }

    public static Vec3 spawnPos(Vec3 eyePos, Vec3 look) {
        return eyePos.add(look.scale(SPAWN_FORWARD_OFFSET));
    }

    public static Vec3 launchVelocity(Vec3 look, float power) {
        return look.scale(Mth.lerp(chargeCurve(power), MIN_VELOCITY, MAX_VELOCITY));
    }

    /** One tick, in the exact order of ThrowableProjectile.tick(): gravity, then drag, then move. */
    public static Vec3 nextVelocity(Vec3 velocity, boolean inWater) {
        return velocity.add(0, -GRAVITY, 0).scale(inWater ? WATER_DRAG : AIR_DRAG);
    }
}
