package net.ryzlar.renderer;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ryzlar.entities.OrbitalLaserEntity;
import net.ryzlar.mixin.client.GameRendererAccessor;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws every {@link OrbitalLaserEntity} in the client level. Called from our own frame-graph pass
 * (see LevelRendererMixin), so the beam is composited after water, clouds and weather.
 */
public class LaserRenderer {

    private static final float IGNITE_TICKS = 8.0f;     // beam grows in
    private static final float COLLAPSE_TICKS = 15.0f;  // beam shrinks away at the end
    // Break points (blocks above the base) for the vertical brightness falloff
    private static final float[] HEIGHT_STEPS = {0, 2, 6, 16, 40, 96, 200};

    private static final List<OrbitalLaserEntity> LASERS = new ArrayList<>();

    /** Collects the lasers for this frame; returns false when there is nothing to draw. */
    public static boolean hasLasers() {
        LASERS.clear();
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return false;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof OrbitalLaserEntity laser && !laser.isRemoved()) {
                LASERS.add(laser);
            }
        }
        return !LASERS.isEmpty();
    }

    /** Fog buffer that disables fog; used for UI and for strike effects that must read from far away. */
    public static GpuBufferSlice noFog() {
        FogRenderer fogRenderer = ((GameRendererAccessor) Minecraft.getInstance().gameRenderer).lasermod$getFogRenderer();
        return fogRenderer.getBuffer(FogRenderer.FogMode.NONE);
    }

    public static void renderWorld(MultiBufferSource.BufferSource bufferSource) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || LASERS.isEmpty()) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.position();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float seconds = ((mc.level.getGameTime() % 24000L) + partialTick) / 20.0f;
        PoseStack poseStack = new PoseStack();

        for (OrbitalLaserEntity laser : LASERS) {
            renderLaser(poseStack, bufferSource, laser, camera, cam, partialTick, seconds);
        }
        bufferSource.endBatch(ModRendererTypes.getLaserBeamTranslucent());
        bufferSource.endBatch(ModRendererTypes.getLaserGlow());
    }

    /** Timer panels; call with fog disabled so they stay readable at any distance. */
    public static void renderUi(MultiBufferSource.BufferSource bufferSource) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || LASERS.isEmpty()) return;
        Camera camera = mc.gameRenderer.getMainCamera();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float seconds = ((mc.level.getGameTime() % 24000L) + partialTick) / 20.0f;
        LaserTimerRenderer.render(new PoseStack(), bufferSource, LASERS, camera, partialTick, seconds);
    }

    // ---------------------------------------------------------------- beam

    private static void renderLaser(PoseStack poseStack, MultiBufferSource bufferSource, OrbitalLaserEntity laser,
                                    Camera camera, Vec3 cam, float partialTick, float seconds) {
        float remaining = laser.getRemainingTicks(partialTick);
        float elapsed = laser.getDuration() - remaining;

        float ignite = smoothstep(Mth.clamp(elapsed / IGNITE_TICKS, 0.0f, 1.0f));
        float collapse = Mth.clamp(remaining / COLLAPSE_TICKS, 0.0f, 1.0f);
        float size = ignite * (0.2f + 0.8f * collapse);
        if (size <= 0.001f) return;

        float flash = 1.0f + 1.5f * Math.max(0.0f, 1.0f - elapsed / 6.0f); // bright burst on impact
        float flicker = 0.9f + 0.06f * Mth.sin(seconds * 47.0f) + 0.04f * Mth.sin(seconds * 113.0f);
        float intensity = collapse * flicker;

        int color = laser.getColor();
        float r = ((color >> 16) & 255) / 255.0f;
        float g = ((color >> 8) & 255) / 255.0f;
        float b = (color & 255) / 255.0f;

        float bx = (float) (laser.getX() - cam.x);
        float by = (float) (laser.getY() - cam.y);
        float bz = (float) (laser.getZ() - cam.z);
        float height = laser.getBeamHeight();

        // Horizontal vector perpendicular to the view direction, so glow strips always face the camera
        float dx = -bx, dz = -bz;
        float len = Mth.sqrt(dx * dx + dz * dz);
        float sx, sz;
        if (len > 0.05f) {
            sx = -dz / len;
            sz = dx / len;
        } else {
            Vector3fc left = camera.leftVector();
            float l = Mth.sqrt(left.x() * left.x() + left.z() * left.z());
            sx = l > 1.0e-4f ? left.x() / l : 1.0f;
            sz = l > 1.0e-4f ? left.z() / l : 0.0f;
        }

        Matrix4f mat = poseStack.last().pose();

        // Energy texture: rotating beacon-textured prism inside the glow
        renderEnergyPrism(poseStack, bufferSource, bx, by, bz, height, 0.22f * size, r, g, b, 0.55f * collapse, seconds);

        VertexConsumer glow = bufferSource.getBuffer(ModRendererTypes.getLaserGlow());
        float lr = lighten(r, 0.4f), lg = lighten(g, 0.4f), lb = lighten(b, 0.4f);
        glowColumn(glow, mat, bx, by, bz, sx, sz, height, 2.6f * size, r, g, b, 0.14f * intensity);
        glowColumn(glow, mat, bx, by, bz, sx, sz, height, 1.1f * size, r, g, b, 0.4f * intensity);
        glowColumn(glow, mat, bx, by, bz, sx, sz, height, 0.42f * size, lr, lg, lb, 0.85f * intensity);
        glowColumn(glow, mat, bx, by, bz, sx, sz, height, 0.13f * size, 1.0f, 0.96f, 0.92f, Math.min(1.0f, intensity * flash));

        renderImpact(glow, mat, bx, by + 0.03f, bz, size, r, g, b, intensity, flash, seconds);
    }

    /** A vertical strip with soft edges (bright centre, transparent sides), fading out with height. */
    private static void glowColumn(VertexConsumer vc, Matrix4f mat, float x, float y, float z, float sx, float sz,
                                   float height, float halfWidth, float r, float g, float b, float alpha) {
        float ex = sx * halfWidth, ez = sz * halfWidth;
        for (int i = 0; i < HEIGHT_STEPS.length; i++) {
            float h0 = HEIGHT_STEPS[i];
            if (h0 >= height) break;
            float h1 = i + 1 < HEIGHT_STEPS.length ? Math.min(HEIGHT_STEPS[i + 1], height) : height;
            float a0 = alpha * heightFalloff(h0);
            float a1 = alpha * heightFalloff(h1);
            float y0 = y + h0, y1 = y + h1;

            for (int side = -1; side <= 1; side += 2) {
                vc.addVertex(mat, x, y0, z).setColor(r, g, b, a0);
                vc.addVertex(mat, x + ex * side, y0, z + ez * side).setColor(r, g, b, 0.0f);
                vc.addVertex(mat, x + ex * side, y1, z + ez * side).setColor(r, g, b, 0.0f);
                vc.addVertex(mat, x, y1, z).setColor(r, g, b, a1);
            }
        }
    }

    private static float heightFalloff(float h) {
        return 1.0f / (1.0f + h / 70.0f);
    }

    private static void renderEnergyPrism(PoseStack poseStack, MultiBufferSource bufferSource, float x, float y, float z,
                                          float height, float width, float r, float g, float b, float a, float seconds) {
        VertexConsumer vc = bufferSource.getBuffer(ModRendererTypes.getLaserBeamTranslucent());
        float texV = -seconds * 2.5f;
        float vEnd = texV + height / 3.0f; // repeat the texture along the beam instead of stretching it

        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(new Quaternionf().rotateY(seconds * 3.0f));
        Matrix4f mat = poseStack.last().pose();

        for (int i = 0; i < 4; i++) {
            float angle = Mth.HALF_PI * i;
            float x1 = Mth.cos(angle) * width, z1 = Mth.sin(angle) * width;
            float x2 = -Mth.sin(angle) * width, z2 = Mth.cos(angle) * width;
            vc.addVertex(mat, x1, height, z1).setColor(r, g, b, a).setUv(0, texV).setUv2(240, 240).setNormal(0, 1, 0);
            vc.addVertex(mat, x1, 0, z1).setColor(r, g, b, a).setUv(0, vEnd).setUv2(240, 240).setNormal(0, 1, 0);
            vc.addVertex(mat, x2, 0, z2).setColor(r, g, b, a).setUv(1, vEnd).setUv2(240, 240).setNormal(0, 1, 0);
            vc.addVertex(mat, x2, height, z2).setColor(r, g, b, a).setUv(1, texV).setUv2(240, 240).setNormal(0, 1, 0);
        }
        poseStack.popPose();
    }

    // ---------------------------------------------------------------- ground impact

    private static void renderImpact(VertexConsumer vc, Matrix4f mat, float x, float y, float z, float size,
                                     float r, float g, float b, float intensity, float flash, float seconds) {
        // Heat glow on the ground
        disc(vc, mat, x, y, z, 3.4f * size, r, g, b, 0.5f * intensity * flash);
        disc(vc, mat, x, y, z, 1.2f * size, lighten(r, 0.5f), lighten(g, 0.5f), lighten(b, 0.5f), 0.7f * intensity);

        // Rotating hot rim
        float rim = 0.95f * size;
        ring(vc, mat, x, y, z, rim, 0.12f * size, lighten(r, 0.3f), lighten(g, 0.3f), lighten(b, 0.3f),
                (0.6f + 0.2f * Mth.sin(seconds * 9.0f)) * intensity);

        // Shockwaves rolling outwards
        for (int i = 0; i < 3; i++) {
            float phase = (seconds * 0.9f + i / 3.0f) % 1.0f;
            float radius = (0.8f + phase * 5.0f) * size;
            float fade = (1.0f - phase) * (1.0f - phase);
            ring(vc, mat, x, y, z, radius, 0.35f * size, r, g, b, 0.45f * fade * intensity);
        }
    }

    /** Flat radial gradient disc: centre alpha to transparent edge. */
    private static void disc(VertexConsumer vc, Matrix4f mat, float x, float y, float z, float radius,
                             float r, float g, float b, float a) {
        int segments = 32;
        for (int i = 0; i < segments; i++) {
            float a1 = Mth.TWO_PI * i / segments, a2 = Mth.TWO_PI * (i + 1) / segments;
            vc.addVertex(mat, x, y, z).setColor(r, g, b, a);
            vc.addVertex(mat, x + Mth.cos(a1) * radius, y, z + Mth.sin(a1) * radius).setColor(r, g, b, 0.0f);
            vc.addVertex(mat, x + Mth.cos(a2) * radius, y, z + Mth.sin(a2) * radius).setColor(r, g, b, 0.0f);
            vc.addVertex(mat, x, y, z).setColor(r, g, b, a);
        }
    }

    /** Flat ring that is brightest at {@code radius} and fades out to both sides. */
    private static void ring(VertexConsumer vc, Matrix4f mat, float x, float y, float z, float radius, float halfWidth,
                             float r, float g, float b, float a) {
        int segments = 48;
        float inner = Math.max(0.0f, radius - halfWidth), outer = radius + halfWidth;
        for (int i = 0; i < segments; i++) {
            float a1 = Mth.TWO_PI * i / segments, a2 = Mth.TWO_PI * (i + 1) / segments;
            float c1 = Mth.cos(a1), s1 = Mth.sin(a1), c2 = Mth.cos(a2), s2 = Mth.sin(a2);
            // inner half: transparent -> bright
            vc.addVertex(mat, x + c1 * inner, y, z + s1 * inner).setColor(r, g, b, 0.0f);
            vc.addVertex(mat, x + c1 * radius, y, z + s1 * radius).setColor(r, g, b, a);
            vc.addVertex(mat, x + c2 * radius, y, z + s2 * radius).setColor(r, g, b, a);
            vc.addVertex(mat, x + c2 * inner, y, z + s2 * inner).setColor(r, g, b, 0.0f);
            // outer half: bright -> transparent
            vc.addVertex(mat, x + c1 * radius, y, z + s1 * radius).setColor(r, g, b, a);
            vc.addVertex(mat, x + c1 * outer, y, z + s1 * outer).setColor(r, g, b, 0.0f);
            vc.addVertex(mat, x + c2 * outer, y, z + s2 * outer).setColor(r, g, b, 0.0f);
            vc.addVertex(mat, x + c2 * radius, y, z + s2 * radius).setColor(r, g, b, a);
        }
    }

    // ---------------------------------------------------------------- particles

    /** Sparks, embers and smoke at the impact point. Call once per client tick. */
    public static void tickParticles(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) return;
        RandomSource random = level.getRandom();

        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof OrbitalLaserEntity laser) || laser.getRemainingTicks(0.0f) < COLLAPSE_TICKS) continue;
            double x = laser.getX(), y = laser.getY(), z = laser.getZ();

            for (int i = 0; i < 2; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double dist = 0.3 + random.nextDouble() * 1.2;
                level.addParticle(ParticleTypes.FLAME, x + Math.cos(angle) * dist, y + 0.1, z + Math.sin(angle) * dist,
                        Math.cos(angle) * 0.05, 0.05 + random.nextDouble() * 0.1, Math.sin(angle) * 0.05);
            }
            double angle = random.nextDouble() * Math.PI * 2;
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y + random.nextDouble() * 4.0, z,
                    Math.cos(angle) * 0.4, 0.1, Math.sin(angle) * 0.4);
            if (random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.LARGE_SMOKE, x + random.nextGaussian() * 0.8, y + 0.2, z + random.nextGaussian() * 0.8,
                        0.0, 0.08, 0.0);
            }
            if (random.nextInt(6) == 0) {
                level.addParticle(ParticleTypes.LAVA, x, y + 0.1, z, 0.0, 0.0, 0.0);
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private static float smoothstep(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    private static float lighten(float channel, float amount) {
        return channel + (1.0f - channel) * amount;
    }
}
