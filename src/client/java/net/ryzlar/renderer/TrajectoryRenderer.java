package net.ryzlar.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

public class TrajectoryRenderer {

    private static Vec3 lastLandingPos = null;

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, float power) {

        Minecraft mc = Minecraft.getInstance();

        Player player = mc.player;

        if (player == null) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().position();

        List<Vec3> points = TrajectoryCalc.calculate(player, power);
        if (points.size() < 2) return;

        float time = (System.currentTimeMillis() % 2000L) / 2000.0f;
        float pulse = (float)(Math.sin(time * Math.PI * 2) * 0.5 + 0.5);

        double yaw = Math.toRadians(player.getYRot());
        double offsetX = Math.cos(yaw) * 0.4;
        double offsetZ = Math.sin(yaw) * 0.4;

        VertexConsumer vc = bufferSource.getBuffer(RenderTypes.debugQuads());
        Matrix4f mat = poseStack.last().pose();

        for (int i = 0; i < points.size(); i += 3) {
            Vec3 p = points.get(i).add(offsetX, 0, offsetZ);
            float progress = i / (float) points.size();

            if (p.distanceTo(player.getEyePosition()) < 1.5) continue;

//            float dashPhase = (progress - time * 1.5f) % 1.0f;
//            if (dashPhase < 0) dashPhase += 1.0f;
//            if (dashPhase > 0.5f) continue;

            float r = Math.min(1.0f, progress * 2.0f);
            float g = Math.min(1.0f, (1.0f - progress) * 2.0f);
            float b = 0.0f;
            float alpha = 0.7f + pulse * 0.3f;

            float px = (float)(p.x - cam.x);
            float py = (float)(p.y - cam.y);
            float pz = (float)(p.z - cam.z);

            float cubeSize = 0.06f + pulse * 0.02f;
            renderCube(vc, mat, px, py, pz, cubeSize, r, g, b, alpha);
        }

        if (bufferSource instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch(RenderTypes.debugQuads());
        }

        if (!points.isEmpty()) {
            lastLandingPos = points.get(points.size() - 1).add(offsetX, 0, offsetZ);
        }

        if (lastLandingPos != null) {
            renderLandingZone(poseStack, bufferSource, lastLandingPos, cam, pulse, time);
        }
    }

    private static void renderDot(VertexConsumer vc, Matrix4f mat,
                                  float x, float y, float z,
                                  float size, float r, float g, float b, float a) {
        // Quad als punt
        vc.addVertex(mat, x - size, y, z - size).setColor(r, g, b, a);
        vc.addVertex(mat, x - size, y, z + size).setColor(r, g, b, a);
        vc.addVertex(mat, x + size, y, z + size).setColor(r, g, b, a);
        vc.addVertex(mat, x + size, y, z - size).setColor(r, g, b, a);
    }

    private static void renderCube(VertexConsumer vc, Matrix4f mat,
                                   float x, float y, float z,
                                   float size, float r, float g, float b, float a) {
        float s = size;

        // Voor
        vc.addVertex(mat, x-s, y-s, z+s).setColor(r, g, b, a);
        vc.addVertex(mat, x+s, y-s, z+s).setColor(r, g, b, a);
        vc.addVertex(mat, x+s, y+s, z+s).setColor(r, g, b, a);
        vc.addVertex(mat, x-s, y+s, z+s).setColor(r, g, b, a);

        // Achter
        vc.addVertex(mat, x+s, y-s, z-s).setColor(r, g, b, a);
        vc.addVertex(mat, x-s, y-s, z-s).setColor(r, g, b, a);
        vc.addVertex(mat, x-s, y+s, z-s).setColor(r, g, b, a);
        vc.addVertex(mat, x+s, y+s, z-s).setColor(r, g, b, a);

        // Links
        vc.addVertex(mat, x-s, y-s, z-s).setColor(r, g, b, a);
        vc.addVertex(mat, x-s, y-s, z+s).setColor(r, g, b, a);
        vc.addVertex(mat, x-s, y+s, z+s).setColor(r, g, b, a);
        vc.addVertex(mat, x-s, y+s, z-s).setColor(r, g, b, a);

        // Rechts
        vc.addVertex(mat, x+s, y-s, z+s).setColor(r, g, b, a);
        vc.addVertex(mat, x+s, y-s, z-s).setColor(r, g, b, a);
        vc.addVertex(mat, x+s, y+s, z-s).setColor(r, g, b, a);
        vc.addVertex(mat, x+s, y+s, z+s).setColor(r, g, b, a);

        // Boven
        vc.addVertex(mat, x-s, y+s, z+s).setColor(r, g, b, a);
        vc.addVertex(mat, x+s, y+s, z+s).setColor(r, g, b, a);
        vc.addVertex(mat, x+s, y+s, z-s).setColor(r, g, b, a);
        vc.addVertex(mat, x-s, y+s, z-s).setColor(r, g, b, a);

        // Onder
        vc.addVertex(mat, x-s, y-s, z-s).setColor(r, g, b, a);
        vc.addVertex(mat, x+s, y-s, z-s).setColor(r, g, b, a);
        vc.addVertex(mat, x+s, y-s, z+s).setColor(r, g, b, a);
        vc.addVertex(mat, x-s, y-s, z+s).setColor(r, g, b, a);
    }

    private static void renderLandingZone(PoseStack poseStack, MultiBufferSource bufferSource,
                                          Vec3 landing, Vec3 cam, float pulse, float time) {
        VertexConsumer vc = bufferSource.getBuffer(RenderTypes.debugQuads());
        Matrix4f mat = poseStack.last().pose();

        float cx = (float)(landing.x - cam.x);
        float cy = (float)(landing.y - cam.y);
        float cz = (float)(landing.z - cam.z);

        // Pulserende buitenste cirkel van puntjes
        float outerRadius = 0.9f + pulse * 0.3f;
        int segments = 32;
        float rotOffset = time * (float)(Math.PI * 2);

        for (int i = 0; i < segments; i++) {
            // Elke 3e punt weglaten voor dash effect
            if (i % 3 == 0) continue;

            float angle = (float)(i / (double) segments * Math.PI * 2) + rotOffset;
            float x = cx + (float)Math.cos(angle) * outerRadius;
            float z = cz + (float)Math.sin(angle) * outerRadius;

            float alpha = 0.5f + pulse * 0.5f;
            float dotSize = 0.05f + pulse * 0.03f;
            renderDot(vc, mat, x, cy + 0.05f, z, dotSize, 1f, 0.1f, 0.1f, alpha);
        }

        // Binnenste snelle roterende cirkel
        float innerRadius = 0.25f + pulse * 0.08f;
        float fastRot = time * (float)(Math.PI * 6);
        for (int i = 0; i < 12; i++) {
            if (i % 2 == 0) continue;
            float angle = (float)(i / 12.0 * Math.PI * 2) + fastRot;
            float x = cx + (float)Math.cos(angle) * innerRadius;
            float z = cz + (float)Math.sin(angle) * innerRadius;
            renderDot(vc, mat, x, cy + 0.05f, z, 0.04f, 1f, 0.4f, 0.4f, 1f);
        }

        // Kruis in het midden — pulserende alpha
        float crossAlpha = 0.6f + pulse * 0.4f;
        float crossSize = 0.08f;
        float crossLength = 0.2f;

        // Horizontale balk
        renderDot(vc, mat, cx - crossLength, cy + 0.05f, cz, crossSize, 1f, 1f, 1f, crossAlpha);
        renderDot(vc, mat, cx, cy + 0.05f, cz, crossSize, 1f, 1f, 1f, crossAlpha);
        renderDot(vc, mat, cx + crossLength, cy + 0.05f, cz, crossSize, 1f, 1f, 1f, crossAlpha);

        // Verticale balk
        renderDot(vc, mat, cx, cy + 0.05f, cz - crossLength, crossSize, 1f, 1f, 1f, crossAlpha);
        renderDot(vc, mat, cx, cy + 0.05f, cz + crossLength, crossSize, 1f, 1f, 1f, crossAlpha);

        if (bufferSource instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch(RenderTypes.debugQuads());
        }
    }
}