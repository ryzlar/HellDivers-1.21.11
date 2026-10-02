package net.ryzlar.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Reusable world-space effect primitives: soft sprites, rings, ribbons, beams, walls, domes, lightning bolts,
 * ground cracks and polygons. All positions are camera-relative (see {@link FxContext#rel}).
 * {@code additive} picks the glow layer (light) or the matter layer (smoke, void).
 */
public final class FxDraw {

    public static final float DEFAULT_SOFTNESS = 0.9f;
    /**
     * Soft-depth distance in blocks written with every vertex: how far in front of the terrain/blocks behind it an
     * effect starts fading out (0 = hard edge). Keeps flat effect geometry from being visibly sliced by blocks.
     * Set it around a group of draws with {@link #setSoftness} and restore the previous value afterwards.
     */
    public static float softness = DEFAULT_SOFTNESS;

    private FxDraw() {
    }

    /** Sets the soft-depth distance for the following draws and returns the previous value (to restore). */
    public static float setSoftness(float value) {
        float previous = softness;
        softness = Math.max(0.0f, value);
        return previous;
    }

    // ---------------------------------------------------------------- camera-facing

    /** Soft round sprite facing the camera: {@code aCenter} in the middle fading to {@code aEdge}. */
    public static void sprite(FxContext ctx, boolean additive, Vector3f c, float radius, int rgb, float aCenter, float aEdge) {
        if (radius <= 0.001f || (aCenter <= 0.002f && aEdge <= 0.002f)) return;
        VertexConsumer vc = ctx.layer(additive);
        int segments = radius > 20 ? 28 : 16;
        float r = r(rgb), g = g(rgb), b = b(rgb);
        for (int i = 0; i < segments; i++) {
            float a1 = Mth.TWO_PI * i / segments, a2 = Mth.TWO_PI * (i + 1) / segments;
            vertex(vc, ctx.pose, c.x, c.y, c.z, r, g, b, aCenter);
            vertex(vc, ctx.pose, planeX(ctx, c, a1, radius), planeY(ctx, c, a1, radius), planeZ(ctx, c, a1, radius), r, g, b, aEdge);
            vertex(vc, ctx.pose, planeX(ctx, c, a2, radius), planeY(ctx, c, a2, radius), planeZ(ctx, c, a2, radius), r, g, b, aEdge);
            vertex(vc, ctx.pose, c.x, c.y, c.z, r, g, b, aCenter);
        }
    }

    /** Camera-facing ring, brightest at {@code radius}, soft on both sides. */
    public static void spriteRing(FxContext ctx, boolean additive, Vector3f c, float radius, float halfWidth, int rgb, float a) {
        ring(ctx, additive, c, new Vector3f(ctx.left), new Vector3f(ctx.up), radius, radius, halfWidth, rgb, a, 40);
    }

    /**
     * Ring/ellipse in the plane spanned by {@code u} and {@code v} (need not face the camera),
     * radii {@code ru}/{@code rv}, soft edges.
     */
    public static void ring(FxContext ctx, boolean additive, Vector3f c, Vector3f u, Vector3f v, float ru, float rv,
                            float halfWidth, int rgb, float a, int segments) {
        if (a <= 0.002f || ru <= 0) return;
        VertexConsumer vc = ctx.layer(additive);
        float r = r(rgb), g = g(rgb), b = b(rgb);
        for (int i = 0; i < segments; i++) {
            float a1 = Mth.TWO_PI * i / segments, a2 = Mth.TWO_PI * (i + 1) / segments;
            float c1 = Mth.cos(a1), s1 = Mth.sin(a1), c2 = Mth.cos(a2), s2 = Mth.sin(a2);
            for (int side = -1; side <= 1; side += 2) {
                float off = side * halfWidth;
                vertex(vc, ctx.pose, ellipse(c, u, v, ru, rv, c1, s1, 0), r, g, b, a);
                vertex(vc, ctx.pose, ellipse(c, u, v, ru + off, rv + off, c1, s1, 0), r, g, b, 0);
                vertex(vc, ctx.pose, ellipse(c, u, v, ru + off, rv + off, c2, s2, 0), r, g, b, 0);
                vertex(vc, ctx.pose, ellipse(c, u, v, ru, rv, c2, s2, 0), r, g, b, a);
            }
        }
    }

    /**
     * Soft ribbon through {@code points} that always faces the camera (beams, trails, bolts).
     * Per-point half widths and alphas; the center line carries the alpha, the edges fade to 0.
     */
    public static void ribbon(FxContext ctx, boolean additive, List<Vector3f> points, float[] halfWidths, float[] alphas, int rgb) {
        int n = points.size();
        if (n < 2) return;
        VertexConsumer vc = ctx.layer(additive);
        float r = r(rgb), g = g(rgb), b = b(rgb);
        Vector3f[] side = new Vector3f[n];
        for (int i = 0; i < n; i++) {
            Vector3f tangent = new Vector3f(points.get(Math.min(i + 1, n - 1))).sub(points.get(Math.max(i - 1, 0)));
            Vector3f s = tangent.cross(points.get(i), new Vector3f());
            side[i] = s.lengthSquared() > 1.0e-10f ? s.normalize() : (i > 0 ? side[i - 1] : new Vector3f(ctx.up));
        }
        for (int i = 0; i < n - 1; i++) {
            float a1 = alphas[i], a2 = alphas[i + 1];
            if (a1 <= 0.002f && a2 <= 0.002f) continue;
            Vector3f p1 = points.get(i), p2 = points.get(i + 1);
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                Vector3f e1 = new Vector3f(side[i]).mul(halfWidths[i] * sgn).add(p1);
                Vector3f e2 = new Vector3f(side[i + 1]).mul(halfWidths[i + 1] * sgn).add(p2);
                vertex(vc, ctx.pose, p1, r, g, b, a1);
                vertex(vc, ctx.pose, e1, r, g, b, 0);
                vertex(vc, ctx.pose, e2, r, g, b, 0);
                vertex(vc, ctx.pose, p2, r, g, b, a2);
            }
        }
    }

    /** Ribbon with one width, alpha fading linearly from {@code aStart} to {@code aEnd}. */
    public static void ribbon(FxContext ctx, boolean additive, List<Vector3f> points, float halfWidth, float aStart, float aEnd, int rgb) {
        int n = points.size();
        float[] w = new float[n], a = new float[n];
        for (int i = 0; i < n; i++) {
            w[i] = halfWidth;
            a[i] = Mth.lerp(n == 1 ? 0 : i / (float) (n - 1), aStart, aEnd);
        }
        ribbon(ctx, additive, points, w, a, rgb);
    }

    /** Straight soft beam from {@code from} to {@code to}. */
    public static void beam(FxContext ctx, boolean additive, Vector3f from, Vector3f to, float halfWidth, int rgb, float aFrom, float aTo) {
        ribbon(ctx, additive, List.of(from, to), new float[]{halfWidth, halfWidth}, new float[]{aFrom, aTo}, rgb);
    }

    /**
     * Crisp ribbon: full alpha right up to its edge (no soft falloff). For sharp cores: crack lines, bolt cores,
     * rims. Faces the camera along its length.
     */
    public static void hardRibbon(FxContext ctx, boolean additive, List<Vector3f> points, float halfWidth, int rgb, float a) {
        int n = points.size();
        if (n < 2 || a <= 0.002f) return;
        VertexConsumer vc = ctx.layer(additive);
        float r = r(rgb), g = g(rgb), b = b(rgb);
        Vector3f prevSide = null;
        for (int i = 0; i < n - 1; i++) {
            Vector3f p1 = points.get(i), p2 = points.get(i + 1);
            Vector3f side = new Vector3f(p2).sub(p1).cross(new Vector3f(p1).add(p2).mul(0.5f));
            side = side.lengthSquared() > 1.0e-10f ? side.normalize().mul(halfWidth) : (prevSide != null ? prevSide : new Vector3f(ctx.up).mul(halfWidth));
            Vector3f s1 = prevSide != null ? prevSide : side;
            vertex(vc, ctx.pose, new Vector3f(p1).sub(s1), r, g, b, a);
            vertex(vc, ctx.pose, new Vector3f(p1).add(s1), r, g, b, a);
            vertex(vc, ctx.pose, new Vector3f(p2).add(side), r, g, b, a);
            vertex(vc, ctx.pose, new Vector3f(p2).sub(side), r, g, b, a);
            prevSide = side;
        }
    }

    /** One triangle (as a degenerate quad), per-vertex alpha. */
    public static void triangle(FxContext ctx, boolean additive, Vector3f a, Vector3f b, Vector3f c, int rgb, float aA, float aB, float aC) {
        VertexConsumer vc = ctx.layer(additive);
        float r = r(rgb), g = g(rgb), bl = b(rgb);
        vertex(vc, ctx.pose, a, r, g, bl, aA);
        vertex(vc, ctx.pose, b, r, g, bl, aB);
        vertex(vc, ctx.pose, c, r, g, bl, aC);
        vertex(vc, ctx.pose, c, r, g, bl, aC);
    }

    /** One quad with per-vertex colors and alphas. */
    public static void quad(FxContext ctx, boolean additive, Vector3f a, Vector3f b, Vector3f c, Vector3f d,
                            int rgbAB, float aAB, int rgbCD, float aCD) {
        VertexConsumer vc = ctx.layer(additive);
        vertex(vc, ctx.pose, a, r(rgbAB), g(rgbAB), b(rgbAB), aAB);
        vertex(vc, ctx.pose, b, r(rgbAB), g(rgbAB), b(rgbAB), aAB);
        vertex(vc, ctx.pose, c, r(rgbCD), g(rgbCD), b(rgbCD), aCD);
        vertex(vc, ctx.pose, d, r(rgbCD), g(rgbCD), b(rgbCD), aCD);
    }

    /**
     * Vertical cylinder made of stacked rings whose brightness flows upward over time (lava columns, energy beams).
     * Real geometry: looks right from every angle.
     */
    public static void flowCylinder(FxContext ctx, boolean additive, Vector3f base, float radius, float height,
                                    int rgb, float alpha, float time, float bands) {
        if (radius <= 0.01f || height <= 0.01f || alpha <= 0.002f) return;
        VertexConsumer vc = ctx.layer(additive);
        float r = r(rgb), g = g(rgb), b = b(rgb);
        int segments = 24, rows = Math.max(4, (int) (height / 2.0f));
        for (int row = 0; row < rows; row++) {
            float y1 = height * row / rows, y2 = height * (row + 1) / rows;
            float a1 = alpha * flow(y1, height, time, bands), a2 = alpha * flow(y2, height, time, bands);
            for (int i = 0; i < segments; i++) {
                float t1 = Mth.TWO_PI * i / segments, t2 = Mth.TWO_PI * (i + 1) / segments;
                float x1 = Mth.cos(t1) * radius, z1 = Mth.sin(t1) * radius, x2 = Mth.cos(t2) * radius, z2 = Mth.sin(t2) * radius;
                vertex(vc, ctx.pose, base.x + x1, base.y + y1, base.z + z1, r, g, b, a1);
                vertex(vc, ctx.pose, base.x + x1, base.y + y2, base.z + z1, r, g, b, a2);
                vertex(vc, ctx.pose, base.x + x2, base.y + y2, base.z + z2, r, g, b, a2);
                vertex(vc, ctx.pose, base.x + x2, base.y + y1, base.z + z2, r, g, b, a1);
            }
        }
    }

    private static float flow(float y, float height, float time, float bands) {
        float fade = 1.0f - y / height;
        return fade * (0.55f + 0.45f * Mth.sin(y / height * bands * Mth.TWO_PI - time));
    }

    // ---------------------------------------------------------------- projection (fake depth behind a surface)

    /**
     * Projects a point along the view ray onto a plane (camera at the origin). Geometry "behind" a surface,
     * projected like this, keeps exactly the same screen position (so it shows real parallax as you move)
     * while it is depth-tested as if it lay on the surface: it can only be seen through the opening and is
     * hidden correctly by anything in front of it. Returns null when the plane is edge-on or behind the camera.
     */
    public static Vector3f project(Vector3f p, Vector3f planePoint, Vector3f planeNormal, float towardCamera) {
        float denom = p.dot(planeNormal);
        if (Math.abs(denom) < 1.0e-5f) return null;
        float t = planePoint.dot(planeNormal) / denom;
        if (t <= 0) return null;
        return new Vector3f(p).mul(t * (1.0f - towardCamera));
    }

    /** A quad behind a surface, projected onto it (see {@link #project}). */
    public static void projectedQuad(FxContext ctx, boolean additive, Vector3f planePoint, Vector3f planeNormal,
                                     Vector3f a, Vector3f b, Vector3f c, Vector3f d,
                                     int rgbAB, float aAB, int rgbCD, float aCD) {
        Vector3f pa = project(a, planePoint, planeNormal, 0.0015f), pb = project(b, planePoint, planeNormal, 0.0015f);
        Vector3f pc = project(c, planePoint, planeNormal, 0.0015f), pd = project(d, planePoint, planeNormal, 0.0015f);
        if (pa == null || pb == null || pc == null || pd == null) return;
        quad(ctx, additive, pa, pb, pc, pd, rgbAB, aAB, rgbCD, aCD);
    }

    /** A soft sprite placed behind a surface, projected onto it; shrinks with depth like a real object would. */
    public static void projectedSprite(FxContext ctx, boolean additive, Vector3f planePoint, Vector3f planeNormal,
                                       Vector3f c, float radius, int rgb, float aCenter, float aEdge) {
        float denom = c.dot(planeNormal);
        if (Math.abs(denom) < 1.0e-5f) return;
        float t = planePoint.dot(planeNormal) / denom;
        if (t <= 0) return;
        sprite(ctx, additive, new Vector3f(c).mul(t * 0.998f), radius * t, rgb, aCenter, aEdge);
    }

    // ---------------------------------------------------------------- ground-aligned

    /** Flat horizontal disc, radial gradient. */
    public static void disc(FxContext ctx, boolean additive, Vector3f c, float radius, int rgb, float aCenter, float aEdge) {
        if (radius <= 0.001f) return;
        float soft = setSoftness(Math.min(softness, 0.15f));
        VertexConsumer vc = ctx.layer(additive);
        float r = r(rgb), g = g(rgb), b = b(rgb);
        int segments = 32;
        for (int i = 0; i < segments; i++) {
            float a1 = Mth.TWO_PI * i / segments, a2 = Mth.TWO_PI * (i + 1) / segments;
            vertex(vc, ctx.pose, c.x, c.y, c.z, r, g, b, aCenter);
            vertex(vc, ctx.pose, c.x + Mth.cos(a1) * radius, c.y, c.z + Mth.sin(a1) * radius, r, g, b, aEdge);
            vertex(vc, ctx.pose, c.x + Mth.cos(a2) * radius, c.y, c.z + Mth.sin(a2) * radius, r, g, b, aEdge);
            vertex(vc, ctx.pose, c.x, c.y, c.z, r, g, b, aCenter);
        }
        setSoftness(soft);
    }

    /** Flat horizontal ring (shockwave front on the ground). */
    public static void groundRing(FxContext ctx, boolean additive, Vector3f c, float radius, float halfWidth, int rgb, float a) {
        float soft = setSoftness(Math.min(softness, 0.15f));
        ring(ctx, additive, c, new Vector3f(1, 0, 0), new Vector3f(0, 0, 1), radius, radius, halfWidth, rgb, a, 64);
        setSoftness(soft);
    }

    /** Flat horizontal arc from angle {@code from} to {@code to}. */
    public static void groundArc(FxContext ctx, boolean additive, Vector3f c, float radius, float halfWidth,
                                 float from, float to, int rgb, float a) {
        float soft = setSoftness(Math.min(softness, 0.15f));
        VertexConsumer vc = ctx.layer(additive);
        float r = r(rgb), g = g(rgb), b = b(rgb);
        int segments = Math.max(2, (int) (Math.abs(to - from) * 10));
        for (int i = 0; i < segments; i++) {
            float a1 = Mth.lerp(i / (float) segments, from, to), a2 = Mth.lerp((i + 1) / (float) segments, from, to);
            float c1 = Mth.cos(a1), s1 = Mth.sin(a1), c2 = Mth.cos(a2), s2 = Mth.sin(a2);
            vertex(vc, ctx.pose, c.x + c1 * (radius - halfWidth), c.y, c.z + s1 * (radius - halfWidth), r, g, b, a);
            vertex(vc, ctx.pose, c.x + c1 * (radius + halfWidth), c.y, c.z + s1 * (radius + halfWidth), r, g, b, a);
            vertex(vc, ctx.pose, c.x + c2 * (radius + halfWidth), c.y, c.z + s2 * (radius + halfWidth), r, g, b, a);
            vertex(vc, ctx.pose, c.x + c2 * (radius - halfWidth), c.y, c.z + s2 * (radius - halfWidth), r, g, b, a);
        }
        setSoftness(soft);
    }

    /** Pie slice on the ground fading from {@code aLead} at angle {@code to} to 0 at {@code from} (radar sweep). */
    public static void groundSweep(FxContext ctx, boolean additive, Vector3f c, float radius, float from, float to, int rgb, float aLead) {
        float soft = setSoftness(Math.min(softness, 0.15f));
        VertexConsumer vc = ctx.layer(additive);
        float r = r(rgb), g = g(rgb), b = b(rgb);
        int segments = 16;
        for (int i = 0; i < segments; i++) {
            float t1 = i / (float) segments, t2 = (i + 1) / (float) segments;
            float a1 = Mth.lerp(t1, from, to), a2 = Mth.lerp(t2, from, to);
            vertex(vc, ctx.pose, c.x, c.y, c.z, r, g, b, 0);
            vertex(vc, ctx.pose, c.x + Mth.cos(a1) * radius, c.y, c.z + Mth.sin(a1) * radius, r, g, b, aLead * t1 * t1);
            vertex(vc, ctx.pose, c.x + Mth.cos(a2) * radius, c.y, c.z + Mth.sin(a2) * radius, r, g, b, aLead * t2 * t2);
            vertex(vc, ctx.pose, c.x, c.y, c.z, r, g, b, 0);
        }
        setSoftness(soft);
    }

    /** Vertical cylinder band (dust wall behind a shockwave, energy curtain). */
    public static void wall(FxContext ctx, boolean additive, Vector3f c, float radius, float height, int rgb, float aBottom, float aTop) {
        if (radius <= 0.01f || (aBottom <= 0.002f && aTop <= 0.002f)) return;
        VertexConsumer vc = ctx.layer(additive);
        float r = r(rgb), g = g(rgb), b = b(rgb);
        int segments = 64;
        for (int i = 0; i < segments; i++) {
            float a1 = Mth.TWO_PI * i / segments, a2 = Mth.TWO_PI * (i + 1) / segments;
            float x1 = c.x + Mth.cos(a1) * radius, z1 = c.z + Mth.sin(a1) * radius;
            float x2 = c.x + Mth.cos(a2) * radius, z2 = c.z + Mth.sin(a2) * radius;
            vertex(vc, ctx.pose, x1, c.y, z1, r, g, b, aBottom);
            vertex(vc, ctx.pose, x1, c.y + height, z1, r, g, b, aTop);
            vertex(vc, ctx.pose, x2, c.y + height, z2, r, g, b, aTop);
            vertex(vc, ctx.pose, x2, c.y, z2, r, g, b, aBottom);
        }
    }

    /**
     * Sphere/hemisphere shell whose edges glow and middle is clear (fresnel), like a pressure wave.
     * {@code full=false} draws only the upper half.
     */
    public static void shell(FxContext ctx, boolean additive, Vector3f c, float radius, int rgb, float a, boolean full) {
        if (radius <= 0.01f || a <= 0.002f) return;
        VertexConsumer vc = ctx.layer(additive);
        float r = r(rgb), g = g(rgb), b = b(rgb);
        int lat = full ? 16 : 8, lon = 36;
        float latStart = full ? -Mth.HALF_PI : 0.0f;
        for (int i = 0; i < lat; i++) {
            float t1 = latStart + (Mth.HALF_PI - latStart) * i / lat;
            float t2 = latStart + (Mth.HALF_PI - latStart) * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                float p1 = Mth.TWO_PI * j / lon, p2 = Mth.TWO_PI * (j + 1) / lon;
                shellVertex(vc, ctx.pose, c, radius, t1, p1, r, g, b, a);
                shellVertex(vc, ctx.pose, c, radius, t2, p1, r, g, b, a);
                shellVertex(vc, ctx.pose, c, radius, t2, p2, r, g, b, a);
                shellVertex(vc, ctx.pose, c, radius, t1, p2, r, g, b, a);
            }
        }
    }

    private static void shellVertex(VertexConsumer vc, Matrix4f pose, Vector3f c, float radius, float lat, float lon,
                                    float r, float g, float b, float a) {
        Vector3f n = new Vector3f(Mth.cos(lat) * Mth.cos(lon), Mth.sin(lat), Mth.cos(lat) * Mth.sin(lon));
        Vector3f p = new Vector3f(n).mul(radius).add(c);
        Vector3f view = new Vector3f(p).normalize();
        float rim = 1.0f - Math.abs(n.dot(view));
        vertex(vc, pose, p, r, g, b, a * rim * rim);
    }

    /**
     * Ribbon laid flat on the ground (cracks, scorch lines). Points are camera-relative; width per point.
     */
    public static void groundRibbon(FxContext ctx, boolean additive, List<Vector3f> points, float[] halfWidths, float[] alphas, int rgb) {
        int n = points.size();
        if (n < 2) return;
        float soft = setSoftness(Math.min(softness, 0.15f));
        VertexConsumer vc = ctx.layer(additive);
        float r = r(rgb), g = g(rgb), b = b(rgb);
        Vector3f[] side = new Vector3f[n];
        for (int i = 0; i < n; i++) {
            Vector3f t = new Vector3f(points.get(Math.min(i + 1, n - 1))).sub(points.get(Math.max(i - 1, 0)));
            Vector3f s = new Vector3f(-t.z, 0, t.x);
            side[i] = s.lengthSquared() > 1.0e-8f ? s.normalize() : new Vector3f(1, 0, 0);
        }
        for (int i = 0; i < n - 1; i++) {
            Vector3f p1 = points.get(i), p2 = points.get(i + 1);
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                vertex(vc, ctx.pose, p1, r, g, b, alphas[i]);
                vertex(vc, ctx.pose, new Vector3f(side[i]).mul(halfWidths[i] * sgn).add(p1), r, g, b, 0);
                vertex(vc, ctx.pose, new Vector3f(side[i + 1]).mul(halfWidths[i + 1] * sgn).add(p2), r, g, b, 0);
                vertex(vc, ctx.pose, p2, r, g, b, alphas[i + 1]);
            }
        }
        setSoftness(soft);
    }

    /** Filled polygon from a center to a closed outline, with separate center/edge colors. */
    public static void fan(FxContext ctx, boolean additive, Vector3f c, List<Vector3f> outline,
                           int rgbCenter, float aCenter, int rgbEdge, float aEdge) {
        VertexConsumer vc = ctx.layer(additive);
        int n = outline.size();
        for (int i = 0; i < n; i++) {
            Vector3f p1 = outline.get(i), p2 = outline.get((i + 1) % n);
            vertex(vc, ctx.pose, c, r(rgbCenter), g(rgbCenter), b(rgbCenter), aCenter);
            vertex(vc, ctx.pose, p1, r(rgbEdge), g(rgbEdge), b(rgbEdge), aEdge);
            vertex(vc, ctx.pose, p2, r(rgbEdge), g(rgbEdge), b(rgbEdge), aEdge);
            vertex(vc, ctx.pose, c, r(rgbCenter), g(rgbCenter), b(rgbCenter), aCenter);
        }
    }

    // ---------------------------------------------------------------- draped on the terrain

    /** Terrain surface height at world x/z, camera-relative (top of the highest motion-blocking block). */
    public static float groundRel(FxContext ctx, double x, double z) {
        return (float) (FxPresets.groundY(ctx.level, x, z) - ctx.cam.y);
    }

    /**
     * Ring laid onto the real terrain: every vertex sits on the ground surface under it, so a shockwave front or a
     * designator climbs hills, dips into craters and never floats or cuts into slopes.
     */
    public static void drapedRing(FxContext ctx, boolean additive, Vec3 center, float radius, float halfWidth,
                                  int rgb, float a, float lift) {
        if (a <= 0.002f || radius <= 0.01f) return;
        int segments = Mth.clamp((int) (radius * 3.0f), 24, 180);
        float soft = setSoftness(0.0f);
        VertexConsumer vc = ctx.layer(additive);
        float r = r(rgb), g = g(rgb), b = b(rgb);
        float inner = Math.max(0.0f, radius - halfWidth), outer = radius + halfWidth;
        for (int i = 0; i < segments; i++) {
            float a1 = Mth.TWO_PI * i / segments, a2 = Mth.TWO_PI * (i + 1) / segments;
            float c1 = Mth.cos(a1), s1 = Mth.sin(a1), c2 = Mth.cos(a2), s2 = Mth.sin(a2);
            Vector3f mid1 = draped(ctx, center, c1 * radius, s1 * radius, lift);
            Vector3f mid2 = draped(ctx, center, c2 * radius, s2 * radius, lift);
            Vector3f in1 = draped(ctx, center, c1 * inner, s1 * inner, lift), in2 = draped(ctx, center, c2 * inner, s2 * inner, lift);
            Vector3f out1 = draped(ctx, center, c1 * outer, s1 * outer, lift), out2 = draped(ctx, center, c2 * outer, s2 * outer, lift);
            vertex(vc, ctx.pose, mid1, r, g, b, a);
            vertex(vc, ctx.pose, in1, r, g, b, 0);
            vertex(vc, ctx.pose, in2, r, g, b, 0);
            vertex(vc, ctx.pose, mid2, r, g, b, a);
            vertex(vc, ctx.pose, mid1, r, g, b, a);
            vertex(vc, ctx.pose, out1, r, g, b, 0);
            vertex(vc, ctx.pose, out2, r, g, b, 0);
            vertex(vc, ctx.pose, mid2, r, g, b, a);
        }
        setSoftness(soft);
    }

    /** Disc laid onto the terrain (glow pools, scorch, shadows), radial gradient from center to edge. */
    public static void drapedDisc(FxContext ctx, boolean additive, Vec3 center, float radius, int rgb,
                                  float aCenter, float aEdge, float lift) {
        if (radius <= 0.01f || (aCenter <= 0.002f && aEdge <= 0.002f)) return;
        int segments = Mth.clamp((int) (radius * 2.0f), 20, 96);
        int rows = Mth.clamp((int) (radius / 1.5f), 2, 14);
        float soft = setSoftness(0.0f);
        VertexConsumer vc = ctx.layer(additive);
        float r = r(rgb), g = g(rgb), b = b(rgb);
        for (int row = 0; row < rows; row++) {
            float t1 = row / (float) rows, t2 = (row + 1) / (float) rows;
            float al1 = Mth.lerp(t1, aCenter, aEdge), al2 = Mth.lerp(t2, aCenter, aEdge);
            for (int i = 0; i < segments; i++) {
                float a1 = Mth.TWO_PI * i / segments, a2 = Mth.TWO_PI * (i + 1) / segments;
                float c1 = Mth.cos(a1), s1 = Mth.sin(a1), c2 = Mth.cos(a2), s2 = Mth.sin(a2);
                vertex(vc, ctx.pose, draped(ctx, center, c1 * radius * t1, s1 * radius * t1, lift), r, g, b, al1);
                vertex(vc, ctx.pose, draped(ctx, center, c1 * radius * t2, s1 * radius * t2, lift), r, g, b, al2);
                vertex(vc, ctx.pose, draped(ctx, center, c2 * radius * t2, s2 * radius * t2, lift), r, g, b, al2);
                vertex(vc, ctx.pose, draped(ctx, center, c2 * radius * t1, s2 * radius * t1, lift), r, g, b, al1);
            }
        }
        setSoftness(soft);
    }

    /**
     * Ribbon following the terrain through world x/z points (cracks, scorch trails, ground arcs). Each vertex is
     * placed on the ground under it plus {@code lift}.
     */
    public static void drapedRibbon(FxContext ctx, boolean additive, List<Vec3> points, float[] halfWidths, float[] alphas,
                                    int rgb, float lift) {
        int n = points.size();
        if (n < 2) return;
        float soft = setSoftness(0.0f);
        VertexConsumer vc = ctx.layer(additive);
        float r = r(rgb), g = g(rgb), b = b(rgb);
        double[] sx = new double[n], sz = new double[n];
        for (int i = 0; i < n; i++) {
            Vec3 t = points.get(Math.min(i + 1, n - 1)).subtract(points.get(Math.max(i - 1, 0)));
            double len = Math.sqrt(t.x * t.x + t.z * t.z);
            sx[i] = len < 1.0e-6 ? 1 : -t.z / len;
            sz[i] = len < 1.0e-6 ? 0 : t.x / len;
        }
        for (int i = 0; i < n - 1; i++) {
            if (alphas[i] <= 0.002f && alphas[i + 1] <= 0.002f) continue;
            Vec3 p1 = points.get(i), p2 = points.get(i + 1);
            Vector3f c1 = draped(ctx, p1, 0, 0, lift), c2 = draped(ctx, p2, 0, 0, lift);
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                Vector3f e1 = draped(ctx, p1, (float) (sx[i] * halfWidths[i] * sgn), (float) (sz[i] * halfWidths[i] * sgn), lift);
                Vector3f e2 = draped(ctx, p2, (float) (sx[i + 1] * halfWidths[i + 1] * sgn), (float) (sz[i + 1] * halfWidths[i + 1] * sgn), lift);
                vertex(vc, ctx.pose, c1, r, g, b, alphas[i]);
                vertex(vc, ctx.pose, e1, r, g, b, 0);
                vertex(vc, ctx.pose, e2, r, g, b, 0);
                vertex(vc, ctx.pose, c2, r, g, b, alphas[i + 1]);
            }
        }
        setSoftness(soft);
    }

    private static Vector3f draped(FxContext ctx, Vec3 center, float dx, float dz, float lift) {
        double x = center.x + dx, z = center.z + dz;
        return new Vector3f((float) (x - ctx.cam.x), groundRel(ctx, x, z) + lift, (float) (z - ctx.cam.z));
    }

    // ---------------------------------------------------------------- distortion (heat haze, lensing)

    /**
     * World-anchored distortion sprite: bends the scene behind it. Must be drawn from a visual's
     * {@code renderDistortion} pass (before the effects themselves, so it only bends the world).
     *
     * @param lens     -1..1: positive magnifies toward the center, negative pushes the image outward (gravitational lens)
     * @param shimmer  0..1 heat shimmer
     * @param swirl    -1..1 twists the image around the center
     * @param strength 0..1 overall
     */
    public static void distortion(FxContext ctx, Vector3f c, float radius, float lens, float shimmer, float swirl, float strength) {
        if (strength <= 0.002f || radius <= 0.01f) return;
        VertexConsumer vc = ctx.distort();
        Vector3f right = ctx.right(), up = ctx.up;
        float r = Mth.clamp(0.5f + lens * 0.5f, 0.0f, 1.0f), g = Mth.clamp(shimmer, 0.0f, 1.0f);
        float b = Mth.clamp(0.5f + swirl * 0.5f, 0.0f, 1.0f), a = Mth.clamp(strength, 0.0f, 1.0f);
        float[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (float[] k : corners) {
            float x = c.x + (right.x * k[0] + up.x * k[1]) * radius;
            float y = c.y + (right.y * k[0] + up.y * k[1]) * radius;
            float z = c.z + (right.z * k[0] + up.z * k[1]) * radius;
            vc.addVertex(ctx.pose, x, y, z).setUv(k[0], k[1]).setColor(r, g, b, a);
        }
    }

    /** A column of rising heat shimmer (over lava, craters, a fireball), {@code height} blocks tall. */
    public static void heatHaze(FxContext ctx, Vector3f base, float radius, float height, float strength) {
        if (strength <= 0.002f || radius <= 0.01f) return;
        int steps = Math.max(1, Math.min(8, (int) (height / (radius * 1.2f))));
        for (int i = 0; i < steps; i++) {
            float t = (i + 0.5f) / steps;
            distortion(ctx, new Vector3f(base).add(0, height * t, 0), radius * (1.0f + 0.3f * t), 0.0f, 1.0f, 0.0f,
                    strength * (1.0f - t * 0.7f));
        }
    }

    // ---------------------------------------------------------------- lightning

    /** Jagged bolt between two points (midpoint displacement); {@code detail} = subdivision rounds. */
    public static List<Vector3f> bolt(Random random, Vector3f from, Vector3f to, float displacement, int detail) {
        List<Vector3f> points = new ArrayList<>();
        points.add(new Vector3f(from));
        points.add(new Vector3f(to));
        float disp = displacement;
        for (int round = 0; round < detail; round++) {
            List<Vector3f> next = new ArrayList<>();
            for (int i = 0; i < points.size() - 1; i++) {
                Vector3f a = points.get(i), b = points.get(i + 1);
                next.add(a);
                Vector3f mid = new Vector3f(a).add(b).mul(0.5f);
                mid.add((random.nextFloat() - 0.5f) * disp, (random.nextFloat() - 0.5f) * disp * 0.5f, (random.nextFloat() - 0.5f) * disp);
                next.add(mid);
            }
            next.add(points.get(points.size() - 1));
            points = next;
            disp *= 0.55f;
        }
        return points;
    }

    /** A bolt plus a few forks splitting off it. */
    public static List<List<Vector3f>> forkedBolt(Random random, Vector3f from, Vector3f to, float displacement, int forks) {
        List<List<Vector3f>> bolts = new ArrayList<>();
        List<Vector3f> main = bolt(random, from, to, displacement, 6);
        bolts.add(main);
        for (int i = 0; i < forks; i++) {
            Vector3f start = main.get(2 + random.nextInt(Math.max(1, main.size() - 6)));
            Vector3f dir = new Vector3f(to).sub(from).mul(0.15f + random.nextFloat() * 0.25f);
            dir.add((random.nextFloat() - 0.5f) * displacement * 1.5f, 0, (random.nextFloat() - 0.5f) * displacement * 1.5f);
            bolts.add(bolt(random, start, new Vector3f(start).add(dir), displacement * 0.4f, 4));
        }
        return bolts;
    }

    /** Draws a bolt: wide colored glow, white-hot core. */
    public static void drawBolt(FxContext ctx, List<Vector3f> points, float coreWidth, int glowRgb, float alpha) {
        ribbon(ctx, true, points, Math.min(coreWidth * 3.5f, 1.2f), alpha * 0.22f, alpha * 0.15f, glowRgb);
        ribbon(ctx, true, points, coreWidth * 1.5f, alpha * 0.7f, alpha * 0.55f, glowRgb);
        hardRibbon(ctx, true, points, coreWidth * 0.5f, 0xFFFFFF, alpha);
    }

    // ---------------------------------------------------------------- smoke

    /** Collects smoke puffs and draws them back to front so alpha blending stays correct. */
    public static final class Puffs {
        private record Puff(Vector3f c, float radius, int rgb, float a) {
        }

        private final List<Puff> puffs = new ArrayList<>();

        public void add(Vector3f c, float radius, int rgb, float a) {
            if (a > 0.004f && radius > 0.01f) puffs.add(new Puff(c, radius, rgb, a));
        }

        public void draw(FxContext ctx) {
            puffs.sort(Comparator.comparingDouble((Puff p) -> -p.c().lengthSquared()));
            float soft = softness;
            for (Puff p : puffs) {
                // big smoke melts into the ground and walls it touches
                setSoftness(Math.max(soft, p.radius() * 0.45f));
                sprite(ctx, false, p.c(), p.radius(), p.rgb(), p.a(), 0.0f);
                // lighter top: gives the puff some volume
                Vector3f top = new Vector3f(p.c()).add(0, p.radius() * 0.25f, 0);
                sprite(ctx, false, top, p.radius() * 0.6f, lighten(p.rgb(), 0.25f), p.a() * 0.45f, 0.0f);
            }
            setSoftness(soft);
            puffs.clear();
        }
    }

    // ---------------------------------------------------------------- helpers

    public static void vertex(VertexConsumer vc, Matrix4f pose, float x, float y, float z, float r, float g, float b, float a) {
        // UV0.x = soft-depth distance (ignored by layers without UV, e.g. the solid one)
        vc.addVertex(pose, x, y, z).setUv(softness, 0.0f).setColor(r, g, b, Mth.clamp(a, 0.0f, 1.0f));
    }

    public static void vertex(VertexConsumer vc, Matrix4f pose, Vector3f p, float r, float g, float b, float a) {
        vertex(vc, pose, p.x, p.y, p.z, r, g, b, a);
    }

    private static Vector3f ellipse(Vector3f c, Vector3f u, Vector3f v, float ru, float rv, float cos, float sin, float unused) {
        return new Vector3f(c).add(u.x * cos * ru + v.x * sin * rv, u.y * cos * ru + v.y * sin * rv, u.z * cos * ru + v.z * sin * rv);
    }

    private static float planeX(FxContext ctx, Vector3f c, float angle, float radius) {
        return c.x + (ctx.left.x * Mth.cos(angle) + ctx.up.x * Mth.sin(angle)) * radius;
    }

    private static float planeY(FxContext ctx, Vector3f c, float angle, float radius) {
        return c.y + (ctx.left.y * Mth.cos(angle) + ctx.up.y * Mth.sin(angle)) * radius;
    }

    private static float planeZ(FxContext ctx, Vector3f c, float angle, float radius) {
        return c.z + (ctx.left.z * Mth.cos(angle) + ctx.up.z * Mth.sin(angle)) * radius;
    }

    public static float r(int rgb) {
        return ((rgb >> 16) & 255) / 255.0f;
    }

    public static float g(int rgb) {
        return ((rgb >> 8) & 255) / 255.0f;
    }

    public static float b(int rgb) {
        return (rgb & 255) / 255.0f;
    }

    public static int lerpColor(int from, int to, float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        int r = (int) Mth.lerp(t, (from >> 16) & 255, (to >> 16) & 255);
        int g = (int) Mth.lerp(t, (from >> 8) & 255, (to >> 8) & 255);
        int b = (int) Mth.lerp(t, from & 255, to & 255);
        return (r << 16) | (g << 8) | b;
    }

    public static int lighten(int rgb, float amount) {
        return lerpColor(rgb, 0xFFFFFF, amount);
    }

    public static float smooth(float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        return t * t * (3 - 2 * t);
    }

    public static float easeOut(float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        return 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
    }

    /** Cheap deterministic pseudo-random in [0,1) from integers. */
    public static float hash(int a, int b) {
        int h = a * 374761393 + b * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFFFF) / (float) 0x1000000;
    }
}
