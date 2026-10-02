package net.ryzlar.strike.visual;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ryzlar.fx.CameraFx;
import net.ryzlar.fx.FxContext;
import net.ryzlar.fx.FxDraw;
import net.ryzlar.fx.FxMesh;
import net.ryzlar.fx.FxPresets;
import net.ryzlar.strike.StrikeEntity;
import net.ryzlar.strike.StrikeVisual;
import net.ryzlar.strike.VoidRiftStrike;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Void Rift: a tear in reality, fixed in the world.
 *
 * <ul>
 *     <li><b>The opening</b> is a vertical tear in a seeded plane. Its surface is opaque and writes depth, so whatever
 *     crosses it disappears exactly where it passes the boundary. Its lower part lies below the surface, inside the
 *     fissure the server tears into the terrain: the void continues into the ground instead of being cut off by it.</li>
 *     <li><b>The interior</b> is an impossible space that does not fit behind the opening: drifting chunks of a broken
 *     world at strange angles, a twisting corridor, an endless regress of smaller tears, a black sun. It is built in 3D
 *     behind the plane and projected onto the opening along every view ray, then clamped to the tear's outline: real
 *     parallax from every angle, never spilling outside the tear. It also gets <i>deeper</i> the more obliquely you
 *     look at it, the opposite of what a real hole would do.</li>
 *     <li><b>The boundary</b> bends light (screen-space lensing), has chromatic fringes, and sheds pieces of the world
 *     that fall inward. Cold white fractures spread over the ground around the fissure.</li>
 *     <li><b>Closing</b>: the interior contracts, the edges knit into a blazing seam, the seam collapses to a point and
 *     snaps; a scar hangs in the air and fades.</li>
 * </ul>
 * Deliberately not a portal: no purple, no particles, no flat texture.
 */
public class VoidRiftVisual implements StrikeVisual {

    private static final int VOID = 0x010304;
    private static final int ABYSS = 0x03111A;
    private static final int NEBULA = 0x123A4A;
    private static final int PALE = 0xEAF8FF;
    private static final int ICE = 0xA8EEFF;
    private static final int FRINGE_RED = 0xFF5634;
    private static final int FRINGE_CYAN = 0x38E8FF;
    private static final int STONE = 0x6C6C6E;
    private static final int DIRT = 0x5C432E;
    private static final int DUST = 0x8C8476;
    private static final int CORRIDOR = 0xAEB8C0;
    private static final int CRACK_DARK = 0x07090B;

    private static final int TEAR = VoidRiftStrike.TEAR;
    private static final int OPEN = VoidRiftStrike.OPEN;
    private static final int CLOSING = VoidRiftStrike.CLOSING;
    private static final int COMPRESS = VoidRiftStrike.COMPRESS;
    private static final int SNAP = VoidRiftStrike.SNAP;
    private static final int DURATION = VoidRiftStrike.DURATION;

    private static final int OUTLINE = 72;
    private static final int BINS = 144;

    private final Map<Integer, Integer> grassColors = new HashMap<>();
    private final Map<Integer, List<List<float[]>>> crackCache = new HashMap<>();

    // =====================================================================================================
    // Frame: the rift's geometry for this frame (camera-relative), its outline and the clamp table
    // =====================================================================================================

    private static final class Frame {
        final Vec3 base;
        final Vector3f c, n, r;
        final float w, h, open, unstable, pull, age;
        final int seed;
        /** Unit vector from the plane away from the camera: the interior extends this way. */
        final Vector3f away;
        /** Tiny lift toward the camera so plane-aligned layers sit in front of the opaque surface. */
        final Vector3f lift;
        /** Grows with the openness and shrinks again while closing: the whole interior contracts with it. */
        final float contract;
        /** Deeper when seen obliquely (impossible depth). */
        final float depthScale;
        final float[] un = new float[OUTLINE], vn = new float[OUTLINE];
        final float[] bound = new float[BINS];

        Frame(FxContext ctx, StrikeEntity strike, float age) {
            this.age = age;
            this.seed = strike.getSeed();
            this.base = strike.position();
            Vec3 nn = VoidRiftStrike.normal(seed), rr = VoidRiftStrike.right(seed);
            this.unstable = VoidRiftStrike.instability(age);
            this.open = VoidRiftStrike.openness(age);
            this.pull = VoidRiftStrike.pull(age);
            float lurch = unstable * 0.25f;
            Random jitter = new Random(seed ^ (int) (age / 2) * 31L);
            this.c = ctx.rel(VoidRiftStrike.riftCenter(base)).add((jitter.nextFloat() - 0.5f) * lurch,
                    (jitter.nextFloat() - 0.5f) * lurch, (jitter.nextFloat() - 0.5f) * lurch);
            this.n = new Vector3f((float) nn.x, 0, (float) nn.z);
            this.r = new Vector3f((float) rr.x, 0, (float) rr.z);
            float breathe = 1.0f + unstable * 0.08f * Mth.sin(age * 1.7f);
            this.w = Math.max(0.02f, VoidRiftStrike.width(age) * breathe);
            this.h = Math.max(0.02f, VoidRiftStrike.height(age) * breathe);
            float side = Math.signum(-c.dot(n));
            if (side == 0) side = 1;
            this.away = new Vector3f(n).mul(-side);
            this.lift = new Vector3f(away).mul(-0.012f);
            this.contract = FxDraw.smooth(Math.min(1.0f, open * 1.15f));
            float view = Math.abs(new Vector3f(c).normalize().dot(n));
            this.depthScale = 1.0f + 1.3f * (1.0f - view);
            buildOutline();
        }

        /** Jagged, pointed tear in normalized plane space (u / (w/2), v / (h/2)), plus a polar clamp table. */
        private void buildOutline() {
            float s = (seed & 1023) * 0.01f;
            int bucket = (int) (age / 2);
            for (int i = 0; i < OUTLINE; i++) {
                float phi = Mth.TWO_PI * i / OUTLINE;
                float tooth = (FxDraw.hash(seed, i) - 0.5f) * (i % 2 == 0 ? 0.2f : 0.08f);
                float slow = 0.06f * Mth.sin(5 * phi + age * 0.05f + s) + 0.035f * Mth.sin(11 * phi - age * 0.08f);
                float twitch = unstable * 0.18f * (FxDraw.hash(i, bucket) - 0.5f);
                float k = 1.0f + tooth + slow + twitch;
                float cos = Mth.cos(phi);
                // |cos|^1.35 narrows the tear toward its ends: pointed tips instead of an oval
                un[i] = Math.signum(cos) * (float) Math.pow(Math.abs(cos), 1.35) * k;
                vn[i] = Mth.sin(phi) * k;
            }
            java.util.Arrays.fill(bound, 1.0f);
            for (int i = 0; i < OUTLINE; i++) {
                int j = (i + 1) % OUTLINE;
                float a1 = angle(un[i], vn[i]), a2 = angle(un[j], vn[j]);
                float r1 = Mth.sqrt(un[i] * un[i] + vn[i] * vn[i]), r2 = Mth.sqrt(un[j] * un[j] + vn[j] * vn[j]);
                if (a2 < a1) a2 += Mth.TWO_PI;
                int b1 = Mth.ceil(a1 / Mth.TWO_PI * BINS), b2 = Mth.floor(a2 / Mth.TWO_PI * BINS);
                for (int b = b1; b <= b2; b++) {
                    float t = (b * Mth.TWO_PI / BINS - a1) / Math.max(1.0e-5f, a2 - a1);
                    bound[Math.floorMod(b, BINS)] = Mth.lerp(Mth.clamp(t, 0, 1), r1, r2);
                }
            }
        }

        private static float angle(float u, float v) {
            float a = (float) Math.atan2(v, u);
            return a < 0 ? a + Mth.TWO_PI : a;
        }

        float boundAt(float angle) {
            float x = angle / Mth.TWO_PI * BINS;
            int i = Mth.floor(x);
            float t = x - i;
            return Mth.lerp(t, bound[Math.floorMod(i, BINS)], bound[Math.floorMod(i + 1, BINS)]);
        }

        /** World point (camera-relative) on the plane at plane coordinates u, v (blocks). */
        Vector3f at(float u, float v) {
            return new Vector3f(c).add(r.x * u, v, r.z * u);
        }

        /** Outline point i scaled by {@code scale}, slightly in front of the surface. */
        Vector3f edge(int i, float scale) {
            int k = Math.floorMod(i, OUTLINE);
            return at(un[k] * scale * w * 0.5f, vn[k] * scale * h * 0.5f).add(lift);
        }

        /** Point inside the interior: in-plane offsets (blocks) and depth behind the opening, contracted with the rift. */
        Vector3f interior(float iu, float iv, float depth) {
            return new Vector3f(c).add(r.x * iu * contract, iv * contract, r.z * iu * contract)
                    .add(new Vector3f(away).mul(depth * depthScale * contract));
        }

        /**
         * Maps an interior point onto the opening: along the view ray onto the plane, then pulled inside the outline.
         * Returns null if it cannot be seen through the plane.
         */
        Vector3f window(Vector3f p) {
            Vector3f q = FxDraw.project(p, c, n, 0.0f);
            if (q == null) return null;
            Vector3f rel = q.sub(c);
            float u = rel.dot(r), v = rel.y;
            float hw = w * 0.5f, hh = h * 0.5f;
            float nu = u / hw, nv = v / hh;
            float rho = Mth.sqrt(nu * nu + nv * nv);
            float limit = boundAt(angle(nu, nv)) * 0.975f;
            if (rho > limit) {
                u *= limit / rho;
                v *= limit / rho;
            }
            return at(u, v).add(lift);
        }

        /** How much a projected interior object shrinks on screen relative to its true size. */
        float projectionScale(Vector3f p) {
            Vector3f q = FxDraw.project(p, c, n, 0.0f);
            return q == null ? 0.0f : q.length() / Math.max(1.0e-3f, p.length());
        }
    }

    // =====================================================================================================
    // Passes
    // =====================================================================================================

    @Override
    public void renderDistortion(FxContext ctx, StrikeEntity strike, float age) {
        Frame f = new Frame(ctx, strike, age);
        float build = Mth.clamp(age / TEAR, 0.0f, 1.0f);
        if (age < SNAP) {
            // Space bending around the spot before anything is visible, then a lensing halo around the tear
            float size = 3.0f + 7.0f * build + Math.max(f.w, f.h) * 0.6f;
            float strength = 0.35f * build + 0.45f * f.open + 0.3f * f.unstable * (0.5f + 0.5f * Mth.sin(age * 0.9f));
            FxDraw.distortion(ctx, f.c, size, -0.85f, 0.15f, 0.35f + 0.5f * f.unstable, Math.min(1.0f, strength));
            // things being dragged in get their own small whirl
            if (f.pull > 0.2f) {
                Vec3 center = VoidRiftStrike.riftCenter(f.base);
                for (Entity e : ctx.level.getEntities((Entity) null, new AABB(center, center).inflate(24),
                        e -> e instanceof LivingEntity || e instanceof ItemEntity || e instanceof FallingBlockEntity)) {
                    Vec3 body = e.getBoundingBox().getCenter();
                    float close = (float) Mth.clamp(1.0 - body.distanceTo(center) / 24.0, 0.0, 1.0);
                    if (close < 0.1f) continue;
                    FxDraw.distortion(ctx, ctx.rel(body), Math.max(0.8f, e.getBbHeight()), -0.4f, 0.0f, 0.8f, close * f.pull * 0.8f);
                }
            }
        } else {
            float d = age - SNAP;
            if (d < 22) FxDraw.distortion(ctx, f.c, 2.0f + d * 1.8f, 0.9f, 0.0f, 0.0f, 1.0f - d / 22.0f);
            float scar = Mth.clamp(1.0f - d / 120.0f, 0.0f, 1.0f);
            FxDraw.distortion(ctx, f.c, 3.0f, -0.4f, 0.3f, 0.2f, 0.35f * scar);
        }
    }

    /** The opening itself (opaque, depth writing) and the pieces of ground it drags in. */
    @Override
    public void renderSolid(FxContext ctx, StrikeEntity strike, float age) {
        if (age >= SNAP) return;
        Frame f = new Frame(ctx, strike, age);
        if (f.open > 0.001f) {
            VertexConsumer vc = ctx.solid();
            float rr = FxDraw.r(VOID), gg = FxDraw.g(VOID), bb = FxDraw.b(VOID);
            for (int i = 0; i < OUTLINE; i++) {
                Vector3f a = f.at(f.un[i] * f.w * 0.5f, f.vn[i] * f.h * 0.5f);
                int j = (i + 1) % OUTLINE;
                Vector3f b = f.at(f.un[j] * f.w * 0.5f, f.vn[j] * f.h * 0.5f);
                FxDraw.vertex(vc, ctx.pose, f.c, rr, gg, bb, 1);
                FxDraw.vertex(vc, ctx.pose, a, rr, gg, bb, 1);
                FxDraw.vertex(vc, ctx.pose, b, rr, gg, bb, 1);
                FxDraw.vertex(vc, ctx.pose, f.c, rr, gg, bb, 1);
            }
        }
        renderPebbles(ctx, f);
    }

    @Override
    public void render(FxContext ctx, StrikeEntity strike, float age) {
        Frame f = new Frame(ctx, strike, age);
        float soft = FxDraw.setSoftness(FxDraw.DEFAULT_SOFTNESS);
        if (age >= SNAP) {
            renderAftermath(ctx, f, age - SNAP);
            FxDraw.setSoftness(soft);
            return;
        }
        renderWarning(ctx, f);
        renderGround(ctx, f);
        if (f.open > 0.001f) {
            FxDraw.setSoftness(0.0f); // everything on the plane is deliberately flush with it
            renderInterior(ctx, f);
            renderEdge(ctx, f);
            renderShards(ctx, f);
            FxDraw.setSoftness(FxDraw.DEFAULT_SOFTNESS);
            renderSuction(ctx, f);
        }
        if (age >= TEAR - 4 && age < TEAR + 40) renderTearLine(ctx, f);
        if (age >= COMPRESS - 30) renderSeam(ctx, f);
        FxDraw.setSoftness(soft);
    }

    // =====================================================================================================
    // Warning: the air goes wrong before anything opens
    // =====================================================================================================

    private void renderWarning(FxContext ctx, Frame f) {
        float build = Mth.clamp(f.age / TEAR, 0.0f, 1.0f);
        float fade = 1.0f - Mth.clamp((f.age - OPEN) / 40.0f, 0.0f, 1.0f);
        // Light is swallowed around the spot
        FxDraw.sprite(ctx, false, f.c, 5 + 12 * build + 10 * f.open, 0x000000, 0.25f * build + 0.2f * f.open, 0.0f);
        // Glitch fractures: brief hairline cracks in the air itself, more and more of them
        int bucket = (int) (f.age / 3);
        Random random = new Random(f.seed * 977L + bucket);
        int count = Math.round((1 + 4 * build) * fade + 5 * f.unstable);
        for (int i = 0; i < count; i++) {
            float spread = 2.5f + 5 * build;
            Vector3f start = f.at((random.nextFloat() - 0.5f) * spread, (random.nextFloat() - 0.5f) * spread * 1.6f)
                    .add(new Vector3f(f.n).mul((random.nextFloat() - 0.5f) * 2));
            Vector3f end = new Vector3f(start).add((random.nextFloat() - 0.5f) * 2.5f, (random.nextFloat() - 0.5f) * 3.0f, (random.nextFloat() - 0.5f) * 2.5f);
            List<Vector3f> crack = FxDraw.bolt(random, start, end, 0.7f, 3);
            FxDraw.hardRibbon(ctx, true, crack, 0.012f, PALE, 0.9f);
            FxDraw.ribbon(ctx, true, crack, 0.12f, 0.25f, 0.25f, ICE);
        }
        // Motes of light drawn toward the point where it will tear
        if (f.age < TEAR + 20) {
            for (int i = 0; i < 26; i++) {
                float life = (f.age * 0.012f + FxDraw.hash(i, f.seed)) % 1.0f;
                float a = FxDraw.hash(f.seed, i) * Mth.TWO_PI + life * 2.0f;
                float rr = (1.0f - life) * (6 + 6 * FxDraw.hash(i, 3));
                Vector3f p = new Vector3f(f.c).add(Mth.cos(a) * rr, (FxDraw.hash(i, 7) - 0.5f) * rr, Mth.sin(a) * rr);
                FxDraw.sprite(ctx, true, p, 0.07f, PALE, 0.9f * build * life, 0.0f);
            }
        }
    }

    /** The first fracture: a white-hot line ripping up and down through the air and into the ground. */
    private void renderTearLine(FxContext ctx, Frame f) {
        float t = Mth.clamp((f.age - (TEAR - 4)) / 14.0f, 0.0f, 1.0f);
        float fade = 1.0f - Mth.clamp((f.age - TEAR - 15) / 25.0f, 0.0f, 1.0f);
        float half = VoidRiftStrike.FULL_HEIGHT * 0.45f * FxDraw.easeOut(t);
        Random random = new Random(f.seed * 31L + (int) (f.age / 2));
        List<Vector3f> line = FxDraw.bolt(random, f.at(0, -half), f.at(0, half), 0.6f, 5);
        FxDraw.hardRibbon(ctx, true, line, 0.05f, 0xFFFFFF, fade);
        FxDraw.ribbon(ctx, true, line, 0.6f, 0.5f * fade, 0.5f * fade, ICE);
        // chromatic split of the fracture
        List<Vector3f> red = new ArrayList<>(), cyan = new ArrayList<>();
        for (Vector3f p : line) {
            red.add(new Vector3f(p).add(f.r.x * 0.07f, 0, f.r.z * 0.07f));
            cyan.add(new Vector3f(p).add(-f.r.x * 0.07f, 0, -f.r.z * 0.07f));
        }
        FxDraw.hardRibbon(ctx, true, red, 0.02f, FRINGE_RED, 0.7f * fade);
        FxDraw.hardRibbon(ctx, true, cyan, 0.02f, FRINGE_CYAN, 0.7f * fade);
        if (f.age >= TEAR && f.age < TEAR + 6) {
            FxDraw.sprite(ctx, true, f.c, 14.0f, PALE, 0.6f * (1.0f - (f.age - TEAR) / 6.0f), 0.0f);
        }
    }

    // =====================================================================================================
    // Interior: the impossible space behind the tear
    // =====================================================================================================

    /** One opaque interior face, already mapped onto the opening, with its true depth for sorting. */
    private record Face(Vector3f[] points, int rgb, float alpha, float depth) {
    }

    private void renderInterior(FxContext ctx, Frame f) {
        float alpha = Mth.clamp(f.open * 3.0f, 0.0f, 1.0f);
        float spin = f.age * 0.004f + f.unstable * 0.3f * Mth.sin(f.age * 0.21f);

        // ---- far: a nebula haze and a black sun
        for (int i = 0; i < 7; i++) {
            Vector3f p = f.interior(rot(i, (FxDraw.hash(i, f.seed) - 0.5f) * 40, (FxDraw.hash(f.seed, i) - 0.5f) * 50, spin, true),
                    rot(i, (FxDraw.hash(i, f.seed) - 0.5f) * 40, (FxDraw.hash(f.seed, i) - 0.5f) * 50, spin, false),
                    90 + 60 * FxDraw.hash(i, 9));
            interiorDisc(ctx, f, p, 18 + 14 * FxDraw.hash(i, 4), true, i % 2 == 0 ? NEBULA : 0x1A3040, 0.35f * alpha, 0.0f);
        }
        Vector3f sun = f.interior(rot(0, 6, 9, spin * 0.5f, true), rot(0, 6, 9, spin * 0.5f, false), 160);
        interiorDisc(ctx, f, sun, 16, true, ICE, 0.35f * alpha, 0.0f);
        interiorDisc(ctx, f, sun, 10.5f, true, PALE, 0.7f * alpha, 0.0f);
        interiorDisc(ctx, f, sun, 9.0f, false, VOID, alpha, alpha);

        // ---- an endless regress of the tear itself, each copy twisted further and somehow no smaller
        for (int k = 1; k <= 6; k++) {
            float depth = 3.0f * (float) Math.pow(k, 1.6);
            float scale = 0.5f + 0.32f * k;
            float twist = k * 0.4f + f.age * 0.006f * k + spin;
            List<Vector3f> loop = new ArrayList<>(OUTLINE + 1);
            for (int i = 0; i <= OUTLINE; i++) {
                int j = i % OUTLINE;
                float u = f.un[j] * f.w * 0.5f * scale, v = f.vn[j] * f.h * 0.5f * scale;
                float cu = u * Mth.cos(twist) - v * Mth.sin(twist), cv = u * Mth.sin(twist) + v * Mth.cos(twist);
                Vector3f p = f.window(f.interior(cu, cv, depth));
                if (p != null) loop.add(p);
            }
            planeLine(ctx, f, loop, 0.045f / Mth.sqrt(k), true, k % 2 == 0 ? ICE : PALE, alpha * (0.6f - k * 0.07f));
        }

        // ---- solid pieces: drifting chunks of the world and a corridor that goes where corridors cannot
        List<Face> faces = new ArrayList<>();
        collectFragments(f, faces, spin);
        collectCorridor(f, faces, spin);
        faces.sort(Comparator.comparingDouble((Face face) -> -face.depth()));
        VertexConsumer vc = ctx.matter();
        for (Face face : faces) {
            float r = FxDraw.r(face.rgb()), g = FxDraw.g(face.rgb()), b = FxDraw.b(face.rgb());
            for (Vector3f p : face.points()) FxDraw.vertex(vc, ctx.pose, p, r, g, b, face.alpha() * alpha);
        }

        // ---- specks of light drifting ever deeper, and matter streaming in from the edge
        for (int i = 0; i < 90; i++) {
            float drift = (FxDraw.hash(i, 17) + f.age * 0.0015f * (1 + f.pull)) % 1.0f;
            float depth = 3 + drift * 120;
            Vector3f p = f.interior(rot(i, (FxDraw.hash(f.seed, i) - 0.5f) * 30, (FxDraw.hash(i, f.seed) - 0.5f) * 36, spin, true),
                    rot(i, (FxDraw.hash(f.seed, i) - 0.5f) * 30, (FxDraw.hash(i, f.seed) - 0.5f) * 36, spin, false), depth);
            float twinkle = 0.5f + 0.5f * Mth.sin(f.age * (0.1f + FxDraw.hash(i, 3) * 0.3f) + i);
            interiorDisc(ctx, f, p, 0.12f + 0.12f * FxDraw.hash(i, 9), true, i % 5 == 0 ? ICE : PALE, twinkle * alpha * (1.0f - 0.5f * drift), 0.0f);
        }
        for (int i = 0; i < 26; i++) {
            float life = (f.age * 0.01f * (0.5f + f.pull) + FxDraw.hash(i, 41)) % 1.0f;
            int k = (int) (FxDraw.hash(f.seed, i + 41) * OUTLINE);
            float u0 = f.un[k] * f.w * 0.48f, v0 = f.vn[k] * f.h * 0.48f;
            Vector3f now = f.window(f.interior(u0 * (1 - life), v0 * (1 - life), life * 40));
            Vector3f before = f.window(f.interior(u0 * (1 - life + 0.08f), v0 * (1 - life + 0.08f), Math.max(0, life - 0.08f) * 40));
            if (now != null && before != null) {
                planeLine(ctx, f, List.of(before, now), 0.03f, true, PALE, (1.0f - life) * 0.8f * alpha);
            }
        }

        // ---- when unstable, the whole space flickers
        if (f.unstable > 0) {
            float flash = Math.max(0, Mth.sin(f.age * 2.3f)) * f.unstable;
            Vector3f deep = f.interior(0, 0, 40);
            interiorDisc(ctx, f, deep, 30, true, PALE, 0.25f * flash * alpha, 0.0f);
        }
    }

    /** Rotates interior offsets (u, v) around the rift axis; returns u when {@code wantU}, else v. */
    private static float rot(int i, float u, float v, float angle, boolean wantU) {
        float a = angle * (0.6f + 0.8f * FxDraw.hash(i, 61));
        return wantU ? u * Mth.cos(a) - v * Mth.sin(a) : u * Mth.sin(a) + v * Mth.cos(a);
    }

    /** Chunks of a broken world, tumbling slowly at angles no gravity would allow. */
    private void collectFragments(Frame f, List<Face> faces, float spin) {
        int grass = grassColors.getOrDefault(f.seed, 0x5F8A3A);
        Vector3f light = new Vector3f(0.35f, 0.8f, -0.45f).normalize();
        for (int i = 0; i < 16; i++) {
            float depth = 8 + 62 * FxDraw.hash(i, f.seed + 3);
            float orbit = spin * (0.5f + FxDraw.hash(i, 5)) + FxDraw.hash(f.seed, i + 3) * Mth.TWO_PI;
            float radius = 3 + 14 * FxDraw.hash(i, 6);
            float iu = Mth.cos(orbit) * radius, iv = Mth.sin(orbit) * radius * 1.2f + Mth.sin(f.age * 0.02f + i) * 0.6f;
            Vector3f center = f.interior(iu, iv, depth);
            Quaternionf rotation = FxMesh.tumble(f.seed + i * 13, f.age, 0.004f);
            if (i % 3 == 0) rotation.rotateZ(Mth.PI); // upside down
            int cubes = 1 + (int) (FxDraw.hash(i, 8) * 3);
            boolean stone = FxDraw.hash(i, 11) < 0.35f;
            for (int k = 0; k < cubes; k++) {
                float size = (1.2f + 2.0f * FxDraw.hash(i * 7 + k, 12)) * f.contract;
                Vector3f offset = rotation.transform(new Vector3f((k - cubes / 2.0f) * size * 0.9f, (FxDraw.hash(k, i) - 0.5f) * size, 0));
                cube(f, faces, new Vector3f(center).add(offset), size, rotation, stone ? STONE : grass, stone ? STONE : DIRT, light, depth);
            }
        }
    }

    private static final float[][] CUBE_FACES = {
            // normal xyz, then 4 corners as unit cube offsets
            {0, 1, 0, -1, 1, -1, 1, 1, -1, 1, 1, 1, -1, 1, 1},
            {0, -1, 0, -1, -1, 1, 1, -1, 1, 1, -1, -1, -1, -1, -1},
            {1, 0, 0, 1, -1, -1, 1, -1, 1, 1, 1, 1, 1, 1, -1},
            {-1, 0, 0, -1, -1, 1, -1, -1, -1, -1, 1, -1, -1, 1, 1},
            {0, 0, 1, -1, -1, 1, -1, 1, 1, 1, 1, 1, 1, -1, 1},
            {0, 0, -1, 1, -1, -1, 1, 1, -1, -1, 1, -1, -1, -1, -1}};

    private static void cube(Frame f, List<Face> faces, Vector3f center, float size, Quaternionf rotation,
                             int topRgb, int sideRgb, Vector3f light, float depth) {
        float half = size * 0.5f;
        for (int fi = 0; fi < CUBE_FACES.length; fi++) {
            float[] spec = CUBE_FACES[fi];
            Vector3f normal = rotation.transform(new Vector3f(spec[0], spec[1], spec[2]));
            Vector3f faceCenter = new Vector3f(normal).mul(half).add(center);
            if (normal.dot(faceCenter) >= 0) continue; // facing away from the camera
            Vector3f[] points = new Vector3f[4];
            boolean visible = true;
            for (int k = 0; k < 4; k++) {
                Vector3f corner = rotation.transform(new Vector3f(spec[3 + k * 3], spec[4 + k * 3], spec[5 + k * 3]).mul(half)).add(center);
                points[k] = f.window(corner);
                if (points[k] == null) visible = false;
            }
            if (!visible) continue;
            float shade = 0.35f + 0.65f * Math.max(0.0f, normal.dot(light));
            int rgb = scale(fi == 0 ? topRgb : sideRgb, shade);
            rgb = FxDraw.lerpColor(rgb, ABYSS, Mth.clamp(depth / 95.0f, 0.0f, 0.85f)); // fades into the deep
            faces.add(new Face(points, rgb, 1.0f, faceCenter.length()));
        }
    }

    /** A pale corridor of door frames that runs straight back, turns sideways, then climbs, twisting as it goes. */
    private void collectCorridor(Frame f, List<Face> faces, float spin) {
        int frames = 11;
        float startU = -2.5f + 5 * FxDraw.hash(f.seed, 71) - 2.5f;
        float[] prevLeft = null, prevRight = null;
        Vector3f[] prevBottom = null;
        for (int k = 0; k < frames; k++) {
            float s = k / (float) (frames - 1);
            // path in interior space: back, then sideways, then up
            float depth = 4 + 30 * Math.min(1, s * 2) + 8 * Math.max(0, s - 0.5f);
            float iu = startU + 18 * smoothstep(0.35f, 0.75f, s);
            float iv = -3 + 20 * smoothstep(0.65f, 1.0f, s);
            float roll = s * 1.8f + spin * 0.5f;
            float cr = Mth.cos(roll), sr = Mth.sin(roll);
            float hw = 1.6f, hh = 2.4f;
            float[][] corners = {{-hw, -hh}, {hw, -hh}, {hw, hh}, {-hw, hh}};
            Vector3f[] p = new Vector3f[4];
            for (int i = 0; i < 4; i++) {
                float cu = corners[i][0] * cr - corners[i][1] * sr, cv = corners[i][0] * sr + corners[i][1] * cr;
                p[i] = f.interior(iu + cu, iv + cv, depth);
            }
            int rgb = FxDraw.lerpColor(CORRIDOR, ABYSS, Mth.clamp(depth / 70.0f, 0.0f, 0.8f));
            // frame bars
            for (int i = 0; i < 4; i++) {
                Vector3f a = p[i], b = p[(i + 1) % 4];
                Vector3f inward = new Vector3f(p[(i + 2) % 4]).sub(a).mul(0.07f);
                Vector3f inwardB = new Vector3f(p[(i + 3) % 4]).sub(b).mul(0.07f);
                addFace(f, faces, new Vector3f[]{a, b, new Vector3f(b).add(inwardB), new Vector3f(a).add(inward)}, rgb, depth);
            }
            // floor between this frame and the previous one, checkered
            if (prevBottom != null) {
                int floor = FxDraw.lerpColor(k % 2 == 0 ? 0x2A3138 : 0x4A545C, ABYSS, Mth.clamp(depth / 70.0f, 0.0f, 0.85f));
                addFace(f, faces, new Vector3f[]{prevBottom[0], prevBottom[1], p[1], p[0]}, floor, depth);
            }
            prevBottom = new Vector3f[]{p[0], p[1]};
        }
    }

    private static void addFace(Frame f, List<Face> faces, Vector3f[] interiorPoints, int rgb, float depth) {
        Vector3f[] mapped = new Vector3f[4];
        for (int i = 0; i < 4; i++) {
            mapped[i] = f.window(interiorPoints[i]);
            if (mapped[i] == null) return;
        }
        faces.add(new Face(mapped, rgb, 1.0f, interiorPoints[0].length()));
    }

    private static float smoothstep(float a, float b, float x) {
        return FxDraw.smooth((x - a) / (b - a));
    }

    /** A round object inside the void (seen through the opening), shrunk by perspective and clamped to the tear. */
    private void interiorDisc(FxContext ctx, Frame f, Vector3f interiorCenter, float radius, boolean additive,
                              int rgb, float aCenter, float aEdge) {
        if (aCenter <= 0.002f && aEdge <= 0.002f) return;
        float t = f.projectionScale(interiorCenter);
        Vector3f center = FxDraw.project(interiorCenter, f.c, f.n, 0.0f);
        if (t <= 0 || center == null) return;
        Vector3f rel = new Vector3f(center).sub(f.c);
        float cu = rel.dot(f.r), cv = rel.y, rr = radius * t;
        Vector3f mid = f.window(interiorCenter);
        if (mid == null) return;
        VertexConsumer vc = ctx.layer(additive);
        float r = FxDraw.r(rgb), g = FxDraw.g(rgb), b = FxDraw.b(rgb);
        int segments = 16;
        for (int i = 0; i < segments; i++) {
            float a1 = Mth.TWO_PI * i / segments, a2 = Mth.TWO_PI * (i + 1) / segments;
            Vector3f p1 = clampPlane(f, cu + Mth.cos(a1) * rr, cv + Mth.sin(a1) * rr);
            Vector3f p2 = clampPlane(f, cu + Mth.cos(a2) * rr, cv + Mth.sin(a2) * rr);
            FxDraw.vertex(vc, ctx.pose, mid, r, g, b, aCenter);
            FxDraw.vertex(vc, ctx.pose, p1, r, g, b, aEdge);
            FxDraw.vertex(vc, ctx.pose, p2, r, g, b, aEdge);
            FxDraw.vertex(vc, ctx.pose, mid, r, g, b, aCenter);
        }
    }

    private static Vector3f clampPlane(Frame f, float u, float v) {
        float hw = f.w * 0.5f, hh = f.h * 0.5f;
        float nu = u / hw, nv = v / hh;
        float rho = Mth.sqrt(nu * nu + nv * nv);
        float a = (float) Math.atan2(nv, nu);
        if (a < 0) a += Mth.TWO_PI;
        float limit = f.boundAt(a) * 0.975f;
        if (rho > limit) {
            u *= limit / rho;
            v *= limit / rho;
        }
        return f.at(u, v).add(f.lift);
    }

    /** A thin line lying in the rift plane (constant width, independent of view). */
    private static void planeLine(FxContext ctx, Frame f, List<Vector3f> points, float halfWidth, boolean additive, int rgb, float a) {
        if (points.size() < 2 || a <= 0.002f) return;
        VertexConsumer vc = ctx.layer(additive);
        float r = FxDraw.r(rgb), g = FxDraw.g(rgb), b = FxDraw.b(rgb);
        for (int i = 0; i < points.size() - 1; i++) {
            Vector3f p1 = points.get(i), p2 = points.get(i + 1);
            Vector3f along = new Vector3f(p2).sub(p1);
            Vector3f side = along.cross(f.n, new Vector3f());
            if (side.lengthSquared() < 1.0e-10f) continue;
            side.normalize().mul(halfWidth);
            FxDraw.vertex(vc, ctx.pose, new Vector3f(p1).sub(side), r, g, b, a);
            FxDraw.vertex(vc, ctx.pose, new Vector3f(p1).add(side), r, g, b, a);
            FxDraw.vertex(vc, ctx.pose, new Vector3f(p2).add(side), r, g, b, a);
            FxDraw.vertex(vc, ctx.pose, new Vector3f(p2).sub(side), r, g, b, a);
        }
    }

    // =====================================================================================================
    // The boundary
    // =====================================================================================================

    private void renderEdge(FxContext ctx, Frame f) {
        // While closing the energy of the whole tear is squeezed into its edge
        float squeeze = f.age >= CLOSING ? Mth.clamp((f.age - CLOSING) / (float) (COMPRESS - CLOSING), 0.0f, 1.0f) : 0.0f;
        float glow = 0.8f + 0.2f * Mth.sin(f.age * 0.4f) + 1.5f * squeeze + 0.4f * f.unstable;
        float wobble = 1.0f + f.unstable * 0.02f * Mth.sin(f.age * 3.1f);
        band(ctx, f, 0.965f, 0.985f, FRINGE_CYAN, 0.55f * Math.min(1, glow));
        band(ctx, f, 0.985f, 1.0f, PALE, Math.min(1.0f, glow));
        band(ctx, f, 1.0f * wobble, 1.028f * wobble, FRINGE_RED, 0.5f * Math.min(1, glow));
        // soft cold halo
        List<Vector3f> loop = new ArrayList<>(OUTLINE + 2);
        for (int i = 0; i <= OUTLINE + 1; i++) loop.add(f.edge(i, 1.04f));
        FxDraw.ribbon(ctx, true, loop, 0.35f + 0.5f * f.open + squeeze, 0.18f * glow, 0.18f * glow, ICE);

        // Hairline fractures spreading off the edge into the air
        int bucket = (int) (f.age / 4);
        Random random = new Random(f.seed ^ (bucket * 613L));
        int fractures = 2 + Math.round(4 * f.unstable);
        for (int i = 0; i < fractures; i++) {
            int k = random.nextInt(OUTLINE);
            Vector3f start = f.edge(k, 1.0f);
            Vector3f out = new Vector3f(start).sub(f.c);
            out.y *= 0.4f;
            out.normalize().mul(1.0f + random.nextFloat() * (2.0f + 3.0f * f.unstable));
            List<Vector3f> crack = FxDraw.bolt(random, start, new Vector3f(start).add(out), 0.5f, 3);
            FxDraw.hardRibbon(ctx, true, crack, 0.012f, PALE, 0.8f);
        }
    }

    /** A crisp band between two scaled copies of the outline, in the rift plane. */
    private static void band(FxContext ctx, Frame f, float inner, float outer, int rgb, float a) {
        if (a <= 0.002f) return;
        VertexConsumer vc = ctx.glow();
        float r = FxDraw.r(rgb), g = FxDraw.g(rgb), b = FxDraw.b(rgb);
        for (int i = 0; i < OUTLINE; i++) {
            FxDraw.vertex(vc, ctx.pose, f.edge(i, inner), r, g, b, a);
            FxDraw.vertex(vc, ctx.pose, f.edge(i + 1, inner), r, g, b, a);
            FxDraw.vertex(vc, ctx.pose, f.edge(i + 1, outer), r, g, b, a);
            FxDraw.vertex(vc, ctx.pose, f.edge(i, outer), r, g, b, a);
        }
    }

    /** Pieces of the world breaking off the edge and falling inward, shrinking as they go. */
    private void renderShards(FxContext ctx, Frame f) {
        if (f.pull <= 0.05f) return;
        int grass = grassColors.getOrDefault(f.seed, 0x5F8A3A);
        float side = -Math.signum(f.away.dot(f.n));
        for (int i = 0; i < 34; i++) {
            float life = (f.age * 0.006f * (0.6f + f.pull) + FxDraw.hash(i, f.seed + 9)) % 1.0f;
            int k = (int) (FxDraw.hash(f.seed + 9, i) * OUTLINE);
            float reach = 1.08f * (1.0f - (float) Math.pow(life, 1.4));
            Vector3f center = f.at(f.un[k] * reach * f.w * 0.5f, f.vn[k] * reach * f.h * 0.5f)
                    .add(new Vector3f(f.n).mul(side * (0.15f + 0.3f * (1 - life))));
            float size = (0.18f + 0.35f * FxDraw.hash(i, 13)) * (1.0f - life) * Math.min(1.0f, f.open * 2);
            if (size < 0.02f) continue;
            float spin = f.age * 0.08f * (FxDraw.hash(i, 21) - 0.5f) + i;
            Vector3f a = new Vector3f(Mth.cos(spin), Mth.sin(spin * 1.3f), Mth.sin(spin)).normalize().mul(size);
            Vector3f b = new Vector3f(-Mth.sin(spin * 0.7f), Mth.cos(spin), Mth.cos(spin * 0.4f)).normalize().mul(size * 0.8f);
            Vector3f p1 = new Vector3f(center).add(a), p2 = new Vector3f(center).add(b), p3 = new Vector3f(center).sub(a).sub(b);
            int rgb = i % 3 == 0 ? grass : i % 3 == 1 ? STONE : DIRT;
            float fade = Math.min(1.0f, life * 8);
            FxDraw.triangle(ctx, false, p1, p2, p3, rgb, fade, fade, fade);
            FxDraw.hardRibbon(ctx, true, List.of(p1, p2, p3, p1), 0.01f, PALE, 0.7f * fade);
        }
    }

    // =====================================================================================================
    // Around it: the ground, the dust, the suction
    // =====================================================================================================

    /** Cold white fractures spreading over the ground from the fissure, and the void bleeding into the soil. */
    private void renderGround(FxContext ctx, Frame f) {
        float grow;
        if (f.age < TEAR) grow = 0.4f * Mth.clamp((f.age - 20) / (TEAR - 20.0f), 0.0f, 1.0f);
        else if (f.age < CLOSING) grow = 0.4f + 0.6f * Mth.clamp((f.age - TEAR) / (float) (OPEN - TEAR), 0.0f, 1.0f);
        else grow = 1.0f - Mth.clamp((f.age - CLOSING) / (float) (COMPRESS - CLOSING), 0.0f, 1.0f); // knitting back together
        if (grow <= 0.001f) return;
        float flicker = 0.75f + 0.25f * Mth.sin(f.age * 0.7f);

        for (List<float[]> crack : cracks(f)) {
            List<Vec3> points = new ArrayList<>();
            for (float[] p : crack) {
                if (p[2] > grow) break;
                points.add(f.base.add(p[0], 0, p[1]));
            }
            int n = points.size();
            if (n < 2) continue;
            float[] wide = new float[n], thin = new float[n], dark = new float[n], bright = new float[n];
            for (int i = 0; i < n; i++) {
                float taper = 1.0f - 0.8f * i / (float) n;
                wide[i] = 0.35f * taper;
                thin[i] = 0.07f * taper;
                dark[i] = 0.75f;
                bright[i] = flicker * taper;
            }
            FxDraw.drapedRibbon(ctx, false, points, wide, dark, CRACK_DARK, 0.04f);
            FxDraw.drapedRibbon(ctx, true, points, thin, bright, PALE, 0.05f);
            FxDraw.drapedRibbon(ctx, true, points, wide, scaleArray(bright, 0.3f), ICE, 0.05f);
        }

        // Where the tear meets the ground: glowing lips on both sides of the fissure
        if (f.open > 0.01f) {
            Vec3 center = VoidRiftStrike.riftCenter(f.base);
            Vec3 normal = VoidRiftStrike.normal(f.seed), right = VoidRiftStrike.right(f.seed);
            for (int sideSign = -1; sideSign <= 1; sideSign += 2) {
                List<Vec3> lip = new ArrayList<>();
                for (float u = -f.w * 0.5f; u <= f.w * 0.5f; u += 0.4f) {
                    Vec3 p = center.add(right.scale(u)).add(normal.scale(0.85 * sideSign));
                    double groundV = FxPresets.groundY(ctx.level, p.x, p.z) - center.y;
                    double nu = u / (f.w * 0.5), nv = groundV / (f.h * 0.5);
                    if (nu * nu + nv * nv < 0.85) lip.add(p);
                }
                int n = lip.size();
                if (n < 2) continue;
                float[] widths = new float[n], alphas = new float[n];
                for (int i = 0; i < n; i++) {
                    widths[i] = 0.18f;
                    alphas[i] = 0.9f * Math.min(1.0f, f.open * 3);
                }
                FxDraw.drapedRibbon(ctx, true, lip, widths, alphas, ICE, 0.06f);
            }
            FxDraw.drapedDisc(ctx, false, f.base, 3 + 6 * f.open, VOID, 0.55f * f.open, 0.0f, 0.03f);
        }
    }

    /** Ground cracks: x, z offsets from the base and when they appear (0..1), radiating from the fissure line. */
    private List<List<float[]>> cracks(Frame f) {
        if (crackCache.size() > 16) crackCache.clear();
        return crackCache.computeIfAbsent(f.seed, s -> {
            Random random = new Random(s * 131L);
            Vec3 right = VoidRiftStrike.right(s), normal = VoidRiftStrike.normal(s);
            List<List<float[]>> out = new ArrayList<>();
            for (int i = 0; i < 14; i++) {
                float u = (random.nextFloat() - 0.5f) * VoidRiftStrike.FULL_WIDTH * 0.8f;
                float sideSign = i % 2 == 0 ? 1 : -1;
                double heading = Math.atan2(normal.z * sideSign, normal.x * sideSign) + (random.nextDouble() - 0.5) * 1.4;
                float x = (float) (right.x * u + normal.x * sideSign * 0.8), z = (float) (right.z * u + normal.z * sideSign * 0.8);
                growCrack(out, random, x, z, heading, 5 + random.nextFloat() * 10, 0.0f, 0);
            }
            return out;
        });
    }

    private static void growCrack(List<List<float[]>> out, Random random, float x, float z, double heading, float length,
                                  float startAppear, int depth) {
        List<float[]> crack = new ArrayList<>();
        float travelled = 0;
        while (travelled < length) {
            crack.add(new float[]{x, z, Math.min(1.0f, startAppear + travelled / 16.0f)});
            heading += (random.nextDouble() - 0.5) * 0.9;
            x += (float) Math.cos(heading) * 0.6f;
            z += (float) Math.sin(heading) * 0.6f;
            travelled += 0.6f;
            if (depth < 2 && random.nextFloat() < 0.08f) {
                growCrack(out, random, x, z, heading + (random.nextBoolean() ? 1 : -1) * (0.5 + random.nextDouble() * 0.7),
                        2 + random.nextFloat() * 4, startAppear + travelled / 16.0f, depth + 1);
            }
        }
        out.add(crack);
    }

    /** Dust and loose grit dragged off the ground in spiralling streams, and the halo around things being pulled. */
    private void renderSuction(FxContext ctx, Frame f) {
        if (f.pull <= 0.02f) return;
        FxDraw.Puffs dust = new FxDraw.Puffs();
        for (int i = 0; i < 30; i++) {
            float life = (f.age * 0.006f * (0.5f + f.pull) + FxDraw.hash(i, f.seed + 5)) % 1.0f;
            float a0 = FxDraw.hash(f.seed + 5, i) * Mth.TWO_PI;
            float start = 7 + 16 * FxDraw.hash(i, 77);
            float rr = start * (float) Math.pow(1.0f - life, 1.3f);
            float a = a0 + life * 2.6f;
            double x = f.base.x + Mth.cos(a) * rr, z = f.base.z + Mth.sin(a) * rr;
            float ground = FxDraw.groundRel(ctx, x, z);
            Vector3f p = new Vector3f((float) (x - ctx.cam.x), Mth.lerp(life * life, ground + 0.8f, f.c.y), (float) (z - ctx.cam.z));
            float fade = Math.min(1.0f, life * 5) * (1.0f - life * life);
            dust.add(p, 0.8f + 1.6f * (1.0f - life), DUST, 0.22f * fade * Math.min(1.0f, f.pull));
        }
        dust.draw(ctx);

        Vec3 center = VoidRiftStrike.riftCenter(f.base);
        for (Entity entity : ctx.level.getEntities((Entity) null, new AABB(center, center).inflate(26),
                e -> e instanceof LivingEntity || e instanceof ItemEntity)) {
            Vec3 body = entity.getBoundingBox().getCenter();
            float close = (float) Mth.clamp(1.0 - body.distanceTo(center) / 26.0, 0.0, 1.0);
            if (close <= 0.05f) continue;
            Vector3f p = ctx.rel(body.x, body.y, body.z);
            float size = Math.max(0.6f, entity.getBbHeight() * 0.8f);
            FxDraw.spriteRing(ctx, true, p, size * 0.9f, 0.08f, ICE, 0.3f * close * f.pull);
            Vector3f toRift = new Vector3f(f.c).sub(p).normalize();
            for (int i = 0; i < 3; i++) {
                Vector3f from = new Vector3f(p).add((FxDraw.hash(i, entity.getId()) - 0.5f) * size, (FxDraw.hash(entity.getId(), i) - 0.5f) * size, 0);
                FxDraw.beam(ctx, true, from, new Vector3f(from).add(new Vector3f(toRift).mul(1.5f + 3.5f * close)), 0.04f, PALE,
                        0.5f * close * f.pull, 0.0f);
            }
        }
    }

    /** Small 3D rocks spiralling up into the tear; once the pull lets go they drop and settle. */
    private void renderPebbles(FxContext ctx, Frame f) {
        if (f.age < OPEN - 30 || f.age > COMPRESS + 60) return;
        float pathAge = Math.min(f.age, CLOSING);
        for (int i = 0; i < 18; i++) {
            float period = 120 + 60 * FxDraw.hash(i, 3);
            float life = ((pathAge - (OPEN - 30)) / period + FxDraw.hash(i, f.seed + 2)) % 1.0f;
            float a0 = FxDraw.hash(f.seed + 2, i) * Mth.TWO_PI;
            float start = 6 + 12 * FxDraw.hash(i, 8);
            float rr = start * (1.0f - life);
            float a = a0 + life * 3.0f;
            double x = f.base.x + Mth.cos(a) * rr, z = f.base.z + Mth.sin(a) * rr;
            float ground = FxDraw.groundRel(ctx, x, z);
            float y = Mth.lerp(life, ground + 0.4f, f.c.y);
            if (f.age > CLOSING) {
                float tau = f.age - CLOSING;
                y = Math.max(ground + 0.15f, y - 0.025f * tau * tau); // released: falls back to the ground
            }
            if (life > 0.93f && f.age <= CLOSING) continue; // already swallowed
            float size = (0.15f + 0.25f * FxDraw.hash(i, 9)) * (1.0f - 0.6f * life);
            Vector3f p = new Vector3f((float) (x - ctx.cam.x), y, (float) (z - ctx.cam.z));
            float spinSpeed = f.age > CLOSING ? 0.0f : 0.15f;
            FxMesh.drawRock(ctx, p, size, FxMesh.tumble(f.seed + i, pathAge, spinSpeed), f.seed + i, 0,
                    i % 2 == 0 ? STONE : DIRT, PALE, 0.0f, null);
        }
    }

    // =====================================================================================================
    // Closing and aftermath
    // =====================================================================================================

    /** The tear knitting itself into a blazing seam, then collapsing to a point. */
    private void renderSeam(FxContext ctx, Frame f) {
        float t = Mth.clamp((f.age - (COMPRESS - 30)) / (SNAP - (COMPRESS - 30.0f)), 0.0f, 1.0f);
        float half = Math.max(0.05f, f.h * 0.5f);
        Random random = new Random(f.seed * 17L + (int) (f.age / 2));
        List<Vector3f> seam = FxDraw.bolt(random, f.at(0, -half), f.at(0, half), 0.25f * (1 - t), 4);
        float intensity = 0.4f + 0.6f * t;
        FxDraw.hardRibbon(ctx, true, seam, 0.03f + 0.06f * t, 0xFFFFFF, intensity);
        FxDraw.ribbon(ctx, true, seam, 0.6f + 1.2f * t, 0.45f * intensity, 0.45f * intensity, ICE);
        FxDraw.sprite(ctx, true, f.c, 1.5f + 5.0f * t, PALE, 0.5f * t, 0.0f);
        // energy rings converging on the seam
        for (int i = 0; i < 3; i++) {
            float phase = (t * 3 + i / 3.0f) % 1.0f;
            float s = 14 * (1.0f - phase);
            FxDraw.ring(ctx, true, f.c, f.r, new Vector3f(0, 1, 0), s * 0.5f, s, 0.25f, PALE, phase * 0.6f * t, 48);
        }
    }

    private void renderAftermath(FxContext ctx, Frame f, float d) {
        // the snap
        if (d < 10) FxDraw.sprite(ctx, true, f.c, 60.0f, PALE, 1.0f - d / 10.0f, 0.0f);
        if (d < 4) FxDraw.sprite(ctx, true, f.c, 6.0f, 0xFFFFFF, 1.0f, 0.0f);
        if (d < 26) {
            float fade = 1.0f - d / 26.0f;
            FxDraw.shell(ctx, true, f.c, d * 2.0f, ICE, 0.7f * fade, true);
            FxDraw.drapedRing(ctx, true, f.base, d * 1.6f, 0.9f, ICE, fade, 0.08f);
            FxDraw.Puffs dust = new FxDraw.Puffs();
            for (int i = 0; i < 24; i++) {
                float a = Mth.TWO_PI * i / 24 + FxDraw.hash(i, f.seed);
                double x = f.base.x + Mth.cos(a) * d * 1.4f, z = f.base.z + Mth.sin(a) * d * 1.4f;
                Vector3f p = new Vector3f((float) (x - ctx.cam.x), FxDraw.groundRel(ctx, x, z) + 0.6f + d * 0.05f, (float) (z - ctx.cam.z));
                dust.add(p, 1.2f + d * 0.08f, DUST, 0.45f * fade);
            }
            dust.draw(ctx);
        }
        // a scar left hanging in the air, flickering out
        float scar = Mth.clamp(1.0f - d / 120.0f, 0.0f, 1.0f);
        if (scar > 0) {
            Random random = new Random(f.seed * 7L);
            List<Vector3f> line = FxDraw.bolt(random, f.at(0, -3.5f), f.at(0, 3.5f), 0.4f, 4);
            float flicker = (0.5f + 0.5f * Mth.sin(d * 0.9f)) * (FxDraw.hash((int) (d / 3), f.seed) > 0.15f ? 1.0f : 0.2f);
            FxDraw.hardRibbon(ctx, true, line, 0.02f, PALE, scar * flicker);
            FxDraw.ribbon(ctx, true, line, 0.3f, 0.25f * scar * flicker, 0.25f * scar * flicker, ICE);
        }
        // motes drifting down and settling
        float settle = Mth.clamp(1.0f - d / 140.0f, 0.0f, 1.0f);
        for (int i = 0; settle > 0 && i < 30; i++) {
            float a = FxDraw.hash(i, f.seed + 4) * Mth.TWO_PI;
            float rr = 1 + 6 * FxDraw.hash(f.seed + 4, i);
            Vector3f p = new Vector3f(f.c).add(Mth.cos(a) * rr, 4 - d * (0.05f + 0.04f * FxDraw.hash(i, 2)), Mth.sin(a) * rr);
            FxDraw.sprite(ctx, true, p, 0.06f, PALE, 0.8f * settle, 0.0f);
        }
        FxDraw.drapedDisc(ctx, false, f.base, 6.0f, VOID, 0.5f * scar, 0.0f, 0.03f);
    }

    // =====================================================================================================
    // Per tick: camera and the local ground colors
    // =====================================================================================================

    @Override
    public void tick(StrikeEntity strike, ClientLevel level, int age, RandomSource random) {
        Vec3 rift = VoidRiftStrike.riftCenter(strike.position());
        if (!grassColors.containsKey(strike.getSeed())) {
            if (grassColors.size() > 16) grassColors.clear();
            BlockPos ground = BlockPos.containing(strike.position()).below();
            BlockState state = level.getBlockState(ground);
            int color = state.getMapColor(level, ground).col;
            grassColors.put(strike.getSeed(), color == 0 ? 0x5F8A3A : color);
        }
        float open = VoidRiftStrike.openness(age);
        float unstable = VoidRiftStrike.instability(age);
        float build = Math.min(1.0f, age / (float) TEAR);

        if (age < SNAP) {
            float squeeze = age >= CLOSING ? 0.1f * (1.0f - open) : 0.0f;
            CameraFx.tint(0x020A10, (0.22f * build + 0.25f * open + 0.2f * unstable) * CameraFx.proximity(rift, 110));
            CameraFx.fovSqueeze((0.03f * build + 0.05f * open + 0.08f * unstable + squeeze) * CameraFx.proximity(rift, 100));
            CameraFx.rumbleAt(rift, 0.03f + 0.07f * open * VoidRiftStrike.pull(age) + 0.22f * unstable, 120);
        }
        if (age == TEAR) {
            CameraFx.flashAt(rift, PALE, 0.45f, 90, 10);
            CameraFx.shakeAt(rift, 0.45f, 90);
        }
        if (age == TEAR + 25) {
            CameraFx.shakeAt(rift, 0.5f, 120);
            CameraFx.fovKickAt(rift, -0.08f, 100);
        }
        if (age == SNAP) {
            CameraFx.flashAt(rift, 0xF4FBFF, 1.0f, 200, 26);
            CameraFx.shakeAt(rift, 1.1f, 170);
            CameraFx.fovKickAt(rift, 0.22f, 150);
        }
    }

    // ---------------------------------------------------------------- helpers

    private static int scale(int rgb, float factor) {
        int r = Mth.clamp((int) (((rgb >> 16) & 255) * factor), 0, 255);
        int g = Mth.clamp((int) (((rgb >> 8) & 255) * factor), 0, 255);
        int b = Mth.clamp((int) ((rgb & 255) * factor), 0, 255);
        return (r << 16) | (g << 8) | b;
    }

    private static float[] scaleArray(float[] values, float factor) {
        float[] out = new float[values.length];
        for (int i = 0; i < values.length; i++) out[i] = values[i] * factor;
        return out;
    }
}
