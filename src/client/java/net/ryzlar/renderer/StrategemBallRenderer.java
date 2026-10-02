package net.ryzlar.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.ryzlar.entities.StrategemBallEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The thrown beacon as a real 3D object: it keeps its model (and live programmed glow) in flight and tumbles
 * end over end around an axis perpendicular to its motion, faster when thrown harder - instead of the vanilla
 * camera-facing item sprite.
 */
public class StrategemBallRenderer extends EntityRenderer<StrategemBallEntity, StrategemBallRenderer.State> {

    public static class State extends EntityRenderState {
        public final ItemStackRenderState item = new ItemStackRenderState();
        public final Vector3f axis = new Vector3f(1, 0, 0);
        public float spin;
    }

    private final ItemModelResolver itemModelResolver;

    public StrategemBallRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(StrategemBallEntity ball, State state, float partialTick) {
        super.extractRenderState(ball, state, partialTick);
        itemModelResolver.updateForNonLiving(state.item, ball.getItem(), ItemDisplayContext.GROUND, ball);

        Vec3 motion = ball.getDeltaMovement();
        Vector3f horizontal = new Vector3f((float) motion.x, 0, (float) motion.z);
        if (horizontal.lengthSquared() > 1.0e-6f) {
            // tumble forward: rotate around the horizontal axis perpendicular to the flight direction
            state.axis.set(horizontal.z, 0, -horizontal.x).normalize();
        }
        float speed = (float) motion.length();
        state.spin = (ball.tickCount + partialTick) * (14.0f + speed * 30.0f);
    }

    @Override
    protected int getBlockLightLevel(StrategemBallEntity ball, BlockPos pos) {
        return Math.max(super.getBlockLightLevel(ball, pos), 10); // its own lights keep it readable at night
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0.0f, 0.12f, 0.0f);
        poseStack.mulPose(new Quaternionf().rotateAxis((float) Math.toRadians(state.spin), state.axis));
        poseStack.mulPose(Axis.YP.rotationDegrees(state.spin * 0.35f));
        poseStack.translate(0.0f, -0.12f, 0.0f);
        state.item.submit(poseStack, collector, state.lightCoords, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
