package net.ryzlar.strike.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.ryzlar.fx.CameraFx;
import net.ryzlar.fx.FxContext;
import net.ryzlar.fx.FxDraw;
import net.ryzlar.fx.FxMesh;
import net.ryzlar.fx.FxPresets;
import net.ryzlar.strike.MeteorStrike;
import net.ryzlar.strike.StrikeEntity;
import net.ryzlar.strike.StrikeVisual;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Meteor Strike: something heavy falling out of the sky.
 * <ul>
 *     <li><b>Warning</b>: a designator on the terrain, the sky starts burning where the meteor will enter, the light
 *     turns red, the ground trembles; in the last second and a half the pressure in front of the meteor lifts dust
 *     off the ground and makes loose stones jump.</li>
 *     <li><b>Entry</b>: a real 3D rock with a crawling molten surface, a white-hot bow shock, layered plasma trail,
 *     burning fragments breaking off, a thick smoke wake and heat haze behind it.</li>
 *     <li><b>Impact</b>: flash (scaled by line of sight), a ground-hugging fireball that blends into the terrain,
 *     a refracting pressure dome, a shock front that rolls over the actual terrain with a wall of dust, glowing
 *     ejecta on ballistic arcs trailing smoke, a fire plume turning into a smoke column.</li>
 *     <li><b>Aftermath</b>: the crater floor glows and cools over half a minute, heat shimmers above it, fires burn on
 *     the rim, smoke drifts off.</li>
 * </ul>
 */
public class MeteorVisual implements StrikeVisual {

    private static final int MARK = 0xFF7A1F;
    private static final int PLASMA_WHITE = 0xFFF6E0;
    private static final int PLASMA = 0xFFC060;
    private static final int FIRE = 0xFF6A1A;
    private static final int FIRE_DEEP = 0xD8300C;
    private static final int ROCK = 0x2B211B;
    private static final int SMOKE = 0x3A3330;
    private static final int SMOKE_LIT = 0x7A4A30;
    private static final int DUST = 0x7A6650;
    private static final int MOLTEN = 0xFF5A14;

    private static final int IMPACT = MeteorStrike.IMPACT;
    private static final int FALL_START = MeteorStrike.FALL_START;
    private static final int EJECTA = 36;

    // ---------------------------------------------------------------- distortion

    @Override
    public void renderDistortion(FxContext ctx, StrikeEntity strike, float age) {
        Vec3 target = strike.position();
        int seed = strike.getSeed();
        if (age >= FALL_START && age < IMPACT) {
            Vec3 pos = MeteorStrike.meteorPosition(target, seed, age);
            Vector3f head = ctx.rel(pos);
            Vector3f back = backward(seed);
            FxDraw.distortion(ctx, head, 9.0f, 0.2f, 1.0f, 0.0f, 0.7f);
            for (int k = 1; k <= 4; k++) {
                FxDraw.distortion(ctx, new Vector3f(back).mul(k * 9.0f).add(head), 6.0f + k * 1.5f, 0.0f, 1.0f, 0.0f, 0.55f - k * 0.1f);
            }
        }
        if (age >= IMPACT) {
            float d = age - IMPACT;
            Vector3f c = ctx.rel(target);
            if (d < 22) FxDraw.distortion(ctx, new Vector3f(c).add(0, 2, 0), 3 + d * 3.6f, 0.8f, 0.3f, 0.0f, 1.0f - d / 22.0f);
            float front = MeteorStrike.shockRadius(age);
            if (front < MeteorStrike.MAX_RADIUS) {
                float fade = 1.0f - front / MeteorStrike.MAX_RADIUS;
                for (int i = 0; i < 18; i++) {
                    float a = Mth.TWO_PI * i / 18;
                    double x = target.x + Mth.cos(a) * front, z = target.z + Mth.sin(a) * front;
                    Vector3f p = ctx.rel(x, FxPresets.groundY(ctx.level, x, z) + 1.5f, z);
                    FxDraw.distortion(ctx, p, 2.5f + front * 0.06f, -0.4f, 0.6f, 0.0f, 0.6f * fade);
                }
            }
            float heat = Mth.clamp(1.0f - d / 520.0f, 0.0f, 1.0f);
            FxDraw.heatHaze(ctx, new Vector3f(c).add(0, -3, 0), 6.0f, 14.0f, 0.75f * heat);
        }
    }

    // ---------------------------------------------------------------- solid: the meteor, fragments, ejecta, stones

    @Override
    public void renderSolid(FxContext ctx, StrikeEntity strike, float age) {
        Vec3 target = strike.position();
        int seed = strike.getSeed();
        if (age >= FALL_START && age < IMPACT) {
            Vec3 pos = MeteorStrike.meteorPosition(target, seed, age);
            Vector3f head = ctx.rel(pos);
            Vector3f back = backward(seed);
            Vector3f front = new Vector3f(back).negate();
            FxMesh.drawRock(ctx, head, MeteorStrike.METEOR_RADIUS, FxMesh.tumble(seed, age, 0.05f), seed, 2, ROCK, PLASMA_WHITE,
                    1.0f, front, age);
            // fragments breaking off and tumbling back into the trail
            for (int i = 0; i < 10; i++) {
                float t = (age * 0.05f + FxDraw.hash(seed, i)) % 1.0f;
                float spread = 1.2f + t * 5.0f;
                Vector3f p = new Vector3f(back).mul(4 + t * 28).add(head)
                        .add((FxDraw.hash(i, 3) - 0.5f) * spread, (FxDraw.hash(i, 5) - 0.5f) * spread, (FxDraw.hash(i, 9) - 0.5f) * spread);
                FxMesh.drawRock(ctx, p, 0.35f * (1.0f - t * 0.6f), FxMesh.tumble(seed + i, age, 0.4f), seed + i, 0,
                        ROCK, PLASMA, 0.9f * (1.0f - t), null, age);
            }
        }
        // loose stones jumping on the ground as the pressure arrives
        if (age >= IMPACT - 30 && age < IMPACT) {
            float p = (age - (IMPACT - 30)) / 30.0f;
            for (int i = 0; i < 16; i++) {
                float a = FxDraw.hash(seed, i + 40) * Mth.TWO_PI;
                float rr = 3 + 10 * FxDraw.hash(i + 40, seed);
                double x = target.x + Mth.cos(a) * rr, z = target.z + Mth.sin(a) * rr;
                float hop = Math.abs(Mth.sin(age * (0.6f + FxDraw.hash(i, 2) * 0.5f) + i)) * 0.4f * p;
                Vector3f stone = ctx.rel(x, FxPresets.groundY(ctx.level, x, z) + 0.12f + hop, z);
                FxMesh.drawRock(ctx, stone, 0.12f + 0.1f * FxDraw.hash(i, 6), FxMesh.tumble(seed + i, age, 0.2f * p), seed + i, 0,
                        0x55504A, PLASMA, 0.0f, null);
            }
        }
        if (age >= IMPACT) {
            Vector3f c = ctx.rel(target);
            float d = age - IMPACT;
            float cool = Mth.clamp(1.0f - d / 70.0f, 0.0f, 1.0f);
            for (int i = 0; i < EJECTA; i++) {
                Vector3f now = ejecta(c, i, d, seed, 0);
                if (now == null) continue;
                FxMesh.drawRock(ctx, now, 0.3f + 0.5f * FxDraw.hash(i, 21), FxMesh.tumble(seed + i, age, 0.35f), seed + i, 1,
                        ROCK, PLASMA, 0.4f + 0.5f * cool, null, age);
            }
        }
    }

    /** Ejected rock i, {@code back} ticks ago, on a ballistic arc; null when not in the air. */
    private static Vector3f ejecta(Vector3f c, int i, float d, int seed, float back) {
        float angle = FxDraw.hash(seed, i) * Mth.TWO_PI;
        float speed = 0.6f + FxDraw.hash(i, seed) * 0.9f;
        float up = 0.8f + FxDraw.hash(seed + 1, i) * 1.0f;
        float tau = d - FxDraw.hash(i, 11) * 4 - back;
        if (tau <= 0 || tau > 70) return null;
        Vector3f p = new Vector3f(c).add(Mth.cos(angle) * speed * tau, 1 + up * tau - 0.045f * tau * tau, Mth.sin(angle) * speed * tau);
        return p.y < c.y - 3 ? null : p;
    }

    /** Unit vector pointing back along the flight path (toward where the meteor came from). */
    private static Vector3f backward(int seed) {
        Vec3 entry = MeteorStrike.entryOffset(seed).normalize();
        return new Vector3f((float) entry.x, (float) entry.y, (float) entry.z);
    }

    // ---------------------------------------------------------------- effects

    @Override
    public void render(FxContext ctx, StrikeEntity strike, float age) {
        Vec3 target = strike.position();
        int seed = strike.getSeed();
        if (age < IMPACT) {
            float urgency = Mth.clamp((age - FALL_START) / (float) (IMPACT - FALL_START), 0.0f, 1.0f);
            FxPresets.designator(ctx, target, 6.5f, MARK, age, Math.min(1.0f, age / 15.0f), urgency);
            renderSkyBurn(ctx, ctx.rel(target.add(MeteorStrike.entryOffset(seed))), age);
            if (age >= FALL_START) renderMeteor(ctx, target, seed, age);
            if (age >= IMPACT - 30) renderPressure(ctx, target, seed, (age - (IMPACT - 30)) / 30.0f, age);
        } else {
            renderImpact(ctx, target, age - IMPACT, seed, age);
        }
    }

    /** The sky reacting: a growing burn where the meteor enters the atmosphere, then a bright point racing down. */
    private void renderSkyBurn(FxContext ctx, Vector3f entry, float age) {
        float t = Mth.clamp((age - 10) / (IMPACT - 10.0f), 0.0f, 1.0f);
        if (t <= 0) return;
        float fade = age < FALL_START ? 1.0f : 1.0f - 0.6f * MeteorStrike.fallProgress(age);
        FxDraw.sprite(ctx, true, entry, 70 + 90 * t, FIRE_DEEP, 0.22f * t * fade, 0.0f);
        FxDraw.sprite(ctx, true, entry, 20 + 30 * t, FIRE, 0.35f * t * fade, 0.0f);
        FxDraw.spriteRing(ctx, true, entry, 30 + 70 * t, 8.0f, PLASMA, 0.12f * t * fade * (0.6f + 0.4f * Mth.sin(age * 0.3f)));
    }

    private void renderMeteor(FxContext ctx, Vec3 target, int seed, float age) {
        Vec3 pos = MeteorStrike.meteorPosition(target, seed, age);
        Vector3f head = ctx.rel(pos);
        Vector3f back = backward(seed);
        float progress = MeteorStrike.fallProgress(age);

        // Smoke wake along the path it already flew: soft, lit orange near the head
        FxDraw.Puffs puffs = new FxDraw.Puffs();
        for (int k = 1; k <= 32; k++) {
            float past = age - k * 1.5f;
            if (past < FALL_START) break;
            Vector3f p = ctx.rel(MeteorStrike.meteorPosition(target, seed, past));
            float life = k / 32.0f;
            p.add((FxDraw.hash(k, seed) - 0.5f) * life * 6, (FxDraw.hash(seed, k) - 0.5f) * life * 6, 0);
            puffs.add(p, 3.0f + k * 0.5f, FxDraw.lerpColor(SMOKE_LIT, SMOKE, Math.min(1, life * 3)), 0.5f * (1.0f - life));
        }
        puffs.draw(ctx);

        // Plasma trail: long, tapering, white -> yellow -> orange, slightly turbulent
        float length = 40 + 50 * progress;
        List<Vector3f> trail = new ArrayList<>();
        float[] widths = new float[14], alphas = new float[14];
        for (int i = 0; i < 14; i++) {
            float t = i / 13.0f;
            float wobble = Mth.sin(age * 0.9f + i * 1.3f) * 0.5f * t;
            trail.add(new Vector3f(back).mul(length * t).add(head).add(wobble, wobble * 0.5f, -wobble));
            widths[i] = Mth.lerp(t, 5.0f, 0.3f);
            alphas[i] = (1.0f - t) * 0.9f;
        }
        FxDraw.ribbon(ctx, true, trail, scale(widths, 1.7f), scale(alphas, 0.4f), FIRE_DEEP);
        FxDraw.ribbon(ctx, true, trail, widths, alphas, FIRE);
        FxDraw.ribbon(ctx, true, trail, scale(widths, 0.4f), alphas, PLASMA);
        FxDraw.ribbon(ctx, true, trail.subList(0, 6), scale(widths, 0.15f), alphas, PLASMA_WHITE);

        // Bow shock: rings of compressed plasma wrapped around the rock, opening backward into a cone
        Vector3f axis = new Vector3f(back).negate();
        Vector3f u = new Vector3f(axis).cross(0, 1, 0, new Vector3f());
        if (u.lengthSquared() < 1.0e-4f) u.set(1, 0, 0);
        u.normalize();
        Vector3f v = new Vector3f(axis).cross(u, new Vector3f()).normalize();
        Vector3f nose = new Vector3f(axis).mul(MeteorStrike.METEOR_RADIUS * 1.05f).add(head);
        for (int k = 0; k < 12; k++) {
            float t = k / 11.0f;
            Vector3f ringCenter = new Vector3f(back).mul(t * 10.0f).add(nose);
            float radius = 1.2f + 4.6f * Mth.sqrt(t);
            int color = FxDraw.lerpColor(PLASMA_WHITE, FIRE, t);
            float wobble = 1.0f + 0.04f * Mth.sin(age * 1.3f + k);
            FxDraw.ring(ctx, true, ringCenter, u, v, radius * wobble, radius * wobble, 0.5f + t * 0.8f, color, (1.0f - t) * 0.7f, 28);
        }
        FxDraw.sprite(ctx, true, nose, 1.8f, PLASMA_WHITE, 0.75f, 0.0f);
        FxDraw.sprite(ctx, true, head, 10.0f, FIRE, 0.2f, 0.0f);
        // the ground below starts to glow as it comes in
        FxDraw.drapedDisc(ctx, true, target, 10 + 6 * progress, FIRE, 0.25f * progress * progress, 0.0f, 0.06f);
    }

    /** Pressure in front of the meteor: dust blown off the ground in an expanding, rising ring. */
    private void renderPressure(FxContext ctx, Vec3 target, int seed, float p, float age) {
        FxDraw.Puffs dust = new FxDraw.Puffs();
        for (int i = 0; i < 28; i++) {
            float a = Mth.TWO_PI * i / 28 + FxDraw.hash(i, seed) * 0.4f;
            float rr = 3 + 12 * p + 3 * FxDraw.hash(seed, i);
            double x = target.x + Mth.cos(a) * rr, z = target.z + Mth.sin(a) * rr;
            float size = 0.8f + 1.8f * p;
            Vector3f q = ctx.rel(x, FxPresets.groundY(ctx.level, x, z) + size * 0.4f + p * 1.5f, z);
            dust.add(q, size, DUST, 0.35f * p);
        }
        dust.draw(ctx);
    }

    private void renderImpact(FxContext ctx, Vec3 target, float d, int seed, float age) {
        Vector3f c = ctx.rel(target);
        // flash
        if (d < 8) FxDraw.sprite(ctx, true, new Vector3f(c).add(0, 4, 0), 70.0f, PLASMA_WHITE, 1.0f - d / 8.0f, 0.0f);

        // fireball hugging the ground: big soft depth, so it melts into the terrain instead of being cut by it
        if (d < 50) {
            float t = d / 50.0f;
            float soft = FxDraw.setSoftness(5.0f);
            float radius = 16.0f * FxDraw.easeOut(d / 6.0f) * (1.0f + t * 0.4f);
            Vector3f ball = new Vector3f(c).add(0, 2 + d * 0.22f, 0);
            FxPresets.fireball(ctx, ball, radius, 1.0f - t * 2, 1.0f - t, FIRE_DEEP, FIRE);
            for (int i = 0; i < 8; i++) {
                float a = i * 0.785f + d * 0.04f;
                Vector3f lobe = new Vector3f(ball).add(Mth.cos(a) * radius * 0.6f, Mth.sin(d * 0.1f + i) * radius * 0.2f, Mth.sin(a) * radius * 0.6f);
                FxDraw.sprite(ctx, true, lobe, radius * 0.45f, i % 3 == 0 ? PLASMA : FIRE, 0.3f * (1.0f - t), 0.0f);
            }
            FxDraw.setSoftness(soft);
        }
        // refracting pressure dome
        if (d < 22) FxDraw.shell(ctx, true, c, d * 3.6f, PLASMA_WHITE, 0.45f * (1.0f - d / 22.0f), false);

        // the shock front over the real terrain, with a wall of dust
        FxPresets.groundShock(ctx, target, MeteorStrike.shockRadius(d + IMPACT), MeteorStrike.MAX_RADIUS + 6, PLASMA, DUST, 1.0f, seed);

        // ejecta trails: hot at first, then smoke
        FxDraw.Puffs trails = new FxDraw.Puffs();
        float life = Mth.clamp(1.0f - d / 70.0f, 0.0f, 1.0f);
        for (int i = 0; i < EJECTA; i++) {
            Vector3f now = ejecta(c, i, d, seed, 0);
            Vector3f before = ejecta(c, i, d, seed, 3);
            if (now == null || before == null) continue;
            FxDraw.beam(ctx, true, before, now, 0.25f, FIRE, 0.0f, 0.7f * life);
            for (int k = 1; k <= 3; k++) {
                Vector3f puff = ejecta(c, i, d, seed, k * 3);
                if (puff != null) trails.add(puff, 0.5f + k * 0.35f, SMOKE, 0.35f * (1.0f - k / 4.0f));
            }
        }
        trails.draw(ctx);

        // fire plume turning into a smoke column, lit from below while the crater still glows
        float columnFade = Mth.clamp(1.0f - (d - 320) / 220.0f, 0.0f, 1.0f);
        float glow = Mth.clamp(1.0f - d / 500.0f, 0.0f, 1.0f);
        FxDraw.Puffs column = new FxDraw.Puffs();
        for (int k = 0; k < 24; k++) {
            float phase = (d * 0.35f + k * 9.0f) % 210.0f;
            if (phase > d * 1.5f) continue;
            float h = phase * 0.32f;
            float spread = 2 + phase * 0.06f;
            Vector3f p = new Vector3f(c).add((FxDraw.hash(k, seed) - 0.5f) * spread * 2 + phase * 0.03f, h + 2, (FxDraw.hash(seed, k) - 0.5f) * spread * 2);
            int rgb = FxDraw.lerpColor(SMOKE_LIT, SMOKE, Mth.clamp(h / 12.0f + (1 - glow), 0, 1));
            column.add(p, 3 + phase * 0.07f, rgb, 0.5f * columnFade * (1.0f - phase / 210.0f));
        }
        column.draw(ctx);

        // the crater floor: molten at first, cooling to a dull glow; fires flickering on the rim
        FxDraw.drapedDisc(ctx, true, target, MeteorStrike.CRATER_RADIUS * 0.85f, MOLTEN, 0.75f * glow * glow, 0.0f, 0.08f);
        FxDraw.drapedRing(ctx, true, target, MeteorStrike.CRATER_RADIUS * 0.95f, 2.5f, FIRE_DEEP, 0.35f * glow, 0.1f);
        float fires = Mth.clamp(1.0f - (d - 30) / 500.0f, 0.0f, 1.0f) * Mth.clamp((d - 20) / 10.0f, 0.0f, 1.0f);
        for (int i = 0; fires > 0 && i < 20; i++) {
            float a = FxDraw.hash(i, seed + 8) * Mth.TWO_PI;
            float rr = MeteorStrike.CRATER_RADIUS + 1 + 14 * FxDraw.hash(seed + 8, i);
            double x = target.x + Mth.cos(a) * rr, z = target.z + Mth.sin(a) * rr;
            Vector3f p = ctx.rel(x, FxPresets.groundY(ctx.level, x, z) + 0.6f, z);
            float flicker = 0.6f + 0.4f * Mth.sin(age * (0.7f + FxDraw.hash(i, 1)) + i);
            FxDraw.sprite(ctx, true, p, 1.4f, FIRE, 0.45f * flicker * fires, 0.0f);
        }
    }

    private static float[] scale(float[] values, float factor) {
        float[] out = new float[values.length];
        for (int i = 0; i < values.length; i++) out[i] = values[i] * factor;
        return out;
    }

    // ---------------------------------------------------------------- per tick: camera

    @Override
    public void tick(StrikeEntity strike, ClientLevel level, int age, RandomSource random) {
        Vec3 target = strike.position();
        if (age < FALL_START) {
            float t = age / (float) FALL_START;
            CameraFx.tint(0x401000, 0.08f * t * CameraFx.proximity(target, 220));
            CameraFx.rumbleAt(target, 0.03f * t, 160);
        }
        if (age >= FALL_START && age < IMPACT) {
            float p = MeteorStrike.fallProgress(age);
            CameraFx.rumbleAt(target, 0.05f + 0.2f * p, 220);
            CameraFx.tint(0xFF5010, (0.08f + 0.16f * p) * CameraFx.proximity(target, 220));
            CameraFx.fovSqueeze(0.06f * p * CameraFx.proximity(target, 150));
        }
        if (age == IMPACT) {
            CameraFx.flashSeen(target.add(0, 3, 0), 0xFFE0B0, 0.85f, 260, 16);
            CameraFx.fovKickAt(target, 0.18f, 200);
            CameraFx.shakeAt(target, 1.0f, 200);
        }
        if (age > IMPACT && age < IMPACT + 60) {
            CameraFx.rumbleAt(target, 0.16f * (1.0f - (age - IMPACT) / 60.0f), 150);
        }
        // the shock front reaching the player
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && age > IMPACT) {
            double distance = mc.player.position().distanceTo(target);
            float front = MeteorStrike.shockRadius(age), previous = MeteorStrike.shockRadius(age - 1);
            if (distance > 6 && distance <= front && distance > previous && distance < MeteorStrike.MAX_RADIUS + 20) {
                float strength = (float) (1.0 - distance / (MeteorStrike.MAX_RADIUS + 20));
                CameraFx.shake(0.25f + 0.6f * strength);
                CameraFx.fovKick(-0.08f * strength);
                CameraFx.flash(0xB89A78, 0.25f * strength, 16);
            }
        }
    }
}
