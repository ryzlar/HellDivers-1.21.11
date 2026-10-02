package net.ryzlar.strike;

/**
 * Damage by distance, as a piecewise-linear curve: e.g. {@code of(0, 1000, 15, 400, 40, 90, 150, 0)} means
 * 1000 at the center, falling to 400 at 15 blocks, 90 at 40 and 0 at 150. Beyond the last radius: no damage.
 * Every attack phase gets its own profile, so damage follows what the player actually sees.
 */
public final class DamageProfile {

    private final float[] radii;
    private final float[] damage;

    private DamageProfile(float[] radii, float[] damage) {
        this.radii = radii;
        this.damage = damage;
    }

    /** Pairs of (radius, damage) with ascending radii. */
    public static DamageProfile of(float... pairs) {
        if (pairs.length < 4 || pairs.length % 2 != 0) {
            throw new IllegalArgumentException("DamageProfile needs at least two (radius, damage) pairs");
        }
        int n = pairs.length / 2;
        float[] radii = new float[n];
        float[] damage = new float[n];
        for (int i = 0; i < n; i++) {
            radii[i] = pairs[i * 2];
            damage[i] = pairs[i * 2 + 1];
        }
        return new DamageProfile(radii, damage);
    }

    public float at(double distance) {
        if (distance <= radii[0]) return damage[0];
        for (int i = 1; i < radii.length; i++) {
            if (distance <= radii[i]) {
                float t = (float) ((distance - radii[i - 1]) / (radii[i] - radii[i - 1]));
                return damage[i - 1] + (damage[i] - damage[i - 1]) * t;
            }
        }
        return 0.0f;
    }

    public float maxRadius() {
        return radii[radii.length - 1];
    }

    public float peak() {
        return damage[0];
    }
}
