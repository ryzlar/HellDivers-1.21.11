package net.ryzlar.strike.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.ryzlar.fx.CameraFx;
import net.ryzlar.fx.FxContext;
import net.ryzlar.fx.FxDraw;
import net.ryzlar.fx.FxMesh;
import net.ryzlar.fx.FxPresets;
import net.ryzlar.renderer.LaserTimerRenderer;
import net.ryzlar.sound.ModSounds;
import net.ryzlar.strike.NuclearStrike;
import net.ryzlar.strike.StrikeEntity;
import net.ryzlar.strike.StrikeVisual;
import org.joml.Vector3f;

/**
 * Nuclear Strike, the heaviest event in the mod.
 * <ul>
 *     <li><b>Countdown</b>: designator rings on the terrain, an alarm pulse rolling over the ground, a beam into
 *     orbit, the world going dark and quiet; the warhead streaks down through the last second and a half.</li>
 *     <li><b>Detonation</b>: a sky-wide flash (blinding only if you can see it), a boiling 3D fireball that melts into
 *     the ground it sits on, a refracting pressure dome, debris on ballistic arcs.</li>
 *     <li><b>Blast wave</b>: a bright front and a rolling wall of dust following the real terrain, a heat-shimmer band
 *     behind it, a reflected second wave; when it reaches you: shake, a gust of dust and its own sound.</li>
 *     <li><b>Mushroom cloud</b>: a rising fireball feeding a rolling cap that is lit from below, condensation skirts
 *     around the stem, an ice cap above it early on, a base surge; for a minute it climbs, spreads and drifts apart.</li>
 *     <li><b>Aftermath</b>: fires across the burnt ground with their own smoke, a glowing crater with heat haze, a dust
 *     haze over the whole area, falling ash; everything thins out and clears.</li>
 * </ul>
 */
public class NuclearVisual implements StrikeVisual {

    private static final int HAZARD = 0xF2FF3D;
    private static final int ALARM = 0xFF3B2F;
    private static final int FIRE_HOT = 0xFFF2C0;
    private static final int FIRE = 0xFF9A2E;
    private static final int FIRE_DEEP = 0xFF4A10;
    private static final int SMOKE_YOUNG = 0x4A3C34;
    private static final int SMOKE_OLD = 0x8A8580;
    private static final int SMOKE_LIT = 0xB0603A;
    private static final int CONDENSATION = 0xF2F4F6;
    private static final int DUST = 0x8C7A62;
    private static final int ROCK = 0x2E2724;

    private static final int DET = NuclearStrike.DETONATION;
    private static final int DEBRIS = 60;

    // ---------------------------------------------------------------- distortion

    @Override
    public void renderDistortion(FxContext ctx, StrikeEntity strike, float age) {
        if (age < DET) return;
        Vec3 pos = strike.position();
        Vector3f c = ctx.rel(pos);
        float d = age - DET;
        if (d < 30) FxDraw.distortion(ctx, new Vector3f(c).add(0, 10, 0), 10 + d * 4.2f, 0.9f, 0.4f, 0.0f, 1.0f - d / 30.0f);
        // heat shimmer riding just behind the blast wave
        float front = NuclearStrike.shockRadius(age);
        if (front > 4 && front < NuclearStrike.MAX_RADIUS + 20) {
            float fade = 1.0f - front / (NuclearStrike.MAX_RADIUS + 20);
            int count = 28;
            for (int i = 0; i < count; i++) {
                float a = Mth.TWO_PI * i / count;
                double x = pos.x + Mth.cos(a) * (front - 3), z = pos.z + Mth.sin(a) * (front - 3);
                Vector3f p = ctx.rel(x, FxPresets.groundY(ctx.level, x, z) + 3, z);
                FxDraw.distortion(ctx, p, 4 + front * 0.05f, -0.3f, 0.8f, 0.0f, 0.7f * fade);
            }
        }
        // the fireball and the crater shimmer with heat for a long time
        float ball = NuclearStrike.fireballRadius(age);
        float heat = Mth.clamp(1.0f - d / 200.0f, 0.0f, 1.0f);
        FxDraw.heatHaze(ctx, new Vector3f(c).add(0, NuclearStrike.fireballHeight(age) - ball, 0), ball * 1.1f, ball * 2.0f, 0.6f * heat);
        FxDraw.heatHaze(ctx, c, 14.0f, 22.0f, 0.6f * Mth.clamp(1.0f - d / 900.0f, 0.0f, 1.0f));
    }

    // ---------------------------------------------------------------- solid: debris

    @Override
    public void renderSolid(FxContext ctx, StrikeEntity strike, float age) {
        if (age < DET) return;
        float d = age - DET;
        if (d > 110) return;
        Vector3f c = ctx.rel(strike.position());
        int seed = strike.getSeed();
        float cool = Mth.clamp(1.0f - d / 80.0f, 0.0f, 1.0f);
        for (int i = 0; i < DEBRIS; i++) {
            Vector3f p = debris(c, i, d, seed, 0);
            if (p == null) continue;
            FxMesh.drawRock(ctx, p, 0.5f + 1.1f * FxDraw.hash(i, 31), FxMesh.tumble(seed + i, age, 0.3f), seed + i, 1,
                    ROCK, FIRE, 0.3f + 0.6f * cool, null, age);
        }
    }

    private static Vector3f debris(Vector3f c, int i, float d, int seed, float back) {
        float tau = d - FxDraw.hash(i, 3) * 6 - back;
        if (tau <= 0) return null;
        float angle = FxDraw.hash(seed, i) * Mth.TWO_PI;
        float speed = 1.0f + FxDraw.hash(i, seed) * 1.6f;
        float up = 1.2f + FxDraw.hash(seed + 5, i) * 1.6f;
        Vector3f p = new Vector3f(c).add(Mth.cos(angle) * speed * tau, 2 + up * tau - 0.04f * tau * tau, Mth.sin(angle) * speed * tau);
        return p.y < c.y - 4 ? null : p;
    }

    // ---------------------------------------------------------------- effects

    @Override
    public void render(FxContext ctx, StrikeEntity strike, float age) {
        Vec3 pos = strike.position();
        Vector3f c = ctx.rel(pos);
        int seed = strike.getSeed();
        if (age < DET) {
            renderCountdown(ctx, pos, c, age);
            return;
        }
        float d = age - DET;
        float fade = Mth.clamp((NuclearStrike.DURATION - age) / 300.0f, 0.0f, 1.0f);

        renderAftermath(ctx, pos, c, d, fade, seed, age);
        renderMushroom(ctx, c, d, fade, seed);
        if (d < 110) {
            FxDraw.Puffs trails = new FxDraw.Puffs();
            float hot = Mth.clamp(1.0f - d / 80.0f, 0.0f, 1.0f);
            for (int i = 0; i < DEBRIS; i++) {
                Vector3f now = debris(c, i, d, seed, 0), before = debris(c, i, d, seed, 5);
                if (now == null || before == null) continue;
                FxDraw.beam(ctx, true, before, now, 0.4f, FIRE_DEEP, 0.0f, 0.6f * hot);
                for (int k = 2; k <= 8; k += 3) {
                    Vector3f puff = debris(c, i, d, seed, k * 2);
                    if (puff != null) trails.add(puff, 0.8f + k * 0.2f, SMOKE_YOUNG, 0.35f * (1.0f - k / 10.0f));
                }
            }
            trails.draw(ctx);
        }
        renderFireball(ctx, c, d, age, seed);
        renderBlast(ctx, pos, c, d, seed);
    }

    // ---------------------------------------------------------------- countdown

    private void renderCountdown(FxContext ctx, Vec3 pos, Vector3f c, float age) {
        float p = age / DET;
        float blink = 0.5f + 0.5f * Mth.sin(age * (0.25f + p * 1.1f));
        int color = FxDraw.lerpColor(HAZARD, ALARM, p * blink);
        FxPresets.designator(ctx, pos, 7.0f, color, age, Math.min(1.0f, age / 15.0f), p);
        FxPresets.designator(ctx, pos, 14.0f, ALARM, age * 0.6f, 0.35f * p, p);
        // an alarm pulse rolling out over the ground every second
        float pulse = (age % 20) / 20.0f;
        FxDraw.drapedRing(ctx, true, pos, 4 + pulse * 40, 0.3f, ALARM, 0.5f * (1.0f - pulse) * Math.min(1.0f, age / 15.0f), 0.1f);

        // Designation beam reaching into orbit
        FxDraw.beam(ctx, true, c, new Vector3f(c).add(0, 240, 0), 0.25f + 0.25f * p, color, 0.55f * blink, 0.0f);

        // The warhead streaking down in the last second and a half
        float fall = (age - (DET - 30)) / 30.0f;
        if (fall > 0) {
            float y = 240.0f * (1.0f - fall * fall);
            Vector3f head = new Vector3f(c).add(0, y, 0);
            FxDraw.ribbon(ctx, true, java.util.List.of(head, new Vector3f(head).add(0, 40, 0)), new float[]{1.6f, 0.2f},
                    new float[]{0.8f, 0.0f}, FIRE);
            FxDraw.beam(ctx, true, head, new Vector3f(head).add(0, 25, 0), 0.5f, FIRE_HOT, 0.9f, 0.0f);
            FxDraw.sprite(ctx, true, head, 3.0f, FIRE_HOT, 1.0f, 0.0f);
            FxDraw.spriteRing(ctx, true, head, 2.2f, 0.6f, FIRE, 0.6f);
        }
    }

    @Override
    public void renderUi(FxContext ctx, StrikeEntity strike, float age) {
        if (age >= DET) return;
        float visibility = Math.min(1.0f, age / 10.0f);
        Vec3 anchor = strike.position().add(0, 5, 0);
        LaserTimerRenderer.drawPanel(ctx.poseStack, ctx.buffers, ctx.camera, anchor, 1.0f,
                Component.translatable("strategem.lasermod.nuke_countdown").getString(),
                (DET - age) / 20.0f, 1.0f - age / DET, ALARM, visibility, age / 20.0f);
    }

    // ---------------------------------------------------------------- detonation

    private void renderFireball(FxContext ctx, Vector3f c, float d, float age, int seed) {
        // sky-wide flash
        if (d < 12) FxDraw.sprite(ctx, true, new Vector3f(c).add(0, 10, 0), 180.0f, 0xFFFFFF, 1.0f - d / 12.0f, 0.0f);
        if (d > 170) return;
        float radius = NuclearStrike.fireballRadius(age);
        Vector3f center = new Vector3f(c).add(0, NuclearStrike.fireballHeight(age), 0);
        float heat = Mth.clamp(1.0f - d / 50.0f, 0.0f, 1.0f);
        float alpha = Mth.clamp(1.0f - (d - 70) / 100.0f, 0.0f, 1.0f);
        // the boiling body, its glow, and a white-hot core while it is young
        FxMesh.fireball(ctx, center, radius, Math.max(0.15f, heat), alpha, age, seed);
        float soft = FxDraw.setSoftness(radius * 0.4f);
        FxDraw.sprite(ctx, true, center, radius * 1.6f, FIRE_DEEP, 0.3f * alpha, 0.0f);
        if (heat > 0.05f) FxDraw.sprite(ctx, true, center, radius * 0.6f, FIRE_HOT, heat * alpha, 0.0f);
        FxDraw.setSoftness(soft);
        // as it cools, smoke wraps it from the outside in
        float smoke = Mth.clamp((d - 30) / 80.0f, 0.0f, 1.0f) * alpha;
        if (smoke > 0) {
            FxDraw.Puffs wrap = new FxDraw.Puffs();
            for (int i = 0; i < 16; i++) {
                float a = Mth.TWO_PI * i / 16 + d * 0.01f;
                float y = (FxDraw.hash(i, seed) - 0.5f) * radius;
                Vector3f p = new Vector3f(center).add(Mth.cos(a) * radius * 0.9f, y, Mth.sin(a) * radius * 0.9f);
                wrap.add(p, radius * 0.5f, SMOKE_YOUNG, 0.45f * smoke);
            }
            wrap.draw(ctx);
        }
    }

    private void renderBlast(FxContext ctx, Vec3 pos, Vector3f c, float d, int seed) {
        float front = d * NuclearStrike.SHOCKWAVE_SPEED;
        FxPresets.groundShock(ctx, pos, front, NuclearStrike.MAX_RADIUS + 20, 0xFFE2B0, DUST, 1.4f, seed);
        // a fainter reflected wave
        FxPresets.groundShock(ctx, pos, front * 0.72f, NuclearStrike.MAX_RADIUS, 0xFFB070, DUST, 0.6f, seed + 1);

        // pressure dome
        if (d < 45) FxDraw.shell(ctx, true, c, d * 4.2f, 0xFFF4E0, 0.55f * (1.0f - d / 45.0f), false);
        // Wilson condensation ring around the stem
        if (d > 12 && d < 120) {
            float t = (d - 12) / 108.0f;
            Vector3f ring = new Vector3f(c).add(0, 34 + d * 0.3f, 0);
            float a = Mth.sin(t * Mth.PI) * 0.45f;
            FxDraw.ring(ctx, false, ring, new Vector3f(1, 0, 0), new Vector3f(0, 0, 1), 22 + d * 0.55f, 22 + d * 0.55f,
                    6.0f, CONDENSATION, a, 64);
        }
    }

    // ---------------------------------------------------------------- mushroom cloud

    private void renderMushroom(FxContext ctx, Vector3f c, float d, float fade, int seed) {
        if (d < 8 || fade <= 0) return;
        float g = FxDraw.easeOut((d - 8) / 340.0f);
        // late: it stops climbing and spreads apart
        float disperse = Mth.clamp((d - 700) / 500.0f, 0.0f, 1.0f);
        float height = 20 + 128 * g;
        float stemRadius = (6 + 7 * g) * (1.0f + disperse * 0.6f);
        float capMajor = (14 + 32 * g) * (1.0f + disperse * 0.5f);
        float capMinor = (9 + 10 * g) * (1.0f + disperse * 0.4f);
        float heat = Mth.clamp(1.0f - (d - 8) / 280.0f, 0.0f, 1.0f);
        int smoke = FxDraw.lerpColor(SMOKE_YOUNG, SMOKE_OLD, Mth.clamp(d / 600.0f, 0.0f, 1.0f));
        float alpha = 0.6f * fade * Mth.clamp((d - 8) / 20.0f, 0.0f, 1.0f) * (1.0f - 0.6f * disperse);
        float drift = disperse * 25.0f;
        Vector3f capCenter = new Vector3f(c).add(drift, height, drift * 0.4f);

        FxDraw.Puffs puffs = new FxDraw.Puffs();

        // Stem: flared at the base, wobbling, glowing low down while the fire still feeds it
        int stemPuffs = 26;
        for (int i = 0; i < stemPuffs; i++) {
            float t = i / (float) (stemPuffs - 1);
            float y = t * (height - capMinor * 0.4f);
            float flare = 1.0f + 1.6f * (1.0f - t) * (1.0f - t) * (1.0f - t);
            float wobble = (FxDraw.hash(seed, i) - 0.5f) * stemRadius * 0.6f;
            float a = d * 0.01f + i * 1.7f;
            Vector3f p = new Vector3f(c).add(Mth.cos(a) * wobble + drift * t, y, Mth.sin(a) * wobble + drift * 0.4f * t);
            int rgb = FxDraw.lerpColor(smoke, SMOKE_LIT, heat * (1.0f - t) * 0.7f);
            puffs.add(p, stemRadius * flare * (0.85f + 0.3f * FxDraw.hash(i, seed)), rgb, alpha * (1.0f - disperse * t));
        }

        // Cap: a rolling torus, closed on top, its underside lit by the fire
        int around = 30, minor = 5;
        for (int i = 0; i < around; i++) {
            float a = Mth.TWO_PI * i / around + d * 0.0015f;
            for (int j = 0; j < minor; j++) {
                float phi = Mth.TWO_PI * j / minor + d * 0.012f; // rolls outward like a real cap
                float ring = capMajor + capMinor * Mth.cos(phi);
                float lowness = Mth.clamp(-Mth.sin(phi), 0.0f, 1.0f);
                Vector3f p = new Vector3f(capCenter).add(Mth.cos(a) * ring, capMinor * 0.75f * Mth.sin(phi), Mth.sin(a) * ring);
                int rgb = FxDraw.lerpColor(smoke, SMOKE_LIT, heat * lowness * 0.8f);
                puffs.add(p, capMinor * (0.75f + 0.25f * FxDraw.hash(i * 7 + j, seed)), rgb, alpha);
            }
        }
        for (int i = 0; i < 12; i++) {
            float a = Mth.TWO_PI * i / 12;
            Vector3f p = new Vector3f(capCenter).add(Mth.cos(a) * capMajor * 0.5f, capMinor * 0.7f, Mth.sin(a) * capMajor * 0.5f);
            puffs.add(p, capMinor * 1.1f, smoke, alpha);
        }
        puffs.add(new Vector3f(capCenter).add(0, capMinor, 0), capMinor * 1.3f, smoke, alpha);

        // Condensation: an ice cap above the rising cloud early on, skirts around the stem
        if (d > 15 && d < 160) {
            float t = (d - 15) / 145.0f;
            float a = Mth.sin(t * Mth.PI) * 0.4f * fade;
            for (int i = 0; i < 16; i++) {
                float ang = Mth.TWO_PI * i / 16;
                Vector3f p = new Vector3f(capCenter).add(Mth.cos(ang) * capMajor * 0.7f, capMinor * 1.5f, Mth.sin(ang) * capMajor * 0.7f);
                puffs.add(p, capMinor * 0.55f, CONDENSATION, a);
            }
        }
        if (d > 30 && d < 260) {
            float t = (d - 30) / 230.0f;
            float a = Mth.sin(t * Mth.PI) * 0.35f * fade;
            for (int ring = 0; ring < 2; ring++) {
                float y = height * (0.4f + ring * 0.2f);
                for (int i = 0; i < 14; i++) {
                    float ang = Mth.TWO_PI * i / 14 + ring;
                    float rr = stemRadius * (1.8f + ring * 0.4f);
                    puffs.add(new Vector3f(c).add(Mth.cos(ang) * rr + drift * 0.5f, y, Mth.sin(ang) * rr), stemRadius * 0.5f, CONDENSATION, a);
                }
            }
        }

        // Base surge cloud
        float baseRadius = Math.min(75.0f, d * NuclearStrike.SHOCKWAVE_SPEED * 0.5f);
        for (int i = 0; i < 28; i++) {
            float a = Mth.TWO_PI * i / 28 + FxDraw.hash(i, seed) * 0.3f;
            Vector3f p = new Vector3f(c).add(Mth.cos(a) * baseRadius, 3, Mth.sin(a) * baseRadius);
            puffs.add(p, 9 + 4 * FxDraw.hash(seed, i), DUST, alpha * 0.8f * Mth.clamp(1.0f - d / 700.0f, 0.0f, 1.0f));
        }
        puffs.draw(ctx);

        // Fire still glowing inside the cloud
        if (heat > 0.01f) {
            for (int i = 0; i < around; i += 2) {
                float a = Mth.TWO_PI * i / around + d * 0.0015f;
                Vector3f p = new Vector3f(capCenter).add(Mth.cos(a) * capMajor, -capMinor * 0.3f, Mth.sin(a) * capMajor);
                FxDraw.sprite(ctx, true, p, capMinor * 0.9f, FIRE_DEEP, 0.4f * heat * fade, 0.0f);
            }
            FxDraw.sprite(ctx, true, capCenter, capMajor * 0.9f, FIRE, 0.45f * heat * fade, 0.0f);
            FxDraw.beam(ctx, true, new Vector3f(c).add(0, 2, 0), capCenter, stemRadius * 0.9f, FIRE_DEEP, 0.5f * heat, 0.25f * heat);
        }
    }

    // ---------------------------------------------------------------- aftermath

    private void renderAftermath(FxContext ctx, Vec3 pos, Vector3f c, float d, float fade, int seed, float age) {
        // the crater glowing, then cooling
        float glow = Mth.clamp(1.0f - d / 500.0f, 0.0f, 1.0f);
        FxDraw.drapedDisc(ctx, true, pos, NuclearStrike.CRATER_RADIUS * 1.2f, FIRE_DEEP, 0.55f * glow, 0.0f, 0.1f);
        // fires across the burnt ground, each with its own thin smoke
        float fires = Mth.clamp((d - 14) / 20.0f, 0.0f, 1.0f) * Mth.clamp(1.0f - (d - 400) / 700.0f, 0.0f, 1.0f) * fade;
        if (fires > 0) {
            FxDraw.Puffs smoke = new FxDraw.Puffs();
            for (int i = 0; i < 40; i++) {
                float a = FxDraw.hash(i, seed + 12) * Mth.TWO_PI;
                float rr = NuclearStrike.CRATER_RADIUS + 2 + 60 * FxDraw.hash(seed + 12, i);
                double x = pos.x + Mth.cos(a) * rr, z = pos.z + Mth.sin(a) * rr;
                Vector3f p = ctx.rel(x, FxPresets.groundY(ctx.level, x, z) + 0.8f, z);
                float flicker = 0.6f + 0.4f * Mth.sin(age * (0.6f + FxDraw.hash(i, 3)) + i);
                FxDraw.sprite(ctx, true, p, 1.6f, FIRE, 0.5f * flicker * fires, 0.0f);
                if (i % 4 == 0) {
                    float rise = (d * 0.1f + i) % 14.0f;
                    smoke.add(new Vector3f(p).add(rise * 0.3f, 1 + rise, 0), 1.2f + rise * 0.35f, SMOKE_YOUNG, 0.35f * fires * (1.0f - rise / 14.0f));
                }
            }
            smoke.draw(ctx);
        }
        // a dust haze hanging over the whole blast zone, thinning out
        float haze = Mth.clamp((d - 40) / 120.0f, 0.0f, 1.0f) * Mth.clamp(1.0f - (d - 500) / 700.0f, 0.0f, 1.0f) * fade;
        if (haze > 0) {
            float soft = FxDraw.setSoftness(20.0f);
            FxDraw.sprite(ctx, false, new Vector3f(c).add(0, 10, 0), 120.0f, 0x6A5E50, 0.28f * haze, 0.0f);
            FxDraw.setSoftness(soft);
        }
    }

    // ---------------------------------------------------------------- per tick

    @Override
    public void tick(StrikeEntity strike, ClientLevel level, int age, RandomSource random) {
        Vec3 pos = strike.position();
        if (age < DET) {
            float p = age / (float) DET;
            // The world goes quiet and dark, the view tightens
            CameraFx.tint(0x000000, 0.45f * p * CameraFx.proximity(pos, 260));
            CameraFx.fovSqueeze(0.1f * p * CameraFx.proximity(pos, 260));
            if (age > DET - 60) CameraFx.rumbleAt(pos, 0.04f + 0.1f * (age - (DET - 60)) / 60.0f, 220);
            return;
        }
        if (age == DET) {
            CameraFx.flashSeen(pos.add(0, NuclearStrike.FIREBALL_ALTITUDE, 0), 0xFFFFFF, 1.0f, 450, 60);
            CameraFx.fovKickAt(pos, 0.3f, 400);
            CameraFx.shakeAt(pos, 0.35f, 400);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            double distance = mc.player.position().distanceTo(pos);
            // The blast wave reaching the player: felt and heard when it arrives, not when the bomb goes off
            int arrival = DET + (int) (distance / NuclearStrike.SHOCKWAVE_SPEED);
            if (age == arrival && distance < 320) {
                float strength = (float) (1.0 - distance / 320.0);
                CameraFx.shake(0.4f + 1.0f * strength);
                CameraFx.fovKick(-0.12f * strength);
                CameraFx.flash(0xC8B090, 0.35f * strength, 25);
                Vec3 me = mc.player.position();
                level.playLocalSound(me.x, me.y, me.z, ModSounds.NUKE_SHOCKWAVE, SoundSource.PLAYERS, 0.4f + 0.8f * strength, 1.0f, false);
            }
            // Ash raining down near ground zero
            if (age > DET + 40 && age < NuclearStrike.DURATION - 100 && distance < 220) {
                Vec3 me = mc.player.position();
                for (int i = 0; i < 5; i++) {
                    level.addParticle(i % 3 == 0 ? ParticleTypes.WHITE_ASH : ParticleTypes.ASH,
                            me.x + random.nextGaussian() * 14, me.y + 6 + random.nextDouble() * 10, me.z + random.nextGaussian() * 14,
                            0, -0.02, 0);
                }
            }
        }
        if (age < DET + 400) CameraFx.rumbleAt(pos, 0.1f * (1.0f - (age - DET) / 400.0f), 260);
        float haze = Mth.clamp((age - DET - 40) / 120.0f, 0.0f, 1.0f) * Mth.clamp(1.0f - (age - DET - 500) / 700.0f, 0.0f, 1.0f);
        CameraFx.tint(0x3A2A1A, 0.2f * haze * CameraFx.proximity(pos, 160));
    }
}
