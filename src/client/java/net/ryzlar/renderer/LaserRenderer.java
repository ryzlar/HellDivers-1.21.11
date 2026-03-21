package net.ryzlar.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.ryzlar.ModClient.ModBeams;
import net.ryzlar.laser.BeamData;
import org.joml.Matrix4f;

public class LaserRenderer {

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        double camX = mc.gameRenderer.getMainCamera().position().x;
        double camY = mc.gameRenderer.getMainCamera().position().y;
        double camZ = mc.gameRenderer.getMainCamera().position().z;

        for (BeamData beam : ModBeams.LASER_ACTIVE_BEAMS) {
            renderBeam(poseStack, bufferSource, beam, camX, camY, camZ);
        }
    }

    private static void renderBeam(PoseStack poseStack, MultiBufferSource bufferSource, BeamData beam,
                                   double camX, double camY, double camZ) {
        double x = beam.x - camX;
        double startY = beam.startY - camY;
        double endY = beam.endY - camY;
        double z = beam.z - camZ;

        float time = (System.currentTimeMillis() % 10000L) / 10000.0f;
        float texV = -time * 5;

        float r = beam.r;
        float g = beam.g;
        float b = beam.b;

        poseStack.pushPose();
        poseStack.translate(x, startY, z);
        poseStack.mulPose(new org.joml.Quaternionf().rotateY(time * (float)(Math.PI * 2)));
        poseStack.translate(-x, -startY, -z);

        // This is the middle white beam
        renderLayer(poseStack, bufferSource, ModRendererTypes.getLaserBeamOpaque(), x, startY, endY, z, texV, 0.06f, 1.0f, 1.0f, 1.0f, 1.0f, true);

        // This is the 3 beams on the outside
        renderLayer(poseStack, bufferSource, ModRendererTypes.getLaserBeamTranslucent(), x, startY, endY, z, texV, 0.18f, 1.0f, 0.1f, 0.1f, 0.9f, false);
        renderLayer(poseStack, bufferSource, ModRendererTypes.getLaserBeamTranslucent(), x, startY, endY, z, texV, 0.35f, 1.0f, 0.0f, 0.0f, 0.5f, false);
        renderLayer(poseStack, bufferSource, ModRendererTypes.getLaserBeamTranslucent(), x, startY, endY, z, texV, 0.65f, 0.9f, 0.0f, 0.0f, 0.2f, false);

        poseStack.popPose();
    }

    private static void renderLayer(PoseStack poseStack, MultiBufferSource bufferSource,
                                    RenderType renderType,
                                    double x, double startY, double endY, double z,
                                    float texV, float width, float r, float g, float b, float a,
                                    boolean bothSides) {
        VertexConsumer vc = bufferSource.getBuffer(renderType);
        Matrix4f mat = poseStack.last().pose();

        for (int i = 0; i < 4; i++) {
            float cos = (float) Math.cos(Math.PI / 2 * i);
            float sin = (float) Math.sin(Math.PI / 2 * i);

            float x1 = (float) x + cos * width;
            float z1 = (float) z + sin * width;
            float x2 = (float) x - sin * width;
            float z2 = (float) z + cos * width;

            vc.addVertex(mat, x1, (float) endY,   z1).setColor(r, g, b, a).setUv(0, texV).setUv2(240, 240).setNormal(0, 1, 0);
            vc.addVertex(mat, x1, (float) startY, z1).setColor(r, g, b, a).setUv(0, texV + 1).setUv2(240, 240).setNormal(0, 1, 0);
            vc.addVertex(mat, x2, (float) startY, z2).setColor(r, g, b, a).setUv(1, texV + 1).setUv2(240, 240).setNormal(0, 1, 0);
            vc.addVertex(mat, x2, (float) endY,   z2).setColor(r, g, b, a).setUv(1, texV).setUv2(240, 240).setNormal(0, 1, 0);

            if (bothSides) {
                vc.addVertex(mat, x2, (float) endY,   z2).setColor(r, g, b, a).setUv(1, texV).setUv2(240, 240).setNormal(0, 1, 0);
                vc.addVertex(mat, x2, (float) startY, z2).setColor(r, g, b, a).setUv(1, texV + 1).setUv2(240, 240).setNormal(0, 1, 0);
                vc.addVertex(mat, x1, (float) startY, z1).setColor(r, g, b, a).setUv(0, texV + 1).setUv2(240, 240).setNormal(0, 1, 0);
                vc.addVertex(mat, x1, (float) endY,   z1).setColor(r, g, b, a).setUv(0, texV).setUv2(240, 240).setNormal(0, 1, 0);
            }
        }
    }
}