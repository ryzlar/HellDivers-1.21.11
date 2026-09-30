package net.ryzlar.mixin.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.ryzlar.renderer.LaserRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * renderLevel() only builds a frame graph; passes run later, in order. Drawing directly from an inject
 * (or from WorldRenderEvents.END_MAIN) happens before clouds, weather and the translucent/water composite,
 * which then paint over the beam. Instead we add our own pass right before the late debug pass,
 * so the laser is drawn after everything else in the world, still depth-tested against terrain.
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Shadow @Final private RenderBuffers renderBuffers;
    @Shadow @Final private LevelTargetBundle targets;

    @Inject(method = "addLateDebugPass", at = @At("HEAD"))
    private void lasermod$addLaserPass(FrameGraphBuilder builder, CameraRenderState cameraState, GpuBufferSlice fog,
                                       Matrix4f projection, CallbackInfo ci) {
        if (!LaserRenderer.hasLasers()) return;

        FramePass pass = builder.addPass("lasermod_orbital_laser");
        this.targets.main = pass.readsAndWrites(this.targets.main);
        ResourceHandle<RenderTarget> main = this.targets.main;

        pass.executes(() -> {
            RenderTarget target = main.get();
            RenderSystem.outputColorTextureOverride = target.getColorTextureView();
            RenderSystem.outputDepthTextureOverride = target.getDepthTextureView();
            RenderSystem.setShaderFog(fog);

            LaserRenderer.render(this.renderBuffers.bufferSource(), fog);

            RenderSystem.outputColorTextureOverride = null;
            RenderSystem.outputDepthTextureOverride = null;
        });
    }
}
