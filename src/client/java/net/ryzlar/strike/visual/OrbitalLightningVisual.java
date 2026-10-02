package net.ryzlar.strike.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.ryzlar.fx.CameraFx;
import net.ryzlar.fx.FxContext;
import net.ryzlar.fx.FxDraw;
import net.ryzlar.fx.FxPresets;
import net.ryzlar.strike.OrbitalLightningStrike;
import net.ryzlar.strike.OrbitalLightningStrike.Bolt;
import net.ryzlar.strike.StrikeEntity;
import net.ryzlar.strike.StrikeVisual;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Orbital Lightning: an orbital weapon platform discharging into the ground.
 * <ul>
 *     <li><b>Lock-on</b>: a radar grid draped over the terrain; a targeting laser comes down from a glint in orbit,
 *     hunts for a moment and locks.</li>
 *     <li><b>Charge</b>: the platform's emitter assembles above an ionized storm vortex; arcs crawl through it, the air
 *     shimmers, the light goes cold.</li>
 *     <li><b>Strikes</b>: every bolt is telegraphed by a contracting ring on the ground, then lands as a near-straight
 *     lance with forks, a flash, arcs crawling over the terrain and a scorch mark. Probe shots, an escalating barrage,
 *     two rings of simultaneous strikes closing in, a heavy bolt on the target.</li>
 *     <li><b>Discharge</b>: a pilot beam locks, the column charges, then a full-height energy column with rings racing
 *     down it, an electrical explosion of arcs, a shock front over the terrain and charged ground crackling after.</li>
 * </ul>
 */
public class OrbitalLightningVisual implements StrikeVisual {

    private static final int CYAN = 0x4FD8FF;
    private static final int VIOLET = 0x8A7BFF;
    private static final int ICE = 0xCFF6FF;
    private static final int STORM = 0x141B2C;
    private static final int SCORCH = 0x0C0C0C;
    private static final int DUST = 0x5A6470;

    private static final int LOCK = OrbitalLightningStrike.LOCK;
    private static final int CHARGE = OrbitalLightningStrike.CHARGE_START;
    private static final int FINAL_CHARGE = OrbitalLightningStrike.FINAL_CHARGE;
    private static final int FINAL = OrbitalLightningStrike.FINAL;
    private static final int TELEGRAPH = OrbitalLightningStrike.TELEGRAPH;

    private final Map<Integer, List<Bolt>> scheduleCache = new HashMap<>();

    private List<Bolt> bolts(int seed) {
        if (scheduleCache.size() > 16) scheduleCache.clear();
        return scheduleCache.computeIfAbsent(seed, OrbitalLightningStrike::schedule);
    }

    private static Vec3 hitPoint(FxContext ctx, Vec3 target, Bolt bolt) {
        double x = target.x + bolt.dx(), z = target.z + bolt.dz();
        return new Vec3(x, FxPresets.groundY(ctx.level, x, z), z);
    }

    // ---------------------------------------------------------------- distortion

    @Override
    public void renderDistortion(FxContext ctx, StrikeEntity strike, float age) {
        Vec3 target = strike.position();
        Vector3f c = ctx.rel(target);
        Vector3f vortex = new Vector3f(c).add(0, OrbitalLightningStrike.VORTEX_HEIGHT, 0);
        float form = FxDraw.smooth((age - CHARGE) / 70.0f) * Mth.clamp(1.0f - (age - FINAL) / 60.0f, 0.0f, 1.0f);
        if (form > 0) FxDraw.distortion(ctx, vortex, 26.0f, -0.2f, 0.6f, 0.5f, 0.45f * form);
        for (Bolt bolt : bolts(strike.getSeed())) {
            float local = age - bolt.tick();
            if (local < 0 || local > 6) continue;
            Vector3f hit = ctx.rel(hitPoint(ctx, target, bolt)).add(0, 1, 0);
            FxDraw.distortion(ctx, hit, 2.0f + local * (1.5f + bolt.power() * 2.0f), 0.6f, 0.4f, 0.0f, 1.0f - local / 6.0f);
        }
        if (age >= FINAL_CHARGE && age < FINAL + 40) {
            float t = age < FINAL ? (age - FINAL_CHARGE) / (FINAL - FINAL_CHARGE) : 1.0f - (age - FINAL) / 40.0f;
            FxDraw.heatHaze(ctx, c, 3.5f, 50.0f, 0.7f * t);
        }
        if (age >= FINAL && age < FINAL + 24) {
            float d = age - FINAL;
            FxDraw.distortion(ctx, new Vector3f(c).add(0, 2, 0), 4 + d * 2.4f, 0.8f, 0.3f, 0.0f, 1.0f - d / 24.0f);
        }
    }

    // ---------------------------------------------------------------- effects

    @Override
    public void render(FxContext ctx, StrikeEntity strike, float age) {
        Vec3 target = strike.position();
        Vector3f c = ctx.rel(target);
        Vector3f vortex = new Vector3f(c).add(0, OrbitalLightningStrike.VORTEX_HEIGHT, 0);
        int seed = strike.getSeed();

        if (age < FINAL + 20) renderTargeting(ctx, target, age);
        if (age >= LOCK && age < FINAL + 40) renderUplink(ctx, c, vortex, age, seed);
        if (age >= CHARGE && age < FINAL + 60) renderVortex(ctx, vortex, age, seed);
        for (Bolt bolt : bolts(seed)) {
            float local = age - bolt.tick();
            if (local >= -TELEGRAPH && local < 0) renderTelegraph(ctx, target, bolt, -local, age);
            if (local >= -1 && local < 150) renderBolt(ctx, target, vortex, bolt, local, seed);
        }
        if (age >= FINAL_CHARGE && age < FINAL) renderFinalCharge(ctx, target, c, vortex, age, seed);
        if (age >= FINAL) renderFinal(ctx, target, c, vortex, age - FINAL, seed);
    }

    /** Radar grid on the real terrain: rings, a sweep arm, range ticks, and the designator at the center. */
    private void renderTargeting(FxContext ctx, Vec3 target, float age) {
        float alpha = Math.min(1.0f, age / 15.0f) * Mth.clamp((FINAL + 20 - age) / 20.0f, 0.0f, 1.0f);
        float r = OrbitalLightningStrike.AREA_RADIUS;
        FxDraw.drapedRing(ctx, true, target, r, 0.2f, CYAN, 0.6f * alpha, 0.06f);
        FxDraw.drapedRing(ctx, true, target, r * 0.66f, 0.08f, CYAN, 0.3f * alpha, 0.06f);
        FxDraw.drapedRing(ctx, true, target, r * 0.33f, 0.08f, CYAN, 0.3f * alpha, 0.06f);
        float sweep = age * 0.12f;
        for (int k = 0; k < 6; k++) {
            float a = sweep - k * 0.12f;
            List<Vec3> arm = new ArrayList<>();
            for (int i = 0; i <= 10; i++) arm.add(target.add(Mth.cos(a) * r * i / 10, 0, Mth.sin(a) * r * i / 10));
            float[] w = new float[11], al = new float[11];
            java.util.Arrays.fill(w, 0.14f);
            java.util.Arrays.fill(al, 0.5f * alpha * (1.0f - k / 6.0f));
            FxDraw.drapedRibbon(ctx, true, arm, w, al, CYAN, 0.07f);
        }
        for (int i = 0; i < 12; i++) {
            float a = i * Mth.TWO_PI / 12;
            FxDraw.drapedRibbon(ctx, true, List.of(target.add(Mth.cos(a) * (r + 1.4f), 0, Mth.sin(a) * (r + 1.4f)),
                    target.add(Mth.cos(a) * (r + 0.2f), 0, Mth.sin(a) * (r + 0.2f))), new float[]{0.25f, 0.1f},
                    new float[]{0.8f * alpha, 0.8f * alpha}, ICE, 0.07f);
        }
        FxPresets.designator(ctx, target, 3.0f, CYAN, age, alpha, age / FINAL);
    }

    /** The platform in orbit and its targeting laser: it hunts for a moment, then locks onto the target. */
    private void renderUplink(FxContext ctx, Vector3f c, Vector3f vortex, float age, int seed) {
        float on = Math.min(1.0f, (age - LOCK) / 10.0f) * Mth.clamp(1.0f - (age - FINAL) / 40.0f, 0.0f, 1.0f);
        Vector3f orbit = new Vector3f(c).add(0, OrbitalLightningStrike.ORBIT_HEIGHT, 0);
        float glint = 0.7f + 0.3f * Mth.sin(age * 0.5f);
        FxDraw.sprite(ctx, true, orbit, 6.0f, ICE, 0.8f * on * glint, 0.0f);
        FxDraw.sprite(ctx, true, orbit, 2.0f, 0xFFFFFF, on, 0.0f);
        FxDraw.beam(ctx, true, new Vector3f(orbit).add(-14, 0, 0), new Vector3f(orbit).add(14, 0, 0), 0.25f, ICE, 0.5f * on, 0.5f * on);
        FxDraw.beam(ctx, true, new Vector3f(orbit).add(0, -10, 0), new Vector3f(orbit).add(0, 10, 0), 0.25f, ICE, 0.5f * on, 0.5f * on);
        // hunting, then locked
        float hunt = Mth.clamp(1.0f - (age - LOCK) / 40.0f, 0.0f, 1.0f);
        Vector3f aim = new Vector3f(c).add(Mth.sin(age * 0.31f + seed) * 9 * hunt, 0, Mth.cos(age * 0.23f) * 9 * hunt);
        FxDraw.beam(ctx, true, orbit, aim, 0.12f + 0.1f * (1 - hunt), CYAN, 0.55f * on, 0.35f * on);
        FxDraw.hardRibbon(ctx, true, List.of(orbit, aim), 0.03f, 0xFFFFFF, 0.6f * on);
        if (hunt < 0.05f && age < LOCK + 50) {
            float t = (age - LOCK - 40) / 10.0f;
            FxDraw.sprite(ctx, true, new Vector3f(c).add(0, 0.5f, 0), 3.0f * (1 - t) + 0.5f, 0xFFFFFF, 0.8f * (1 - t), 0.0f);
        }
    }

    private void renderVortex(FxContext ctx, Vector3f vortex, float age, int seed) {
        float form = FxDraw.smooth((age - CHARGE) / 70.0f);
        float contract = age > FINAL_CHARGE ? FxDraw.smooth((age - FINAL_CHARGE) / (FINAL - FINAL_CHARGE)) : 0.0f;
        float out = age > FINAL ? Mth.clamp(1.0f - (age - FINAL) / 60.0f, 0.0f, 1.0f) : 1.0f;
        float radius = 30.0f * form * (1.0f - 0.55f * contract);
        float alpha = form * out;
        if (alpha <= 0.01f) return;

        // Storm mass: overlapping soft puffs in a thick, lumpy band, lit from inside when arcs fire
        FxDraw.Puffs clouds = new FxDraw.Puffs();
        for (int i = 0; i < 64; i++) {
            float a = Mth.TWO_PI * FxDraw.hash(i, seed) - age * 0.008f;
            float rr = radius * (0.85f + 0.6f * FxDraw.hash(seed, i));
            float y = 3 * (FxDraw.hash(i, 3) - 0.3f);
            clouds.add(new Vector3f(vortex).add(Mth.cos(a) * rr, y, Mth.sin(a) * rr), 8.0f + 6 * FxDraw.hash(i, 5), STORM, 0.5f * alpha);
        }
        clouds.draw(ctx);

        // Spiral energy arms, seen from below
        int arms = 5;
        for (int arm = 0; arm < arms; arm++) {
            List<Vector3f> points = new ArrayList<>();
            float[] widths = new float[18], alphas = new float[18];
            for (int k = 0; k < 18; k++) {
                float t = k / 17.0f;
                float rr = radius * t;
                float a = arm * Mth.TWO_PI / arms + t * 2.4f - age * 0.09f;
                points.add(new Vector3f(vortex).add(Mth.cos(a) * rr, Mth.sin(age * 0.1f + k) * 0.4f, Mth.sin(a) * rr));
                widths[k] = Mth.lerp(t, 0.6f, 2.6f);
                alphas[k] = alpha * (0.2f + 0.6f * (1.0f - t)) * (0.7f + 0.3f * Mth.sin(age * 0.5f + arm));
            }
            FxDraw.groundRibbon(ctx, true, points, widths, alphas, arm % 2 == 0 ? CYAN : VIOLET);
        }
        renderEmitter(ctx, new Vector3f(vortex).add(0, 6, 0), age, alpha, contract);

        // The eye
        FxDraw.disc(ctx, true, vortex, radius * 0.3f, ICE, (0.6f + 0.4f * contract) * alpha, 0.0f);
        FxDraw.sprite(ctx, true, vortex, 3 + 6 * contract, 0xFFFFFF, (0.4f + 0.6f * contract) * alpha, 0.0f);

        // Arcs crawling through the vortex
        int bucket = (int) (age / 3);
        Random random = new Random(seed ^ (bucket * 7919L));
        for (int i = 0; i < 3; i++) {
            Vector3f a = randomInDisc(random, vortex, radius * 0.9f);
            Vector3f b = randomInDisc(random, vortex, radius * 0.9f);
            FxDraw.drawBolt(ctx, FxDraw.bolt(random, a, b, 4.0f, 5), 0.12f, VIOLET, 0.8f * alpha);
        }
    }

    /**
     * The weapon itself: a hard-edged orbital emitter assembled above the vortex. Counter-rotating hexagonal rings
     * and pylons, brightening as it charges.
     */
    private void renderEmitter(FxContext ctx, Vector3f center, float age, float alpha, float charge) {
        float glow = 0.5f + 0.5f * charge;
        for (int ring = 0; ring < 3; ring++) {
            float radius = 5.0f + ring * 3.5f;
            float spin = age * 0.02f * (ring % 2 == 0 ? 1 : -1) + ring;
            List<Vector3f> hex = new ArrayList<>();
            for (int i = 0; i <= 6; i++) {
                float a = spin + i * Mth.TWO_PI / 6;
                hex.add(new Vector3f(center).add(Mth.cos(a) * radius, ring * 0.8f, Mth.sin(a) * radius));
            }
            FxDraw.ribbon(ctx, true, hex, 0.5f, 0.35f * alpha * glow, 0.35f * alpha * glow, CYAN);
            FxDraw.hardRibbon(ctx, true, hex, 0.07f, ring == 1 ? VIOLET : ICE, alpha * (0.6f + 0.4f * glow));
            for (int i = 0; i < 6; i++) FxDraw.sprite(ctx, true, hex.get(i), 0.5f + 0.4f * charge, 0xFFFFFF, alpha * glow, 0.0f);
        }
        Vector3f focus = new Vector3f(center).add(0, -5, 0);
        for (int i = 0; i < 6; i++) {
            float a = -age * 0.02f + i * Mth.TWO_PI / 6;
            Vector3f top = new Vector3f(center).add(Mth.cos(a) * 12, 1.6f, Mth.sin(a) * 12);
            FxDraw.hardRibbon(ctx, true, List.of(top, focus), 0.05f, CYAN, 0.5f * alpha * glow);
        }
        FxDraw.sprite(ctx, true, focus, 1.5f + 3 * charge, ICE, alpha * glow, 0.0f);
    }

    /** Half a second before a bolt: a ring contracts onto the spot and a faint guide flickers down to it. */
    private void renderTelegraph(FxContext ctx, Vec3 target, Bolt bolt, float remaining, float age) {
        Vec3 hit = hitPoint(ctx, target, bolt);
        float t = 1.0f - remaining / TELEGRAPH;
        float size = 1.0f + bolt.power() * 2.0f;
        FxDraw.drapedRing(ctx, true, hit, size * (2.5f - 2.0f * t), 0.12f, ICE, 0.4f + 0.6f * t, 0.08f);
        FxDraw.drapedDisc(ctx, true, hit, size * 0.8f, CYAN, 0.35f * t, 0.0f, 0.06f);
        if (((int) age) % 2 == 0) {
            Vector3f ground = ctx.rel(hit);
            FxDraw.beam(ctx, true, ground, new Vector3f(ground).add(0, 40, 0), 0.06f, CYAN, 0.35f * t, 0.0f);
        }
    }

    private void renderBolt(FxContext ctx, Vec3 target, Vector3f vortex, Bolt bolt, float local, int seed) {
        Vec3 hitWorld = hitPoint(ctx, target, bolt);
        Vector3f ground = ctx.rel(hitWorld);
        // scorch stays a while
        if (local > 0) {
            float scorch = Mth.clamp(1.0f - local / 150.0f, 0.0f, 1.0f);
            FxDraw.drapedDisc(ctx, false, hitWorld, 1.6f + bolt.power() * 1.6f, SCORCH, 0.6f * scorch, 0.0f, 0.04f);
            FxDraw.drapedDisc(ctx, true, hitWorld, 0.8f + bolt.power(), CYAN, 0.4f * Mth.clamp(1.0f - local / 40.0f, 0.0f, 1.0f), 0.0f, 0.05f);
        }
        if (local > 10 || local < 0) return;

        float fade = local < 2 ? 1.0f : 1.0f - (local - 2) / 8.0f;
        // a weapon, not weather: a near-straight lance with a few forks, re-jittered every two ticks
        Random random = new Random(seed * 31L + bolt.tick() * 17L + (int) (bolt.dx() * 7) + (int) (local / 2));
        Vector3f from = randomInDisc(new Random(seed + bolt.tick() + (int) (bolt.dz() * 13)), vortex, 10.0f);
        float core = 0.2f + bolt.power() * 0.35f;
        for (List<Vector3f> line : FxDraw.forkedBolt(random, from, ground, 3.5f + bolt.power() * 3, 1 + Math.round(bolt.power() * 3))) {
            FxDraw.drawBolt(ctx, line, core, random.nextBoolean() ? CYAN : VIOLET, fade);
        }
        FxDraw.hardRibbon(ctx, true, List.of(from, ground), core * 0.25f, 0xFFFFFF, fade * 0.8f);
        FxDraw.sprite(ctx, true, new Vector3f(ground).add(0, 1, 0), 4 + bolt.power() * 6, ICE, fade, 0.0f);
        FxDraw.shell(ctx, true, new Vector3f(ground), local * (0.9f + bolt.power()), ICE, 0.5f * fade, false);
        // arcs crawling outward over the actual ground
        if (local < 6) groundArcs(ctx, hitWorld, 4 + Math.round(bolt.power() * 4), 2 + 4 * bolt.power(), seed + bolt.tick(), local, fade);
    }

    /** Jagged arcs crawling outward over the terrain from a point. */
    private static void groundArcs(FxContext ctx, Vec3 center, int count, float length, int seed, float time, float alpha) {
        Random random = new Random(seed * 131L + (int) (time / 2));
        for (int i = 0; i < count; i++) {
            double heading = random.nextDouble() * Math.PI * 2;
            List<Vec3> points = new ArrayList<>();
            double x = center.x, z = center.z;
            int steps = 6 + random.nextInt(4);
            for (int k = 0; k <= steps; k++) {
                points.add(new Vec3(x, 0, z));
                heading += (random.nextDouble() - 0.5) * 1.6;
                x += Math.cos(heading) * length / steps;
                z += Math.sin(heading) * length / steps;
            }
            float[] w = new float[points.size()], a = new float[points.size()];
            for (int k = 0; k < points.size(); k++) {
                w[k] = 0.09f * (1.0f - k / (float) points.size()) + 0.02f;
                a[k] = alpha * (1.0f - k / (float) points.size());
            }
            FxDraw.drapedRibbon(ctx, true, points, w, a, ICE, 0.1f);
            FxDraw.drapedRibbon(ctx, true, points, scale(w, 4), scale(a, 0.3f), CYAN, 0.08f);
        }
    }

    private void renderFinalCharge(FxContext ctx, Vec3 target, Vector3f c, Vector3f vortex, float age, int seed) {
        float t = (age - FINAL_CHARGE) / (FINAL - FINAL_CHARGE);
        FxDraw.beam(ctx, true, vortex, c, 0.08f + 0.5f * t, ICE, 0.4f + 0.6f * t, 0.4f + 0.6f * t);
        for (int i = 0; i < 3; i++) {
            float phase = (t * 2.5f + i / 3.0f) % 1.0f;
            FxDraw.drapedRing(ctx, true, target, OrbitalLightningStrike.FINAL_RADIUS * (1.0f - phase), 0.4f, CYAN, phase * 0.9f, 0.1f);
        }
        FxDraw.wall(ctx, true, c, 4.0f, 30 * t, VIOLET, 0.4f * t, 0.0f);
        // tendrils reaching down around the future column
        Random random = new Random(seed ^ ((int) (age / 2) * 97L));
        for (int i = 0; i < 4; i++) {
            Vector3f from = randomInDisc(random, vortex, 8.0f);
            Vector3f to = new Vector3f(c).add((random.nextFloat() - 0.5f) * 10, 20 * random.nextFloat(), (random.nextFloat() - 0.5f) * 10);
            FxDraw.drawBolt(ctx, FxDraw.bolt(random, from, to, 5.0f, 5), 0.1f, CYAN, 0.6f * t);
        }
        FxDraw.sprite(ctx, true, vortex, 4 + 10 * t, ICE, 0.6f * t, 0.0f);
    }

    private void renderFinal(FxContext ctx, Vec3 target, Vector3f c, Vector3f vortex, float d, int seed) {
        float height = OrbitalLightningStrike.VORTEX_HEIGHT;
        if (d < 30) {
            float fade = 1.0f - d / 30.0f;
            FxDraw.flowCylinder(ctx, true, c, 3.8f * fade, height, VIOLET, 0.35f * fade, -d * 1.5f, 8);
            FxDraw.flowCylinder(ctx, true, c, 1.9f * fade, height, CYAN, 0.6f * fade, -d * 2.0f, 12);
            FxDraw.hardRibbon(ctx, true, List.of(new Vector3f(vortex), new Vector3f(c)), 0.5f * fade, 0xFFFFFF, fade);
            Random random = new Random(seed ^ ((int) (d / 2) * 131L));
            for (int i = 0; i < 6; i++) {
                float a = i * Mth.TWO_PI / 6 + d * 0.2f;
                Vector3f from = new Vector3f(vortex).add(Mth.cos(a) * 6, 0, Mth.sin(a) * 6);
                Vector3f to = new Vector3f(c).add(Mth.cos(a + 1.5f) * 2.5f, 0.5f, Mth.sin(a + 1.5f) * 2.5f);
                FxDraw.drawBolt(ctx, FxDraw.bolt(random, from, to, 7.0f, 6), 0.35f, i % 2 == 0 ? CYAN : VIOLET, fade);
            }
            for (int i = 0; i < 6; i++) {
                float t = ((d * 0.12f) + i / 6.0f) % 1.0f;
                Vector3f ring = new Vector3f(c).add(0, height * (1.0f - t), 0);
                FxDraw.ring(ctx, true, ring, new Vector3f(1, 0, 0), new Vector3f(0, 0, 1), 3 + 3 * t, 3 + 3 * t, 0.35f,
                        i % 2 == 0 ? ICE : VIOLET, fade * (0.4f + 0.6f * t), 32);
            }
        }
        if (d < 8) FxDraw.sprite(ctx, true, new Vector3f(c).add(0, 3, 0), 30.0f, ICE, 0.85f * (1.0f - d / 8.0f), 0.0f);
        // electrical explosion: arcs bursting out of the impact in every direction
        if (d < 10) {
            float fade = 1.0f - d / 10.0f;
            Random random = new Random(seed ^ ((int) d * 733L));
            for (int i = 0; i < 14; i++) {
                float a = FxDraw.hash(seed, i) * Mth.TWO_PI, up = 0.15f + 0.8f * FxDraw.hash(i, seed);
                float len = (6 + 8 * FxDraw.hash(i, 3)) * FxDraw.easeOut(d / 4.0f);
                Vector3f dir = new Vector3f(Mth.cos(a) * (1 - up), up, Mth.sin(a) * (1 - up)).normalize();
                Vector3f start = new Vector3f(c).add(0, 1, 0);
                FxDraw.drawBolt(ctx, FxDraw.bolt(random, start, new Vector3f(dir).mul(len).add(start), 2.0f, 4), 0.12f, CYAN, fade);
            }
        }
        if (d < 16) FxDraw.shell(ctx, true, c, d * 2.2f, ICE, 0.35f * (1.0f - d / 16.0f), false);
        FxPresets.groundShock(ctx, target, OrbitalLightningStrike.finalShockRadius(d + FINAL), OrbitalLightningStrike.FINAL_RADIUS + 10,
                CYAN, DUST, 0.8f, seed);

        float glow = Mth.clamp(1.0f - d / 90.0f, 0.0f, 1.0f);
        FxDraw.drapedDisc(ctx, true, target, 8.0f, CYAN, 0.5f * glow, 0.0f, 0.06f);
        FxDraw.drapedDisc(ctx, false, target, 9.0f, SCORCH, 0.55f * Mth.clamp(1.0f - d / 130.0f, 0.0f, 1.0f), 0.0f, 0.04f);
        // charged ground: arcs crawling over it while it discharges
        if (d > 4 && d < 90) {
            float fade = 1.0f - (d - 4) / 86.0f;
            groundArcs(ctx, target, 5, 7.0f, seed + 999, d, fade);
            Random random = new Random(seed ^ ((int) (d / 5) * 211L));
            Vec3 spot = target.add((random.nextFloat() - 0.5f) * 14, 0, (random.nextFloat() - 0.5f) * 14);
            groundArcs(ctx, spot, 3, 2.5f, seed + (int) d, d, fade);
        }
        // smoke wisps off the scorched ground
        float smoke = Mth.clamp(1.0f - (d - 20) / 110.0f, 0.0f, 1.0f) * Mth.clamp(d / 10.0f, 0.0f, 1.0f);
        FxDraw.Puffs puffs = new FxDraw.Puffs();
        for (int i = 0; smoke > 0 && i < 10; i++) {
            float rise = (d * 0.12f + i * 1.7f) % 9.0f;
            float a = i * 2.4f;
            puffs.add(new Vector3f(c).add(Mth.cos(a) * 3, 0.5f + rise, Mth.sin(a) * 3), 1.0f + rise * 0.25f, 0x3A3D42,
                    0.35f * smoke * (1.0f - rise / 9.0f));
        }
        puffs.draw(ctx);
    }

    private static Vector3f randomInDisc(Random random, Vector3f center, float radius) {
        double a = random.nextDouble() * Math.PI * 2;
        double r = radius * Math.sqrt(random.nextDouble());
        return new Vector3f(center).add((float) (Math.cos(a) * r), 0, (float) (Math.sin(a) * r));
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
        if (age >= CHARGE && age < FINAL + 40) {
            float form = Mth.clamp((age - CHARGE) / 70.0f, 0.0f, 1.0f);
            CameraFx.tint(0x0A1430, 0.3f * form * CameraFx.proximity(target, 140));
        }
        if (age >= FINAL_CHARGE && age < FINAL) {
            CameraFx.fovSqueeze(0.08f * CameraFx.proximity(target, 120));
            CameraFx.rumbleAt(target, 0.12f, 100);
        }
        boolean flashed = false;
        for (Bolt bolt : bolts(strike.getSeed())) {
            if (bolt.tick() != age) continue;
            double x = target.x + bolt.dx(), z = target.z + bolt.dz();
            Vec3 hit = new Vec3(x, FxPresets.groundY(level, x, z) + 1, z);
            if (!flashed) CameraFx.flashSeen(hit, 0xCFF0FF, 0.2f + 0.3f * bolt.power(), 70, 5);
            CameraFx.shakeAt(hit, 0.12f + 0.25f * bolt.power(), 70);
            flashed = true;
        }
        if (age == FINAL) {
            CameraFx.flashSeen(target.add(0, 2, 0), 0xE0F8FF, 0.8f, 220, 16);
            CameraFx.fovKickAt(target, 0.2f, 150);
            CameraFx.shakeAt(target, 1.0f, 160);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && age > FINAL) {
            double distance = mc.player.position().distanceTo(target);
            float front = OrbitalLightningStrike.finalShockRadius(age), previous = OrbitalLightningStrike.finalShockRadius(age - 1);
            if (distance > 4 && distance <= front && distance > previous && distance < 40) {
                CameraFx.shake(0.2f + 0.4f * (float) (1.0 - distance / 40.0));
            }
        }
    }
}
