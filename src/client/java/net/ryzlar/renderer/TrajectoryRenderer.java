package net.ryzlar.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.ryzlar.entities.StrategemBallPhysics;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class TrajectoryRenderer {

    // Arc
    private static final float CORE_WIDTH = 0.018f;
    private static final float GLOW_WIDTH = 0.06f;
    private static final float PIECE_LENGTH = 0.2f;      // arc is resampled so dashes/fades look smooth
    private static final float FADE_IN_DISTANCE = 1.5f;  // keep the arc out of your face
    private static final float DASH_LENGTH = 0.9f;
    private static final float DASH_SPEED = 4.0f;        // blocks per second, flows towards the target

    // Impact marker, same red as the beam (0xff3333)
    private static final float MARK_R = 1.0f, MARK_G = 0.2f, MARK_B = 0.2f;
    private static final float MARKER_RADIUS = 0.7f;
    private static final float SURFACE_OFFSET = 0.02f;   // avoid z-fighting with the block face
    private static final float PILLAR_HEIGHT = 2.5f;

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, float power, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        TrajectoryCalc.Result result = TrajectoryCalc.calculate(player, power, partialTick);
        if (result.points().size() < 2) return;

        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        float seconds = (player.tickCount + partialTick) / 20.0f;

        // Weak throw = amber, full charge = cyan
        float curve = StrategemBallPhysics.chargeCurve(power);
        float r = Mth.lerp(curve, 1.0f, 0.2f);
        float g = Mth.lerp(curve, 0.65f, 0.9f);
        float b = Mth.lerp(curve, 0.1f, 1.0f);

        VertexConsumer vc = bufferSource.getBuffer(RenderTypes.debugQuads());
        Matrix4f mat = poseStack.last().pose();

        renderArc(vc, mat, result.points(), cam, seconds, r, g, b);
        if (result.hit() != null) {
            renderImpactMarker(vc, mat, result.hit(), cam, seconds);
        }

        if (bufferSource instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch(RenderTypes.debugQuads());
        }
    }

    // ---------------------------------------------------------------- arc

    private static void renderArc(VertexConsumer vc, Matrix4f mat, List<Vec3> points, Vec3 cam,
                                  float seconds, float r, float g, float b) {
        // Resample into short pieces, camera-relative, tracking the distance along the arc
        List<Vector3f> pos = new ArrayList<>();
        List<Float> dist = new ArrayList<>();
        float travelled = 0;
        pos.add(relative(points.get(0), cam));
        dist.add(0f);
        for (int i = 1; i < points.size(); i++) {
            Vec3 from = points.get(i - 1);
            Vec3 to = points.get(i);
            float length = (float) from.distanceTo(to);
            int pieces = Math.max(1, Mth.ceil(length / PIECE_LENGTH));
            for (int p = 1; p <= pieces; p++) {
                float t = p / (float) pieces;
                pos.add(relative(from.lerp(to, t), cam));
                dist.add(travelled + length * t);
            }
            travelled += length;
        }

        int n = pos.size();
        float[] alpha = new float[n];
        for (int i = 0; i < n; i++) {
            float d = dist.get(i);
            float fadeIn = Mth.clamp(d / FADE_IN_DISTANCE, 0.0f, 1.0f);
            float phase = (d - seconds * DASH_SPEED) / DASH_LENGTH;
            float dash = 0.5f + 0.5f * Mth.sin(phase * Mth.TWO_PI);
            alpha[i] = fadeIn * (0.35f + 0.6f * dash);
        }

        // Camera-facing ribbon: side vector = tangent x (direction to camera)
        Vector3f[] side = new Vector3f[n];
        for (int i = 0; i < n; i++) {
            Vector3f tangent = new Vector3f(pos.get(Math.min(i + 1, n - 1))).sub(pos.get(Math.max(i - 1, 0)));
            Vector3f s = tangent.cross(pos.get(i), new Vector3f());
            side[i] = s.lengthSquared() > 1.0e-8f ? s.normalize() : (i > 0 ? side[i - 1] : new Vector3f(0, 1, 0));
        }

        ribbon(vc, mat, pos, side, alpha, GLOW_WIDTH, r, g, b, 0.25f);
        ribbon(vc, mat, pos, side, alpha, CORE_WIDTH, lighten(r), lighten(g), lighten(b), 1.0f);
    }

    private static void ribbon(VertexConsumer vc, Matrix4f mat, List<Vector3f> pos, Vector3f[] side, float[] alpha,
                               float width, float r, float g, float b, float alphaScale) {
        for (int i = 0; i < pos.size() - 1; i++) {
            if (alpha[i] <= 0.0f && alpha[i + 1] <= 0.0f) continue;
            Vector3f p1 = pos.get(i);
            Vector3f p2 = pos.get(i + 1);
            Vector3f s1 = new Vector3f(side[i]).mul(width);
            Vector3f s2 = new Vector3f(side[i + 1]).mul(width);
            float a1 = alpha[i] * alphaScale;
            float a2 = alpha[i + 1] * alphaScale;

            vc.addVertex(mat, p1.x - s1.x, p1.y - s1.y, p1.z - s1.z).setColor(r, g, b, a1);
            vc.addVertex(mat, p1.x + s1.x, p1.y + s1.y, p1.z + s1.z).setColor(r, g, b, a1);
            vc.addVertex(mat, p2.x + s2.x, p2.y + s2.y, p2.z + s2.z).setColor(r, g, b, a2);
            vc.addVertex(mat, p2.x - s2.x, p2.y - s2.y, p2.z - s2.z).setColor(r, g, b, a2);
        }
    }

    // ---------------------------------------------------------------- impact marker

    private static void renderImpactMarker(VertexConsumer vc, Matrix4f mat, BlockHitResult hit, Vec3 cam, float seconds) {
        // Build a plane on the face that was hit, so walls and ceilings get a correct marker too
        Direction face = hit.getDirection();
        Vector3f normal = face.step();
        Vector3f u = Math.abs(normal.y) > 0.5f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        Vector3f v = normal.cross(u, new Vector3f()).normalize();
        u = v.cross(normal, new Vector3f()).normalize();

        Vector3f c = relative(hit.getLocation(), cam).add(new Vector3f(normal).mul(SURFACE_OFFSET));
        float pulse = 0.5f + 0.5f * Mth.sin(seconds * Mth.TWO_PI);

        // Soft filled disc
        annulus(vc, mat, c, u, v, 0.0f, MARKER_RADIUS, 0, Mth.TWO_PI, 24, MARK_R, MARK_G, MARK_B, 0.12f + 0.08f * pulse);

        // Rotating segmented ring
        float spin = seconds * 1.5f;
        for (int i = 0; i < 4; i++) {
            float start = spin + i * Mth.HALF_PI;
            annulus(vc, mat, c, u, v, MARKER_RADIUS - 0.05f, MARKER_RADIUS, start, start + Mth.HALF_PI * 0.65f, 8,
                    MARK_R, MARK_G, MARK_B, 0.9f);
        }

        // Shockwave ring expanding outwards
        float wave = (seconds * 0.8f) % 1.0f;
        float waveRadius = Mth.lerp(wave, 0.15f, MARKER_RADIUS * 1.4f);
        annulus(vc, mat, c, u, v, waveRadius - 0.03f, waveRadius, 0, Mth.TWO_PI, 32, MARK_R, MARK_G, MARK_B, (1.0f - wave) * 0.7f);

        // Four targeting ticks pointing inwards
        for (int i = 0; i < 4; i++) {
            float angle = i * Mth.HALF_PI + Mth.PI / 4.0f;
            Vector3f dir = planeDir(u, v, angle);
            Vector3f across = planeDir(u, v, angle + Mth.HALF_PI).mul(0.025f);
            Vector3f inner = new Vector3f(dir).mul(MARKER_RADIUS + 0.08f).add(c);
            Vector3f outer = new Vector3f(dir).mul(MARKER_RADIUS + 0.3f + 0.05f * pulse).add(c);
            quad(vc, mat,
                    new Vector3f(inner).sub(across), new Vector3f(inner).add(across),
                    new Vector3f(outer).add(across), new Vector3f(outer).sub(across),
                    MARK_R, MARK_G, MARK_B, 0.9f, 0.3f);
        }

        // Small centre dot
        annulus(vc, mat, c, u, v, 0.0f, 0.06f + 0.03f * pulse, 0, Mth.TWO_PI, 12, 1.0f, 0.6f, 0.6f, 1.0f);

        // Short vertical pillar hinting where the beam will come down
        renderPillar(vc, mat, c);
    }

    private static void renderPillar(VertexConsumer vc, Matrix4f mat, Vector3f base) {
        Vector3f top = new Vector3f(base).add(0, PILLAR_HEIGHT, 0);
        Vector3f up = new Vector3f(0, 1, 0);
        Vector3f side = up.cross(base, new Vector3f());
        if (side.lengthSquared() < 1.0e-8f) return; // looking straight down the pillar
        side.normalize().mul(0.04f);
        quad(vc, mat,
                new Vector3f(base).sub(side), new Vector3f(base).add(side),
                new Vector3f(top).add(side), new Vector3f(top).sub(side),
                MARK_R, MARK_G, MARK_B, 0.6f, 0.0f);
    }

    // ---------------------------------------------------------------- geometry helpers

    /** Ring (or disc when inner == 0) lying in the plane spanned by u and v. */
    private static void annulus(VertexConsumer vc, Matrix4f mat, Vector3f c, Vector3f u, Vector3f v,
                                float inner, float outer, float from, float to, int segments,
                                float r, float g, float b, float a) {
        for (int i = 0; i < segments; i++) {
            float a1 = Mth.lerp(i / (float) segments, from, to);
            float a2 = Mth.lerp((i + 1) / (float) segments, from, to);
            Vector3f d1 = planeDir(u, v, a1);
            Vector3f d2 = planeDir(u, v, a2);
            quad(vc, mat,
                    new Vector3f(d1).mul(inner).add(c), new Vector3f(d1).mul(outer).add(c),
                    new Vector3f(d2).mul(outer).add(c), new Vector3f(d2).mul(inner).add(c),
                    r, g, b, a, a);
        }
    }

    /** Quad where p1/p2 get alpha {@code a1} and p3/p4 get alpha {@code a2}. */
    private static void quad(VertexConsumer vc, Matrix4f mat, Vector3f p1, Vector3f p2, Vector3f p3, Vector3f p4,
                             float r, float g, float b, float a1, float a2) {
        vc.addVertex(mat, p1.x, p1.y, p1.z).setColor(r, g, b, a1);
        vc.addVertex(mat, p2.x, p2.y, p2.z).setColor(r, g, b, a1);
        vc.addVertex(mat, p3.x, p3.y, p3.z).setColor(r, g, b, a2);
        vc.addVertex(mat, p4.x, p4.y, p4.z).setColor(r, g, b, a2);
    }

    private static Vector3f planeDir(Vector3f u, Vector3f v, float angle) {
        return new Vector3f(u).mul(Mth.cos(angle)).add(new Vector3f(v).mul(Mth.sin(angle)));
    }

    private static Vector3f relative(Vec3 p, Vec3 cam) {
        return new Vector3f((float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z));
    }

    private static float lighten(float channel) {
        return channel + (1.0f - channel) * 0.6f;
    }
}
