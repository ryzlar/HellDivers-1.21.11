package net.ryzlar.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Composite effects built from {@link FxDraw} that several strikes share, each tinted to the strike's own
 * palette: target designators, ground shockwaves, impact flashes, particle bursts.
 */
public final class FxPresets {

    private FxPresets() {
    }

    /**
     * Holographic target designator draped over the real terrain: outer ring, rotating segments, inward ticks,
     * a contracting pulse and a marker beam into the sky. {@code urgency} 0..1 speeds it up.
     */
    public static void designator(FxContext ctx, Vec3 c, float radius, int rgb, float age, float alpha, float urgency) {
        if (alpha <= 0.01f) return;
        float t = age / 20.0f;
        float speed = 1.0f + urgency * 3.0f;
        FxDraw.drapedRing(ctx, true, c, radius, 0.16f, rgb, 0.85f * alpha, 0.06f);
        FxDraw.drapedDisc(ctx, true, c, radius, rgb, 0.0f, 0.1f * alpha, 0.05f);
        float spin = t * 0.8f * speed;
        for (int i = 0; i < 3; i++) {
            float start = spin + i * Mth.TWO_PI / 3;
            List<Vec3> arc = new java.util.ArrayList<>();
            for (int k = 0; k <= 12; k++) {
                float a = start + 1.3f * k / 12;
                arc.add(c.add(Mth.cos(a) * radius * 0.72f, 0, Mth.sin(a) * radius * 0.72f));
            }
            float[] w = new float[13], al = new float[13];
            java.util.Arrays.fill(w, 0.1f);
            java.util.Arrays.fill(al, 0.75f * alpha);
            FxDraw.drapedRibbon(ctx, true, arc, w, al, rgb, 0.07f);
        }
        for (int i = 0; i < 4; i++) {
            float a = i * Mth.HALF_PI + Mth.PI / 4;
            List<Vec3> tick = List.of(c.add(Mth.cos(a) * radius * 1.25f, 0, Mth.sin(a) * radius * 1.25f),
                    c.add(Mth.cos(a) * radius * 0.88f, 0, Mth.sin(a) * radius * 0.88f));
            FxDraw.drapedRibbon(ctx, true, tick, new float[]{0.2f, 0.07f}, new float[]{0.9f * alpha, 0.9f * alpha}, rgb, 0.07f);
        }
        float pulse = (t * speed * 0.8f) % 1.0f;
        FxDraw.drapedRing(ctx, true, c, radius * (1.2f - pulse), 0.12f, rgb, pulse * 0.7f * alpha, 0.08f);
        Vector3f ground = ctx.rel(c.x, groundY(ctx.level, c.x, c.z), c.z);
        FxDraw.sprite(ctx, true, new Vector3f(ground).add(0, 0.3f, 0), 0.6f, FxDraw.lighten(rgb, 0.5f), 0.8f * alpha, 0.0f);
        FxDraw.beam(ctx, true, ground, new Vector3f(ground).add(0, 60, 0), 0.12f + 0.05f * urgency, rgb, 0.65f * alpha, 0.0f);
    }

    /**
     * Ground shockwave following the terrain: a bright front ring draped over the ground and a rolling wall of dust
     * puffs that thins as it travels (soft, so it blends with the ground it rolls over).
     */
    public static void groundShock(FxContext ctx, Vec3 c, float radius, float maxRadius, int edgeRgb, int dustRgb,
                                   float strength, int seed) {
        if (radius <= 0 || radius > maxRadius) return;
        float fade = 1.0f - radius / maxRadius;
        FxDraw.drapedRing(ctx, true, c, radius, 0.8f + radius * 0.03f, edgeRgb, 0.9f * fade * strength, 0.15f);
        FxDraw.drapedRing(ctx, false, c, Math.max(0.1f, radius - 1.5f), 1.6f + radius * 0.04f, dustRgb, 0.35f * fade * strength, 0.1f);
        FxDraw.Puffs dust = new FxDraw.Puffs();
        int count = Mth.clamp((int) (radius * 1.2f), 12, 64);
        for (int i = 0; i < count; i++) {
            float a = Mth.TWO_PI * (i + FxDraw.hash(i, seed) * 0.6f) / count;
            float rr = radius - 1.0f - 1.5f * FxDraw.hash(seed, i);
            double x = c.x + Mth.cos(a) * rr, z = c.z + Mth.sin(a) * rr;
            float size = (1.4f + 2.0f * FxDraw.hash(i, seed + 1)) * (0.6f + 0.6f * fade) * strength;
            Vector3f p = ctx.rel(x, groundY(ctx.level, x, z) + size * 0.5f, z);
            dust.add(p, size, dustRgb, 0.55f * fade);
        }
        dust.draw(ctx);
    }

    /** Layered fireball / energy burst of {@code radius}; {@code heat} 1 = white-hot core, 0 = dull. */
    public static void fireball(FxContext ctx, Vector3f c, float radius, float heat, float alpha, int outer, int mid) {
        if (alpha <= 0.01f) return;
        FxDraw.sprite(ctx, true, c, radius * 1.5f, outer, 0.35f * alpha, 0.0f);
        FxDraw.sprite(ctx, true, c, radius, mid, 0.7f * alpha, 0.0f);
        FxDraw.sprite(ctx, true, c, radius * 0.55f, FxDraw.lighten(mid, 0.5f), 0.8f * alpha * (0.4f + 0.6f * heat), 0.0f);
        if (heat > 0.05f) FxDraw.sprite(ctx, true, c, radius * 0.3f, 0xFFFFFF, heat * alpha, 0.0f);
    }

    /** Ground height at x/z (top of the highest motion-blocking block), as seen by the client. */
    public static float groundY(ClientLevel level, double x, double z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
    }

    /** Spherical particle burst; uses always-visible particles so it shows from far away. */
    public static void burst(ClientLevel level, ParticleOptions particle, Vec3 pos, int count, double spread, double speed, RandomSource random) {
        for (int i = 0; i < count; i++) {
            double vx = random.nextGaussian(), vy = random.nextGaussian(), vz = random.nextGaussian();
            double len = Math.sqrt(vx * vx + vy * vy + vz * vz) + 1.0e-4;
            double s = speed * (0.4 + random.nextDouble() * 0.6) / len;
            level.addAlwaysVisibleParticle(particle, true,
                    pos.x + random.nextGaussian() * spread, pos.y + random.nextGaussian() * spread, pos.z + random.nextGaussian() * spread,
                    vx * s, Math.abs(vy) * s, vz * s);
        }
    }
}
