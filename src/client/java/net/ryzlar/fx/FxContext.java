package net.ryzlar.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.ryzlar.renderer.ModRendererTypes;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Everything a world-space effect needs for one frame. Positions handed to {@link FxDraw} are
 * camera-relative ({@link #rel}), so the camera sits at the origin.
 */
public final class FxContext {

    public final MultiBufferSource.BufferSource buffers;
    public final PoseStack poseStack;
    public final Matrix4f pose;
    public final Camera camera;
    public final Vec3 cam;
    public final Vector3f left;
    public final Vector3f up;
    public final ClientLevel level;
    public final float partialTick;

    public FxContext(MultiBufferSource.BufferSource buffers, PoseStack poseStack, Camera camera, ClientLevel level, float partialTick) {
        this.buffers = buffers;
        this.poseStack = poseStack;
        this.pose = poseStack.last().pose();
        this.camera = camera;
        this.cam = camera.position();
        this.left = new Vector3f(camera.leftVector());
        this.up = new Vector3f(camera.upVector());
        this.level = level;
        this.partialTick = partialTick;
    }

    public Vector3f rel(Vec3 pos) {
        return new Vector3f((float) (pos.x - cam.x), (float) (pos.y - cam.y), (float) (pos.z - cam.z));
    }

    public Vector3f rel(double x, double y, double z) {
        return new Vector3f((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z));
    }

    /**
     * Additive light: fire, energy, lightning. Fetch right before drawing; switching layers ends the other one.
     * Soft depth: fades against the terrain behind it over {@link FxDraw#softness} blocks.
     */
    public VertexConsumer glow() {
        return buffers.getBuffer(ModRendererTypes.getFxSoftGlow());
    }

    /** Alpha-blended matter: smoke, dust, ash, the void. Soft depth like {@link #glow()}. */
    public VertexConsumer matter() {
        return buffers.getBuffer(ModRendererTypes.getFxSoftMatter());
    }

    /** Heat haze / lensing sprites (see {@link FxDraw#distortion}); only valid inside a visual's distortion pass. */
    public VertexConsumer distort() {
        return buffers.getBuffer(ModRendererTypes.getFxDistort());
    }

    /** Camera right vector (screen x), for geometry that has to line up with screen axes. */
    public Vector3f right() {
        return new Vector3f(left).negate();
    }

    /** Opaque and depth-writing: real 3D objects, occluders. */
    public VertexConsumer solid() {
        return buffers.getBuffer(ModRendererTypes.getFxSolid());
    }

    public VertexConsumer layer(boolean additive) {
        return additive ? glow() : matter();
    }
}
