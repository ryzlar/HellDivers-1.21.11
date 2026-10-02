package net.ryzlar.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Real 3D geometry for physical objects (meteors, flying rocks, lava bombs): lumpy rocks built from a
 * subdivided icosahedron with seeded noise, flat-shaded with a fake light and optional glowing heat.
 * Drawn on the solid layer, so they occlude and are occluded like any block.
 */
public final class FxMesh {

    private static final Map<Long, List<Vector3f[]>> ROCKS = new HashMap<>();

    private FxMesh() {
    }

    /**
     * Unit rock (radius ~1) as triangles. Cached per seed bucket and detail level.
     *
     * @param detail 0 = 20 faces (chunky debris), 1 = 80, 2 = 320 (meteor)
     */
    public static List<Vector3f[]> rock(int seed, int detail) {
        long key = ((long) (seed & 15) << 8) | detail;
        return ROCKS.computeIfAbsent(key, k -> buildRock(seed & 15, detail));
    }

    private static List<Vector3f[]> buildRock(int seed, int detail) {
        float t = (1.0f + Mth.sqrt(5.0f)) / 2.0f;
        Vector3f[] v = {
                new Vector3f(-1, t, 0), new Vector3f(1, t, 0), new Vector3f(-1, -t, 0), new Vector3f(1, -t, 0),
                new Vector3f(0, -1, t), new Vector3f(0, 1, t), new Vector3f(0, -1, -t), new Vector3f(0, 1, -t),
                new Vector3f(t, 0, -1), new Vector3f(t, 0, 1), new Vector3f(-t, 0, -1), new Vector3f(-t, 0, 1)};
        int[][] f = {{0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11}, {1, 5, 9}, {5, 11, 4}, {11, 10, 2},
                {10, 7, 6}, {7, 1, 8}, {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9}, {4, 9, 5},
                {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}};
        List<Vector3f[]> tris = new ArrayList<>();
        for (int[] face : f) {
            tris.add(new Vector3f[]{new Vector3f(v[face[0]]).normalize(), new Vector3f(v[face[1]]).normalize(), new Vector3f(v[face[2]]).normalize()});
        }
        for (int d = 0; d < detail; d++) {
            List<Vector3f[]> next = new ArrayList<>();
            for (Vector3f[] tri : tris) {
                Vector3f a = mid(tri[0], tri[1]), b = mid(tri[1], tri[2]), c = mid(tri[2], tri[0]);
                next.add(new Vector3f[]{tri[0], a, c});
                next.add(new Vector3f[]{tri[1], b, a});
                next.add(new Vector3f[]{tri[2], c, b});
                next.add(new Vector3f[]{a, b, c});
            }
            tris = next;
        }
        // Lumps: noise depends only on direction, so shared vertices stay shared (no cracks in the mesh)
        List<Vector3f[]> out = new ArrayList<>();
        for (Vector3f[] tri : tris) {
            Vector3f[] lumpy = new Vector3f[3];
            for (int i = 0; i < 3; i++) lumpy[i] = new Vector3f(tri[i]).mul(lump(tri[i], seed));
            out.add(lumpy);
        }
        return out;
    }

    private static Vector3f mid(Vector3f a, Vector3f b) {
        return new Vector3f(a).add(b).normalize();
    }

    private static float lump(Vector3f d, int seed) {
        float s = seed * 1.7f;
        return 1.0f + 0.16f * Mth.sin(d.x * 3.1f + s) * Mth.cos(d.y * 2.7f - s)
                + 0.1f * Mth.sin(d.z * 5.3f + d.x * 2.0f + s * 2)
                + 0.06f * Mth.sin(d.y * 9.1f + d.z * 7.3f);
    }

    /**
     * Draws a rock.
     *
     * @param base     rock color
     * @param glow     color of hot faces / lava veins
     * @param heat     0..1, how much it glows
     * @param heatDir  faces pointing this way glow most (e.g. the meteor front, the underside of lifted rock); may be null
     */
    public static void drawRock(FxContext ctx, Vector3f center, float radius, Quaternionf rotation, int seed, int detail,
                                int base, int glow, float heat, Vector3f heatDir) {
        drawRock(ctx, center, radius, rotation, seed, detail, base, glow, heat, heatDir, Float.NaN);
    }

    /**
     * Same, with a molten surface: when {@code time} is a number the glowing veins pulse and crawl over the rock
     * (meteors, lava bombs) instead of sitting still.
     */
    public static void drawRock(FxContext ctx, Vector3f center, float radius, Quaternionf rotation, int seed, int detail,
                                int base, int glow, float heat, Vector3f heatDir, float time) {
        boolean molten = !Float.isNaN(time);
        VertexConsumer vc = ctx.solid();
        Vector3f light = new Vector3f(0.35f, 0.85f, 0.25f).normalize();
        int faceIndex = 0;
        for (Vector3f[] tri : rock(seed, detail)) {
            Vector3f a = rotation.transform(new Vector3f(tri[0])).mul(radius).add(center);
            Vector3f b = rotation.transform(new Vector3f(tri[1])).mul(radius).add(center);
            Vector3f c = rotation.transform(new Vector3f(tri[2])).mul(radius).add(center);
            Vector3f normal = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a)).normalize();

            float shade = 0.3f + 0.7f * Math.max(0.0f, normal.dot(light));
            float facing = heatDir == null ? 1.0f : (float) Math.pow(Mth.clamp(normal.dot(heatDir), 0.0f, 1.0f), 1.6);
            float hot = heat * facing;
            // Some faces are glowing veins
            int face = faceIndex++;
            if (molten) {
                float vein = FxDraw.hash(seed * 31 + face, 77);
                float crawl = 0.5f + 0.5f * Mth.sin(time * 0.35f + face * 0.7f + vein * 6.0f);
                if (vein > 0.7f) hot = Math.max(hot, heat * (0.45f + 0.5f * crawl));
            } else if (FxDraw.hash(seed * 31 + face, 77) > 0.86f) {
                hot = Math.max(hot, heat * 0.75f);
            }
            int color = FxDraw.lerpColor(scale(base, shade), glow, hot);

            float r = FxDraw.r(color), g = FxDraw.g(color), bl = FxDraw.b(color);
            FxDraw.vertex(vc, ctx.pose, a, r, g, bl, 1.0f);
            FxDraw.vertex(vc, ctx.pose, b, r, g, bl, 1.0f);
            FxDraw.vertex(vc, ctx.pose, c, r, g, bl, 1.0f);
            FxDraw.vertex(vc, ctx.pose, c, r, g, bl, 1.0f);
        }
    }

    private static int scale(int rgb, float factor) {
        int r = Mth.clamp((int) (((rgb >> 16) & 255) * factor), 0, 255);
        int g = Mth.clamp((int) (((rgb >> 8) & 255) * factor), 0, 255);
        int b = Mth.clamp((int) ((rgb & 255) * factor), 0, 255);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * A boiling fireball: a sphere whose surface heaves with animated noise and whose color follows the turbulence
     * (white-hot in the hottest cells, orange, then deep red in the folds). Drawn additively with soft depth, so where
     * it touches or sinks into the ground it melts into it instead of showing a cut.
     *
     * @param heat  1 = white-hot, 0 = dull red
     * @param time  animation time in ticks
     */
    public static void fireball(FxContext ctx, Vector3f center, float radius, float heat, float alpha, float time, int seed) {
        if (radius <= 0.05f || alpha <= 0.005f) return;
        int lat = 14, lon = 28;
        float soft = FxDraw.setSoftness(Math.max(FxDraw.softness, radius * 0.35f));
        VertexConsumer vc = ctx.glow();
        Vector3f[][] p = new Vector3f[lat + 1][lon + 1];
        float[][] n = new float[lat + 1][lon + 1];
        for (int i = 0; i <= lat; i++) {
            float theta = -Mth.HALF_PI + Mth.PI * i / lat;
            for (int j = 0; j <= lon; j++) {
                float phi = Mth.TWO_PI * j / lon;
                Vector3f dir = new Vector3f(Mth.cos(theta) * Mth.cos(phi), Mth.sin(theta), Mth.cos(theta) * Mth.sin(phi));
                float noise = boil(dir, time, seed);
                n[i][j] = noise;
                p[i][j] = new Vector3f(dir).mul(radius * (1.0f + 0.13f * noise)).add(center);
            }
        }
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                fireVertex(vc, ctx, p[i][j], center, n[i][j], heat, alpha);
                fireVertex(vc, ctx, p[i + 1][j], center, n[i + 1][j], heat, alpha);
                fireVertex(vc, ctx, p[i + 1][j + 1], center, n[i + 1][j + 1], heat, alpha);
                fireVertex(vc, ctx, p[i][j + 1], center, n[i][j + 1], heat, alpha);
            }
        }
        FxDraw.setSoftness(soft);
    }

    private static float boil(Vector3f d, float time, int seed) {
        float s = (seed & 255) * 0.13f;
        return 0.5f * Mth.sin(d.x * 3.3f + time * 0.11f + s) * Mth.cos(d.y * 2.9f - time * 0.08f)
                + 0.35f * Mth.sin(d.z * 5.1f + d.x * 2.2f - time * 0.16f + s * 2)
                + 0.25f * Mth.sin(d.y * 7.7f + d.z * 4.1f + time * 0.21f);
    }

    private static void fireVertex(VertexConsumer vc, FxContext ctx, Vector3f p, Vector3f center, float noise, float heat, float alpha) {
        Vector3f normal = new Vector3f(p).sub(center).normalize();
        Vector3f view = new Vector3f(p).normalize();
        float facing = Math.abs(normal.dot(view));
        // hot cells: noise peaks; the limb of the sphere is darker and redder
        float t = Mth.clamp(0.5f + 0.5f * noise, 0.0f, 1.0f) * (0.4f + 0.6f * facing);
        int color = t * heat > 0.55f ? FxDraw.lerpColor(0xFFB040, 0xFFF4D0, (t * heat - 0.55f) / 0.45f)
                : FxDraw.lerpColor(0x8A1A06, 0xFF7A1A, Mth.clamp(t * (0.6f + 0.6f * heat), 0.0f, 1.0f));
        float a = alpha * (0.35f + 0.65f * facing) * (0.6f + 0.4f * t);
        FxDraw.vertex(vc, ctx.pose, p, FxDraw.r(color), FxDraw.g(color), FxDraw.b(color), a);
    }

    /** A rotation that tumbles over time, different per seed. */
    public static Quaternionf tumble(int seed, float time, float speed) {
        return new Quaternionf()
                .rotateY(FxDraw.hash(seed, 1) * Mth.TWO_PI + time * speed * (0.5f + FxDraw.hash(seed, 2)))
                .rotateX(FxDraw.hash(seed, 3) * Mth.TWO_PI + time * speed * (0.3f + FxDraw.hash(seed, 4)))
                .rotateZ(FxDraw.hash(seed, 5) * Mth.TWO_PI);
    }
}
