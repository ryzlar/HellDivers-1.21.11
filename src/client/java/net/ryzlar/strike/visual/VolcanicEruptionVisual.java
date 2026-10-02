package net.ryzlar.strike.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.ryzlar.fx.CameraFx;
import net.ryzlar.fx.FxContext;
import net.ryzlar.fx.FxDraw;
import net.ryzlar.fx.FxMesh;
import net.ryzlar.fx.FxPresets;
import net.ryzlar.strike.StrikeEntity;
import net.ryzlar.strike.StrikeVisual;
import net.ryzlar.strike.VolcanicEruptionStrike;
import net.ryzlar.strike.VolcanicEruptionStrike.CrackPoint;
import net.ryzlar.strike.VolcanicEruptionStrike.Vent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Volcanic Eruption: the ground itself opens into the furnace below.
 * <ul>
 *     <li>Cracks are drawn as recessed trenches (walls projected onto the ground, so you look <i>into</i> them),
 *     dull red at first, white-hot later. They sit on the real magma cracks the server cuts.</li>
 *     <li>The chasm is real: the server caves the ground in over a few seconds while chunks lift off. The client
 *     lights its walls from below, puts a churning lava surface at the bottom and sends embers and smoke up.</li>
 *     <li>Side vents throw glowing 3D rocks; the eruption drives a flowing 3D lava column, 3D lava bombs, an ash
 *     plume with volcanic lightning and a pyroclastic surge.</li>
 * </ul>
 */
public class VolcanicEruptionVisual implements StrikeVisual {

    private static final int LAVA_WHITE = 0xFFF4C8;
    private static final int LAVA_CORE = 0xFFD45A;
    private static final int LAVA = 0xFF8A1E;
    private static final int MAGMA = 0xFF3C0A;
    private static final int ROCK = 0x2A2220;
    private static final int TRENCH_RIM = 0x140602;
    private static final int CRUST = 0x1C0F0A;
    private static final int ASH = 0x2E2A28;
    private static final int ASH_LIGHT = 0x504945;
    private static final int STEAM = 0xBDB8B2;
    private static final int SURGE = 0x3E3632;
    private static final int ASH_LIT = 0x7A3A1C;

    private static final int OPENING = VolcanicEruptionStrike.OPENING;
    private static final int BUILDUP = VolcanicEruptionStrike.BUILDUP;
    private static final int ERUPTION = VolcanicEruptionStrike.ERUPTION;
    private static final int ERUPTION_END = VolcanicEruptionStrike.ERUPTION_END;
    private static final int PIT_RADIUS = VolcanicEruptionStrike.CALDERA_RADIUS;
    private static final int PIT_DEPTH = VolcanicEruptionStrike.PIT_DEPTH;

    private final Map<Integer, List<List<CrackPoint>>> crackCache = new HashMap<>();
    private final Map<Integer, List<Vent>> ventCache = new HashMap<>();

    private List<List<CrackPoint>> cracks(int seed) {
        if (crackCache.size() > 16) crackCache.clear();
        return crackCache.computeIfAbsent(seed, VolcanicEruptionStrike::cracks);
    }

    private List<Vent> vents(int seed) {
        if (ventCache.size() > 16) ventCache.clear();
        return ventCache.computeIfAbsent(seed, VolcanicEruptionStrike::vents);
    }

    /** Eruption intensity with its pulses, the same timeline the server uses for bombs, damage and sound. */
    private static float power(float age) {
        return VolcanicEruptionStrike.power(age);
    }

    // ---------------------------------------------------------------- distortion: heat

    @Override
    public void renderDistortion(FxContext ctx, StrikeEntity strike, float age) {
        Vec3 base = strike.position();
        Vector3f c = ctx.rel(base);
        int seed = strike.getSeed();
        float heat = heat(age) * Mth.clamp((VolcanicEruptionStrike.DURATION - age) / 200.0f, 0.0f, 1.0f);
        if (heat <= 0.01f) return;
        // heat rising out of the cracks
        int n = 0;
        for (List<CrackPoint> crack : cracks(seed)) {
            for (int i = 2; i < crack.size(); i += 5) {
                CrackPoint p = crack.get(i);
                if (VolcanicEruptionStrike.appearTick(p) > age || ++n > 40) continue;
                double x = base.x + p.x(), z = base.z + p.z();
                FxDraw.distortion(ctx, ctx.rel(x, FxPresets.groundY(ctx.level, x, z) + 1.2f, z), 1.4f, 0.0f, 1.0f, 0.0f, 0.5f * heat);
            }
        }
        if (age >= OPENING) FxDraw.heatHaze(ctx, c, pitRadius(age) * 0.8f + 1, 16.0f, 0.8f * heat);
        float power = power(age);
        if (power > 0.01f) FxDraw.heatHaze(ctx, c, 6.0f, VolcanicEruptionStrike.fountainHeight(age) + 8, 0.7f * power);
        if (age >= ERUPTION && age < ERUPTION + 24) {
            float d = age - ERUPTION;
            FxDraw.distortion(ctx, new Vector3f(c).add(0, 4, 0), 4 + d * 3.0f, 0.8f, 0.4f, 0.0f, 1.0f - d / 24.0f);
        }
    }

    /** How hot the ground is: rises through the build-up, peaks at the eruption, cools after. */
    private static float heat(float age) {
        float rise = Mth.clamp((age - VolcanicEruptionStrike.CRACKS_START) / (float) (ERUPTION - VolcanicEruptionStrike.CRACKS_START), 0.0f, 1.0f);
        float cool = age > ERUPTION_END ? Math.max(0.15f, 1.0f - (age - ERUPTION_END) / 260.0f) : 1.0f;
        return rise * cool;
    }

    /** Chasm radius: grows while the ground caves in. */
    private static float pitRadius(float age) {
        return PIT_RADIUS * FxDraw.smooth((age - OPENING) / (float) (ERUPTION - OPENING));
    }

    // ---------------------------------------------------------------- solid 3D objects

    @Override
    public void renderSolid(FxContext ctx, StrikeEntity strike, float age) {
        Vec3 base = strike.position();
        Vector3f c = ctx.rel(base);
        int seed = strike.getSeed();

        // Loose stones hopping on the trembling ground before the eruption
        if (age > 10 && age < ERUPTION) {
            float tremble = Mth.clamp(age / (float) ERUPTION, 0.0f, 1.0f);
            for (int i = 0; i < 22; i++) {
                float a = FxDraw.hash(seed, i + 500) * Mth.TWO_PI;
                float rr = 4 + 22 * FxDraw.hash(i + 500, seed);
                if (age >= OPENING && rr < pitRadius(age) + 1) continue;
                double x = base.x + Mth.cos(a) * rr, z = base.z + Mth.sin(a) * rr;
                float hop = Math.max(0.0f, Mth.sin(age * (0.45f + 0.3f * FxDraw.hash(i, 4)) + i)) * 0.35f * tremble;
                Vector3f stone = ctx.rel(x, FxPresets.groundY(ctx.level, x, z) + 0.1f + hop, z);
                FxMesh.drawRock(ctx, stone, 0.1f + 0.12f * FxDraw.hash(i, 6), FxMesh.tumble(seed + i, age, 0.15f * tremble),
                        seed + i, 0, 0x4A4440, LAVA, 0.0f, null);
            }
        }

        // Side vents throw glowing rocks
        for (Vent vent : vents(seed)) {
            float d = age - vent.tick();
            if (d < 0 || d > 34) continue;
            double x = base.x + vent.x(), z = base.z + vent.z();
            Vector3f p = ctx.rel(x, FxPresets.groundY(ctx.level, x, z), z);
            for (int i = 0; i < 7; i++) {
                float tau = d - FxDraw.hash(vent.tick(), i) * 6;
                if (tau <= 0) continue;
                float angle = FxDraw.hash(i, vent.tick()) * Mth.TWO_PI;
                float speed = 0.08f + FxDraw.hash(seed, i + vent.tick()) * 0.18f;
                Vector3f rock = new Vector3f(p).add(Mth.cos(angle) * speed * tau, 0.65f * tau - 0.045f * tau * tau, Mth.sin(angle) * speed * tau);
                if (rock.y < p.y - 0.3f) continue;
                FxMesh.drawRock(ctx, rock, 0.18f + 0.12f * FxDraw.hash(i, 3), FxMesh.tumble(i + vent.tick(), age, 0.3f),
                        i + vent.tick(), 0, ROCK, LAVA_CORE, 0.85f, null);
            }
        }

        // Chunks of ground lifted by the heat while the chasm opens, then thrown out by the eruption
        if (age >= OPENING && age < ERUPTION + 70) {
            for (int i = 0; i < 26; i++) {
                float angle = FxDraw.hash(seed, i + 300) * Mth.TWO_PI;
                float dist = 3 + FxDraw.hash(i + 300, seed) * (PIT_RADIUS + 3);
                float start = OPENING + (dist / (PIT_RADIUS + 6)) * (ERUPTION - OPENING) * 0.8f; // inner ones lift first
                float lift = age - start;
                if (lift < 0) continue;
                Vector3f p;
                if (age < ERUPTION) {
                    float rise = Math.min(4.5f, lift * 0.06f) + Mth.sin(age * 0.2f + i) * 0.15f;
                    p = new Vector3f(c).add(Mth.cos(angle) * dist, rise, Mth.sin(angle) * dist);
                } else {
                    float tau = age - ERUPTION;
                    float rise = Math.min(4.5f, (ERUPTION - start) * 0.06f);
                    float speed = 0.5f + FxDraw.hash(i, 9) * 0.6f;
                    p = new Vector3f(c).add(Mth.cos(angle) * (dist + speed * tau), rise + 1.3f * tau - 0.05f * tau * tau, Mth.sin(angle) * (dist + speed * tau));
                    if (p.y < c.y - 2) continue;
                }
                float size = 0.35f + FxDraw.hash(i, 12) * 0.55f;
                FxMesh.drawRock(ctx, p, size, FxMesh.tumble(seed + i, age, age < ERUPTION ? 0.02f : 0.25f), seed + i, 1,
                        ROCK, LAVA, 0.6f * heat(age), new Vector3f(0, -1, 0));
            }
        }

        // Lava bombs during the eruption
        float power = power(age);
        if (power > 0.02f) {
            for (int k = 0; k < 46; k++) {
                Vector3f bomb = bomb(c, k, age - ERUPTION, power, seed, 0);
                if (bomb == null) continue;
                FxMesh.drawRock(ctx, bomb, 0.35f + 0.35f * FxDraw.hash(k, 5), FxMesh.tumble(seed + k * 7, age, 0.4f),
                        seed + k, 1, ROCK, LAVA_CORE, 0.9f, null);
            }
        }
    }

    /** Position of lava bomb k, {@code back} ticks ago; bombs are relaunched continuously. Null when not airborne. */
    private static Vector3f bomb(Vector3f c, int k, float d, float power, int seed, float back) {
        float period = 50.0f;
        float shifted = d + k * 7.3f;
        int cycle = (int) (shifted / period);
        float tau = shifted % period - back;
        if (tau <= 0 || d - back < 0) return null;
        float angle = FxDraw.hash(k, cycle + seed) * Mth.TWO_PI;
        float speed = (0.3f + FxDraw.hash(cycle, k) * 1.1f) * (0.4f + 0.6f * power);
        float up = 1.1f + FxDraw.hash(k + 99, cycle) * 1.0f;
        Vector3f p = new Vector3f(c).add(Mth.cos(angle) * speed * tau, 4 + up * tau - 0.05f * tau * tau, Mth.sin(angle) * speed * tau);
        return p.y < c.y - 1 ? null : p;
    }

    // ---------------------------------------------------------------- everything else

    @Override
    public void render(FxContext ctx, StrikeEntity strike, float age) {
        Vec3 base = strike.position();
        Vector3f c = ctx.rel(base);
        int seed = strike.getSeed();
        float settle = Mth.clamp((VolcanicEruptionStrike.DURATION - age) / 200.0f, 0.0f, 1.0f);

        if (age < ERUPTION) renderTremors(ctx, base, age, seed);
        renderCracks(ctx, base, age, seed);
        if (age > ERUPTION_END - 40) renderSteam(ctx, c, age, seed, settle);
        for (Vent vent : vents(seed)) {
            if (age >= vent.tick() && age < vent.tick() + 60) renderVent(ctx, base, vent, age - vent.tick());
        }
        if (age >= OPENING) renderChasm(ctx, c, age, seed, settle);
        if (age >= ERUPTION) renderEruption(ctx, c, age, seed, settle);
    }

    // ---------------------------------------------------------------- recessed cracks

    /**
     * The cracks are real holes cut by the server (magma at the bottom). Here: a crisp white-hot seam laid along
     * the actual crack floor, a soft heat glow rising out of it, getting hotter as the eruption nears.
     */
    private void renderCracks(FxContext ctx, Vec3 base, float age, int seed) {
        if (age < VolcanicEruptionStrike.CRACKS_START) return;
        float heat = heat(age) * Mth.clamp((VolcanicEruptionStrike.DURATION - age) / 200.0f, 0.0f, 1.0f);
        if (heat <= 0.01f) return;
        float pulse = 0.8f + 0.2f * Mth.sin(age * 0.25f);
        int hot = FxDraw.lerpColor(MAGMA, LAVA_WHITE, heat * heat);
        float pit = age >= OPENING ? pitRadius(age) + 1.5f : 0;

        FxDraw.Puffs smoke = new FxDraw.Puffs();
        for (List<CrackPoint> crack : cracks(seed)) {
            List<Vec3> seam = new ArrayList<>();
            List<Float> widths = new ArrayList<>();
            for (CrackPoint point : crack) {
                float open = (age - VolcanicEruptionStrike.appearTick(point)) / 14.0f;
                if (open <= 0) break;
                if (point.x() * point.x() + point.z() * point.z() < pit * pit) {
                    flushSeam(ctx, seam, widths, hot, heat, pulse, smoke, age);
                    continue; // swallowed by the chasm
                }
                seam.add(base.add(point.x(), 0, point.z()));
                widths.add(point.width() * Math.min(1.0f, open));
            }
            flushSeam(ctx, seam, widths, hot, heat, pulse, smoke, age);
        }
        smoke.draw(ctx);
    }

    /** One continuous stretch of crack: dark scorched lips, a glow from below, a white-hot core, heat and smoke. */
    private void flushSeam(FxContext ctx, List<Vec3> seam, List<Float> widths, int hot, float heat, float pulse,
                           FxDraw.Puffs smoke, float age) {
        int n = seam.size();
        if (n >= 2) {
            float[] lip = new float[n], glow = new float[n], core = new float[n];
            float[] aLip = new float[n], aGlow = new float[n], aCore = new float[n];
            for (int i = 0; i < n; i++) {
                float w = widths.get(i);
                lip[i] = 0.5f + w * 0.6f;
                glow[i] = 0.35f + w * 0.5f;
                core[i] = 0.05f + 0.09f * heat * Math.min(1.0f, w);
                aLip[i] = 0.6f;
                aGlow[i] = 0.55f * heat;
                aCore[i] = pulse;
            }
            FxDraw.drapedRibbon(ctx, false, seam, lip, aLip, TRENCH_RIM, 0.03f);
            FxDraw.drapedRibbon(ctx, true, seam, glow, aGlow, MAGMA, 0.06f);
            FxDraw.drapedRibbon(ctx, true, seam, core, aCore, hot, 0.07f);
            for (int i = 0; i < n; i += 4) {
                // heat rising out of the crack, and thin smoke where it is widest
                Vec3 p = seam.get(i);
                Vector3f q = ctx.rel(p.x, FxPresets.groundY(ctx.level, p.x, p.z) + 0.05f, p.z);
                FxDraw.beam(ctx, true, q, new Vector3f(q).add(0, 1.2f + widths.get(i) * 1.5f, 0), 0.35f + widths.get(i) * 0.4f,
                        MAGMA, 0.35f * heat, 0.0f);
                if (widths.get(i) > 0.8f && i % 8 == 0) {
                    float rise = (age * 0.08f + i * 0.37f) % 5.0f;
                    smoke.add(new Vector3f(q).add(0, 0.5f + rise, 0), 0.7f + rise * 0.3f, ASH, 0.3f * heat * (1.0f - rise / 5.0f));
                }
            }
        }
        seam.clear();
        widths.clear();
    }

    /** Before the eruption: dust jolted off the ground in rhythm with the tremors. */
    private void renderTremors(FxContext ctx, Vec3 base, float age, int seed) {
        float tremble = Mth.clamp(age / (float) ERUPTION, 0.0f, 1.0f);
        FxDraw.Puffs dust = new FxDraw.Puffs();
        for (int i = 0; i < 18; i++) {
            float period = 30 + 25 * FxDraw.hash(i, seed + 3);
            float phase = ((age + FxDraw.hash(seed + 3, i) * period) % period) / period;
            if (phase > 0.5f) continue;
            float a = FxDraw.hash(i, seed + 11) * Mth.TWO_PI;
            float rr = 5 + 25 * FxDraw.hash(seed + 11, i);
            double x = base.x + Mth.cos(a) * rr, z = base.z + Mth.sin(a) * rr;
            float lift = phase * 2.0f;
            Vector3f p = ctx.rel(x, FxPresets.groundY(ctx.level, x, z) + 0.3f + lift, z);
            dust.add(p, 0.6f + lift, 0x7A6E60, 0.35f * tremble * (1.0f - phase * 2));
        }
        dust.draw(ctx);
    }

    // ---------------------------------------------------------------- the chasm

    private void renderChasm(FxContext ctx, Vector3f c, float age, int seed, float settle) {
        float radius = Math.max(0.5f, pitRadius(age));
        float heat = heat(age) * settle;
        float build = Mth.clamp((age - BUILDUP) / (ERUPTION - BUILDUP), 0.0f, 1.0f);
        float throb = 0.8f + 0.2f * Mth.sin(age * (0.25f + build * 0.8f));
        float depth = PIT_DEPTH * Mth.clamp((age - OPENING) / 60.0f, 0.15f, 1.0f);
        Vector3f bottom = new Vector3f(c).add(0, -depth + 1.2f, 0);

        // Light from the furnace below, climbing the chasm walls
        FxDraw.wall(ctx, true, bottom, radius * 0.9f, depth + 1.0f, LAVA, 0.75f * heat * throb, 0.0f);
        FxDraw.wall(ctx, true, bottom, radius * 0.55f, depth * 0.7f, LAVA_CORE, 0.45f * heat * throb, 0.0f);

        // The lava surface at the bottom, with dark crust plates drifting on it
        FxDraw.disc(ctx, true, bottom, radius * 0.75f, LAVA_WHITE, 0.95f * heat * throb, 0.5f * heat);
        FxDraw.disc(ctx, true, new Vector3f(bottom).add(0, 0.02f, 0), radius * 0.9f, MAGMA, 0.6f * heat, 0.0f);
        for (int i = 0; i < 14; i++) {
            float a = FxDraw.hash(seed, i + 50) * Mth.TWO_PI + age * 0.004f * (i % 2 == 0 ? 1 : -1);
            float r = radius * 0.6f * Mth.sqrt(FxDraw.hash(i + 50, seed));
            Vector3f plate = new Vector3f(bottom).add(Mth.cos(a) * r, 0.05f, Mth.sin(a) * r);
            float size = 0.6f + FxDraw.hash(i, 51) * 1.3f;
            float spin = age * 0.01f + i;
            Vector3f p1 = new Vector3f(plate).add(Mth.cos(spin) * size, 0, Mth.sin(spin) * size);
            Vector3f p2 = new Vector3f(plate).add(Mth.cos(spin + 2.2f) * size * 0.8f, 0, Mth.sin(spin + 2.2f) * size * 0.8f);
            Vector3f p3 = new Vector3f(plate).add(Mth.cos(spin + 4.1f) * size * 0.9f, 0, Mth.sin(spin + 4.1f) * size * 0.9f);
            FxDraw.triangle(ctx, false, p1, p2, p3, CRUST, 0.9f, 0.9f, 0.9f);
            FxDraw.hardRibbon(ctx, true, List.of(p1, p2, p3, p1), 0.04f, LAVA_CORE, 0.8f * heat);
        }

        // Glow spilling over the rim, lying on the actual broken ground
        FxDraw.drapedRing(ctx, true, centerWorld(ctx, c), radius, 1.8f, MAGMA, 0.55f * heat * throb, 0.12f);

        // Embers spiralling up out of the chasm
        for (int i = 0; i < 50; i++) {
            float life = ((age * 0.012f) + FxDraw.hash(i, seed + 7)) % 1.0f;
            float a = FxDraw.hash(seed + 7, i) * Mth.TWO_PI + life * 3.0f;
            float r = radius * 0.7f * FxDraw.hash(i, 61) * (1.0f - life * 0.3f);
            Vector3f p = new Vector3f(bottom).add(Mth.cos(a) * r, life * (depth + 18), Mth.sin(a) * r);
            float flicker = 0.6f + 0.4f * Mth.sin(age * 0.9f + i * 1.7f);
            FxDraw.sprite(ctx, true, p, 0.08f + 0.06f * FxDraw.hash(i, 62), life < 0.5f ? LAVA_CORE : LAVA, (1.0f - life) * flicker * heat, 0.0f);
        }

        // Smoke and steam pouring out, building up before the eruption
        FxDraw.Puffs puffs = new FxDraw.Puffs();
        for (int k = 0; k < 14; k++) {
            float rise = ((age * 0.25f + k * 4.1f) % 30.0f);
            float a = k * 2.4f;
            puffs.add(new Vector3f(c).add(Mth.cos(a) * radius * 0.4f, 1 + rise, Mth.sin(a) * radius * 0.4f),
                    2.0f + rise * 0.25f, k % 3 == 0 ? STEAM : ASH, 0.5f * (1.0f - rise / 30.0f) * (0.4f + 0.6f * build) * settle);
        }
        puffs.draw(ctx);
    }

    private void renderVent(FxContext ctx, Vec3 base, Vent vent, float d) {
        double x = base.x + vent.x(), z = base.z + vent.z();
        Vector3f p = ctx.rel(x, FxPresets.groundY(ctx.level, x, z), z);
        float fade = 1.0f - d / 60.0f;
        if (d < 8) {
            FxDraw.sprite(ctx, true, new Vector3f(p).add(0, 1, 0), 5.0f, LAVA, 1.0f - d / 8.0f, 0.0f);
            FxDraw.groundRing(ctx, true, new Vector3f(p).add(0, 0.2f, 0), d * 0.8f, 0.4f, LAVA_CORE, 1.0f - d / 8.0f);
        }
        // Short jet of fire out of the vent
        if (d < 20) {
            FxDraw.flowCylinder(ctx, true, p, 0.6f * (1.0f - d / 20.0f), 6.0f * (1.0f - d / 20.0f), LAVA, 0.8f, d * 0.8f, 3);
        }
        FxDraw.Puffs puffs = new FxDraw.Puffs();
        for (int k = 0; k < 6; k++) {
            float rise = d * 0.25f + k * 1.6f;
            puffs.add(new Vector3f(p).add(Mth.sin(k * 2.3f) * 0.8f, 1 + rise, Mth.cos(k * 1.7f) * 0.8f), 1.2f + rise * 0.18f, ASH, 0.55f * fade);
        }
        puffs.draw(ctx);
    }

    // ---------------------------------------------------------------- eruption

    private void renderEruption(FxContext ctx, Vector3f c, float age, int seed, float settle) {
        float d = age - ERUPTION;
        float power = power(age);

        if (d < 10) FxDraw.sprite(ctx, true, new Vector3f(c).add(0, 6, 0), 70.0f, LAVA, 1.0f - d / 10.0f, 0.0f);
        if (d < 25) FxDraw.shell(ctx, true, c, d * 3.0f, LAVA, 0.5f * (1.0f - d / 25.0f), false);
        FxPresets.groundShock(ctx, centerWorld(ctx, c), d * 2.2f, 60, LAVA_CORE, 0x5A3E30, 0.9f, seed);
        // the plug: a dense black cloud of shattered rock blasted straight up
        if (d < 40) {
            float t = d / 40.0f;
            FxDraw.Puffs plug = new FxDraw.Puffs();
            for (int i = 0; i < 26; i++) {
                float a = FxDraw.hash(i, seed + 21) * Mth.TWO_PI;
                float out = (1.5f + 6 * FxDraw.hash(seed + 21, i)) * FxDraw.easeOut(t);
                float up = (6 + 30 * FxDraw.hash(i, 22)) * FxDraw.easeOut(t * 1.4f);
                Vector3f p = new Vector3f(c).add(Mth.cos(a) * out, 2 + up, Mth.sin(a) * out);
                plug.add(p, 2.5f + 3 * t, FxDraw.lerpColor(0x1A1412, ASH, t), 0.85f * (1.0f - t));
            }
            plug.draw(ctx);
        }

        // Pyroclastic surge: a rolling, glowing-bottomed ash wave
        float front = (age - VolcanicEruptionStrike.SURGE_START) * VolcanicEruptionStrike.SURGE_SPEED;
        if (front > 0 && front < VolcanicEruptionStrike.MAX_RADIUS) {
            float fade = 1.0f - front / VolcanicEruptionStrike.MAX_RADIUS;
            FxDraw.Puffs surge = new FxDraw.Puffs();
            for (int i = 0; i < 44; i++) {
                float a = Mth.TWO_PI * i / 44 + FxDraw.hash(i, seed) * 0.15f;
                float roll = Mth.sin(age * 0.15f + i) * 1.2f;
                Vector3f p = new Vector3f(c).add(Mth.cos(a) * front, 2.5f + roll, Mth.sin(a) * front);
                surge.add(p, 5.5f + 2 * FxDraw.hash(seed, i), SURGE, 0.75f * fade);
            }
            surge.draw(ctx);
            FxDraw.groundRing(ctx, true, new Vector3f(c).add(0, 0.4f, 0), front, 3.0f, MAGMA, 0.5f * fade);
        }

        if (power > 0.01f) {
            // Lava column: real 3D cylinders with lava flowing up them; height follows the pulses
            float height = VolcanicEruptionStrike.fountainHeight(age);
            Vector3f vent = new Vector3f(c).add(0, -PIT_DEPTH + 2, 0);
            float total = height + PIT_DEPTH - 2;
            FxDraw.flowCylinder(ctx, true, vent, 4.2f * power, total, MAGMA, 0.55f * power, age * 0.45f, 6);
            FxDraw.flowCylinder(ctx, true, vent, 2.4f * power, total * 0.95f, LAVA, 0.8f * power, age * 0.6f, 8);
            FxDraw.flowCylinder(ctx, true, vent, 1.0f * power, total * 0.8f, LAVA_WHITE, 0.9f * power, age * 0.8f, 10);
            FxDraw.sprite(ctx, true, new Vector3f(c).add(0, height, 0), 10 * power, LAVA, 0.6f * power, 0.0f);
            // blobs of lava racing up the fountain at different speeds and falling back around it
            for (int k = 0; k < 30; k++) {
                float speed = 0.6f + FxDraw.hash(k, seed + 31);
                float life = (age * 0.02f * speed + FxDraw.hash(seed + 31, k)) % 1.0f;
                float a = FxDraw.hash(k, 33) * Mth.TWO_PI;
                float spread = 1.0f + 3.0f * life * life;
                float y = (height + 4) * (float) Math.sin(life * Math.PI * 0.85);
                Vector3f blob = new Vector3f(c).add(Mth.cos(a) * spread, y, Mth.sin(a) * spread);
                FxDraw.sprite(ctx, true, blob, 0.6f + 0.6f * FxDraw.hash(k, 34), life < 0.4f ? LAVA_WHITE : LAVA_CORE, 0.8f * power, 0.0f);
            }

            // Trails behind the lava bombs (the rocks themselves are in the solid pass)
            for (int k = 0; k < 46; k++) {
                Vector3f now = bomb(c, k, d, power, seed, 0);
                Vector3f before = bomb(c, k, d, power, seed, 3);
                if (now == null || before == null) continue;
                FxDraw.beam(ctx, true, before, now, 0.35f, LAVA, 0.0f, 0.7f * power);
                FxDraw.sprite(ctx, true, now, 1.0f, MAGMA, 0.5f * power, 0.0f);
            }
            FxDraw.Puffs trails = new FxDraw.Puffs();
            for (int k = 0; k < 46; k += 2) {
                for (int back = 4; back <= 12; back += 4) {
                    Vector3f puff = bomb(c, k, d, power, seed, back);
                    if (puff != null) trails.add(puff, 0.5f + back * 0.08f, ASH, 0.4f * power * (1.0f - back / 14.0f));
                }
            }
            trails.draw(ctx);
        }

        // Ash plume: column rising into a spreading umbrella, drifting with the wind
        float grow = FxDraw.easeOut(d / 260.0f);
        float plumeHeight = 35 + 120 * grow;
        float umbrella = 18 + 45 * grow;
        float plumeAlpha = 0.7f * Math.min(1.0f, d / 15.0f) * settle;
        float drift = d * 0.02f;
        FxDraw.Puffs plume = new FxDraw.Puffs();
        for (int i = 0; i < 24; i++) {
            float t = i / 23.0f;
            float y = 10 + t * plumeHeight;
            float spread = 5 + t * 10;
            float a = i * 2.4f + d * 0.01f;
            // the lower column is lit by the fountain below it
            int lit = FxDraw.lerpColor(ASH_LIT, ASH, Mth.clamp(t * 3.0f + (1.0f - power), 0.0f, 1.0f));
            plume.add(new Vector3f(c).add(Mth.cos(a) * spread * 0.4f + drift * t * 10, y, Mth.sin(a) * spread * 0.4f), spread, t < 0.3f ? lit : ASH_LIGHT, plumeAlpha);
        }
        for (int i = 0; i < 40; i++) {
            float a = FxDraw.hash(i, seed) * Mth.TWO_PI + d * 0.003f;
            float r = umbrella * Mth.sqrt(FxDraw.hash(seed, i));
            Vector3f p = new Vector3f(c).add(Mth.cos(a) * r + drift * 10, plumeHeight + 6 * FxDraw.hash(i, 7), Mth.sin(a) * r);
            plume.add(p, 10 + 6 * FxDraw.hash(i, 13), ASH_LIGHT, plumeAlpha * 0.9f);
        }
        plume.draw(ctx);
        FxDraw.sprite(ctx, true, new Vector3f(c).add(0, 18, 0), 22 * power, MAGMA, 0.3f * power, 0.0f);

        // Volcanic lightning inside the ash cloud
        if (power > 0.2f) {
            Random random = new Random(seed ^ ((int) (age / 4) * 4099L));
            if (random.nextFloat() < 0.55f) {
                Vector3f from = new Vector3f(c).add((random.nextFloat() - 0.5f) * umbrella, plumeHeight * (0.5f + random.nextFloat() * 0.5f), (random.nextFloat() - 0.5f) * umbrella);
                Vector3f to = new Vector3f(from).add((random.nextFloat() - 0.5f) * 20, -(5 + random.nextFloat() * 20), (random.nextFloat() - 0.5f) * 20);
                FxDraw.drawBolt(ctx, FxDraw.bolt(random, from, to, 6.0f, 5), 0.15f, 0xB088FF, 0.9f * power);
            }
        }
    }

    /** Waning: white steam pouring off the cooling lava, thinning out as it crusts over. */
    private void renderSteam(FxContext ctx, Vector3f c, float age, int seed, float settle) {
        float t = Mth.clamp((age - (ERUPTION_END - 40)) / 60.0f, 0.0f, 1.0f) * settle;
        FxDraw.Puffs steam = new FxDraw.Puffs();
        for (int k = 0; k < 18; k++) {
            float rise = (age * 0.18f + k * 3.3f) % 26.0f;
            float a = k * 2.4f + FxDraw.hash(k, seed);
            float rr = PIT_RADIUS * 0.6f * FxDraw.hash(seed, k);
            steam.add(new Vector3f(c).add(Mth.cos(a) * rr + rise * 0.2f, -2 + rise, Mth.sin(a) * rr), 1.5f + rise * 0.3f, STEAM,
                    0.4f * t * (1.0f - rise / 26.0f));
        }
        steam.draw(ctx);
    }

    /** World position of the strike center (for terrain-draped decals). */
    private static Vec3 centerWorld(FxContext ctx, Vector3f c) {
        return new Vec3(c.x + ctx.cam.x, c.y + ctx.cam.y, c.z + ctx.cam.z);
    }

    // ---------------------------------------------------------------- per tick

    @Override
    public void tick(StrikeEntity strike, ClientLevel level, int age, RandomSource random) {
        Vec3 base = strike.position();
        int seed = strike.getSeed();

        if (age < ERUPTION) {
            // The ground keeps getting angrier; a jolt when the chasm caves in
            CameraFx.rumbleAt(base, 0.03f + 0.17f * age / ERUPTION + (age >= OPENING ? 0.08f : 0), 130);
            if (age > BUILDUP) CameraFx.fovSqueeze(0.07f * CameraFx.proximity(base, 120));
            if (age >= OPENING) CameraFx.tint(0x401000, 0.12f * CameraFx.proximity(base, 60));
        }
        if (age == OPENING) CameraFx.shakeAt(base, 0.6f, 120);

        for (Vent vent : vents(seed)) {
            if (vent.tick() != age) continue;
            double x = base.x + vent.x(), z = base.z + vent.z();
            Vec3 p = new Vec3(x, FxPresets.groundY(level, x, z), z);
            CameraFx.shakeAt(p, 0.35f, 80);
            FxPresets.burst(level, ParticleTypes.LARGE_SMOKE, p.add(0, 1, 0), 10, 0.8, 0.2, random);
        }
        if (age == ERUPTION) {
            CameraFx.flashSeen(base.add(0, 4, 0), 0xFFA040, 0.75f, 260, 30);
            CameraFx.shakeAt(base, 1.25f, 220);
            CameraFx.fovKickAt(base, 0.16f, 200);
        }
        float power = power(age);
        if (age > ERUPTION + 20 && (age - ERUPTION) % 60 == 12) CameraFx.shakeAt(base, 0.45f * power + 0.1f, 160);
        if (power > 0) {
            CameraFx.rumbleAt(base, 0.28f * power, 170);
            CameraFx.tint(0x2A1206, 0.3f * power * CameraFx.proximity(base, 220));
            if (age % 2 == 0) {
                level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true,
                        base.x + random.nextGaussian() * 3, base.y + 3, base.z + random.nextGaussian() * 3, 0, 0.2, 0);
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.position().distanceTo(base) < 170) {
                Vec3 me = mc.player.position();
                for (int i = 0; i < 6; i++) {
                    level.addParticle(i % 2 == 0 ? ParticleTypes.ASH : ParticleTypes.WHITE_ASH,
                            me.x + random.nextGaussian() * 14, me.y + 5 + random.nextDouble() * 10, me.z + random.nextGaussian() * 14, 0, -0.03, 0);
                }
            }
        }
        // Shake when the surge rolls past the player
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            double distance = mc.player.position().distanceTo(base);
            float front = (age - VolcanicEruptionStrike.SURGE_START) * VolcanicEruptionStrike.SURGE_SPEED;
            if (front > 0 && Math.abs(front - distance) < VolcanicEruptionStrike.SURGE_SPEED && distance < VolcanicEruptionStrike.MAX_RADIUS) {
                CameraFx.shake(0.5f * (float) (1.0 - distance / VolcanicEruptionStrike.MAX_RADIUS) + 0.15f);
                CameraFx.flash(0x5A3020, 0.3f, 20);
            }
        }
    }
}
