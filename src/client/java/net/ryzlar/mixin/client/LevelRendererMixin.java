package net.ryzlar.mixin.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.state.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.ryzlar.fx.BallFx;
import net.ryzlar.fx.FxContext;
import net.ryzlar.fx.SceneTextures;
import net.ryzlar.renderer.LaserRenderer;
import net.ryzlar.strike.StrikeRenderer;
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
 * which then paint over our effects. Instead we add our own pass right before the late debug pass,
 * so lasers and strikes are drawn after everything else in the world, still depth-tested against terrain.
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Shadow @Final private RenderBuffers renderBuffers;
    @Shadow @Final private LevelTargetBundle targets;

    @Inject(method = "addLateDebugPass", at = @At("HEAD"))
    private void lasermod$addEffectsPass(FrameGraphBuilder builder, CameraRenderState cameraState, GpuBufferSlice fog,
                                         Matrix4f projection, CallbackInfo ci) {
        boolean lasers = LaserRenderer.hasLasers();
        boolean strikes = StrikeRenderer.hasStrikes();
        boolean balls = BallFx.hasWork();
        if (!lasers && !strikes && !balls) return;

        FramePass pass = builder.addPass("lasermod_effects");
        this.targets.main = pass.readsAndWrites(this.targets.main);
        ResourceHandle<RenderTarget> main = this.targets.main;

        pass.executes(() -> {
            RenderTarget target = main.get();
            // Snapshot of the finished world (depth + color) for soft-depth effects and heat haze
            SceneTextures.capture(target);
            RenderSystem.outputColorTextureOverride = target.getColorTextureView();
            RenderSystem.outputDepthTextureOverride = target.getDepthTextureView();
            MultiBufferSource.BufferSource buffers = this.renderBuffers.bufferSource();
            GpuBufferSlice noFog = LaserRenderer.noFog();

            // Strikes are huge and must read from far away: no world fog
            RenderSystem.setShaderFog(noFog);
            StrikeRenderer.renderWorld(buffers);
            buffers.endBatch();

            // Thrown beacons: trails and landing beams
            BallFx.render(new FxContext(buffers, new PoseStack(), Minecraft.getInstance().gameRenderer.getMainCamera(),
                    Minecraft.getInstance().level, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)));
            buffers.endBatch();

            RenderSystem.setShaderFog(fog);
            LaserRenderer.renderWorld(buffers);

            // In-world UI (timers, countdowns)
            RenderSystem.setShaderFog(noFog);
            LaserRenderer.renderUi(buffers);
            StrikeRenderer.renderUi(buffers);
            buffers.endBatch();

            RenderSystem.setShaderFog(fog);
            RenderSystem.outputColorTextureOverride = null;
            RenderSystem.outputDepthTextureOverride = null;
        });
    }
}
