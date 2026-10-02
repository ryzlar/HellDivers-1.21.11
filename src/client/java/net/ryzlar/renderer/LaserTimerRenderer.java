package net.ryzlar.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.ryzlar.entities.OrbitalLaserEntity;
import org.joml.Matrix4f;
import org.joml.Vector3fc;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Countdown panel that sticks to the beam at the height you are looking at.
 * The panel slides smoothly along the beam as your view moves and always faces the camera.
 */
public class LaserTimerRenderer {

    private static final int PANEL_W = 104;
    private static final int PANEL_H = 40;
    private static final float PIXEL_SCALE = 0.0017f; // world units per pixel, per block of distance
    private static final float FOLLOW_SPEED = 10.0f;  // higher = snappier following
    private static final float CRITICAL_SECONDS = 5.0f;

    private static final Map<Integer, Float> ANCHOR_Y = new HashMap<>();
    private static long lastFrameNanos = 0L;

    public static void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                              List<OrbitalLaserEntity> lasers, Camera camera, float partialTick, float seconds) {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 0.0f : Math.min(0.1f, (now - lastFrameNanos) / 1.0e9f);
        lastFrameNanos = now;
        ANCHOR_Y.keySet().removeIf(id -> lasers.stream().noneMatch(l -> l.getId() == id));

        for (OrbitalLaserEntity laser : lasers) {
            renderPanel(poseStack, bufferSource, laser, camera, partialTick, seconds, dt);
        }
    }

    private static void renderPanel(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                    OrbitalLaserEntity laser, Camera camera, float partialTick, float seconds, float dt) {
        float remaining = laser.getRemainingTicks(partialTick);
        float elapsed = laser.getDuration() - remaining;
        float visibility = Mth.clamp(elapsed / 10.0f, 0.0f, 1.0f) * Mth.clamp(remaining / 10.0f, 0.0f, 1.0f);
        if (visibility <= 0.01f) return;

        Vec3 cam = camera.position();
        double anchorY = followLookHeight(laser, cam, camera.forwardVector(), dt);
        Vec3 anchor = new Vec3(laser.getX(), anchorY, laser.getZ());
        drawPanel(poseStack, bufferSource, camera, anchor, 0.7f, "ORBITAL LASER", remaining / 20.0f,
                remaining / laser.getDuration(), laser.getColor(), visibility, seconds);
    }

    /**
     * The countdown panel itself, reusable by any effect: faces the camera at {@code anchor}, keeps the same
     * size on screen, sits {@code gapWorld} blocks to the right of the anchor with a connector line.
     */
    public static void drawPanel(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Camera camera,
                                 Vec3 anchor, float gapWorld, String title, float secondsLeft, float progress,
                                 int accent, float visibility, float seconds) {
        Vec3 cam = camera.position();
        float distance = (float) Math.max(2.5, anchor.distanceTo(cam));
        float scale = distance * PIXEL_SCALE;

        poseStack.pushPose();
        poseStack.translate(anchor.x - cam.x, anchor.y - cam.y, anchor.z - cam.z);
        poseStack.mulPose(camera.rotation());
        poseStack.scale(scale, -scale, scale);

        // --- layout (pixels, y down); the beam axis is at x = 0
        boolean critical = secondsLeft <= CRITICAL_SECONDS;
        float blink = 0.5f + 0.5f * Mth.sin(seconds * Mth.TWO_PI * 2.0f);
        int timeColor = critical ? lerpColor(0xFFFFFF, accent, blink) : 0xFFFFFF;

        float gap = gapWorld / scale; // stay clear of the beam core, whatever the distance
        float x0 = gap + 10;
        float y0 = -PANEL_H / 2.0f;
        float x1 = x0 + PANEL_W;
        float y1 = y0 + PANEL_H;

        VertexConsumer vc = bufferSource.getBuffer(ModRendererTypes.getLaserUi());
        Matrix4f mat = poseStack.last().pose();

        // Connector from the beam to the panel, with a diamond marker on the beam
        rect(vc, mat, 4, -0.5f, x0, 0.5f, argb(0.8f * visibility, accent));
        diamond(vc, mat, 0, 0, 3.0f, argb(visibility, lighten(accent)));

        // Panel body + accent bar
        rect(vc, mat, x0, y0, x1, y1, argb(0.82f * visibility, 0x0A0D12));
        rect(vc, mat, x0, y0, x0 + 2, y1, argb(visibility, accent));
        rect(vc, mat, x0 + 2, y0, x1, y0 + 1, argb(0.12f * visibility, 0xFFFFFF));

        // Tactical corner brackets
        int bracket = argb(0.9f * visibility, accent);
        corner(vc, mat, x0 - 2, y0 - 2, 1, 1, bracket);
        corner(vc, mat, x1 + 2, y0 - 2, -1, 1, bracket);
        corner(vc, mat, x0 - 2, y1 + 2, 1, -1, bracket);
        corner(vc, mat, x1 + 2, y1 + 2, -1, -1, bracket);

        // Live indicator
        float dotAlpha = critical ? blink : 0.6f + 0.4f * Mth.sin(seconds * Mth.TWO_PI * 0.5f);
        rect(vc, mat, x1 - 9, y0 + 6, x1 - 6, y0 + 9, argb(dotAlpha * visibility, accent));

        // Progress bar
        float barX0 = x0 + 8, barX1 = x1 - 8, barY = y1 - 6;
        progress = Mth.clamp(progress, 0.0f, 1.0f);
        rect(vc, mat, barX0, barY, barX1, barY + 2, argb(0.18f * visibility, 0xFFFFFF));
        rect(vc, mat, barX0, barY, barX0 + (barX1 - barX0) * progress, barY + 2,
                argb(visibility, critical ? timeColor : accent));

        bufferSource.endBatch(ModRendererTypes.getLaserUi()); // panel first, text on top

        // --- text
        Font font = Minecraft.getInstance().font;
        text(font, poseStack, bufferSource, title, x0 + 8, y0 + 6, 0.75f, argb(visibility, lighten(accent)));

        String time = String.format(Locale.ROOT, "%.1f", secondsLeft);
        text(font, poseStack, bufferSource, time, x0 + 8, y0 + 14, 2.0f, argb(visibility, timeColor));
        text(font, poseStack, bufferSource, "SEC", x0 + 8 + font.width(time) * 2.0f + 3, y0 + 22, 0.75f,
                argb(0.6f * visibility, 0xFFFFFF));

        bufferSource.endBatch();
        poseStack.popPose();
    }

    /**
     * Height on the beam closest to the player's view ray, smoothed over time so the panel glides along.
     * Standard closest-point-between-two-lines, with the beam axis pointing straight up.
     */
    private static double followLookHeight(OrbitalLaserEntity laser, Vec3 cam, Vector3fc forward, float dt) {
        double baseY = laser.getY();
        double wx = laser.getX() - cam.x, wy = baseY - cam.y, wz = laser.getZ() - cam.z;
        double fy = forward.y();
        double denom = 1.0 - fy * fy;
        double dot = forward.x() * wx + fy * wy + forward.z() * wz;

        double target = cam.y; // fallback: eye height
        if (denom > 1.0e-4) {
            double alongRay = (dot - fy * wy) / denom;
            if (alongRay > 0) {
                target = baseY + (fy * dot - wy) / denom;
            }
        }
        double top = baseY + Math.min(laser.getBeamHeight() - 2, 256);
        target = Mth.clamp(target, baseY + 1.2, top);

        float current = ANCHOR_Y.getOrDefault(laser.getId(), (float) target);
        current += (float) (target - current) * (1.0f - (float) Math.exp(-dt * FOLLOW_SPEED));
        ANCHOR_Y.put(laser.getId(), current);
        return current;
    }

    // ---------------------------------------------------------------- drawing helpers

    private static void text(Font font, PoseStack poseStack, MultiBufferSource bufferSource, String text,
                             float x, float y, float size, int color) {
        poseStack.pushPose();
        poseStack.translate(x, y, 0);
        poseStack.scale(size, size, 1.0f);
        font.drawInBatch(text, 0, 0, color, false, poseStack.last().pose(), bufferSource,
                Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    private static void rect(VertexConsumer vc, Matrix4f mat, float x0, float y0, float x1, float y1, int argb) {
        vc.addVertex(mat, x0, y0, 0).setColor(argb);
        vc.addVertex(mat, x0, y1, 0).setColor(argb);
        vc.addVertex(mat, x1, y1, 0).setColor(argb);
        vc.addVertex(mat, x1, y0, 0).setColor(argb);
    }

    private static void diamond(VertexConsumer vc, Matrix4f mat, float cx, float cy, float r, int argb) {
        vc.addVertex(mat, cx, cy - r, 0).setColor(argb);
        vc.addVertex(mat, cx - r, cy, 0).setColor(argb);
        vc.addVertex(mat, cx, cy + r, 0).setColor(argb);
        vc.addVertex(mat, cx + r, cy, 0).setColor(argb);
    }

    /** L-shaped bracket with its corner at (x, y), arms pointing in direction (dx, dy). */
    private static void corner(VertexConsumer vc, Matrix4f mat, float x, float y, int dx, int dy, int argb) {
        float len = 6;
        rect(vc, mat, Math.min(x, x + dx * len), Math.min(y, y + dy), Math.max(x, x + dx * len), Math.max(y, y + dy), argb);
        rect(vc, mat, Math.min(x, x + dx), Math.min(y, y + dy * len), Math.max(x, x + dx), Math.max(y, y + dy * len), argb);
    }

    private static int argb(float alpha, int rgb) {
        return ((int) (Mth.clamp(alpha, 0.0f, 1.0f) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static int lighten(int rgb) {
        return lerpColor(rgb, 0xFFFFFF, 0.35f);
    }

    private static int lerpColor(int from, int to, float t) {
        int r = (int) Mth.lerp(t, (from >> 16) & 255, (to >> 16) & 255);
        int g = (int) Mth.lerp(t, (from >> 8) & 255, (to >> 8) & 255);
        int b = (int) Mth.lerp(t, from & 255, to & 255);
        return (r << 16) | (g << 8) | b;
    }
}
