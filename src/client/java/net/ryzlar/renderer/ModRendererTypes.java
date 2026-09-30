package net.ryzlar.renderer;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.ryzlar.LaserMod;

public class ModRendererTypes {

    private static final Identifier BEAM_TEXTURE =
            Identifier.fromNamespaceAndPath("minecraft", "textures/entity/beacon_beam.png");

    /** Additive, untextured glow (same shader as lightning), no depth write so layers stack into light. */
    private static final RenderPipeline LASER_GLOW_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "pipeline/laser_glow"))
            .withVertexShader("core/rendertype_lightning")
            .withFragmentShader("core/rendertype_lightning")
            .withBlend(BlendFunction.LIGHTNING)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .build();

    /** Flat colored quads that are always drawn on top, for the in-world timer panel. */
    private static final RenderPipeline LASER_UI_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "pipeline/laser_ui"))
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .build();

    private static RenderType laserBeamTranslucent;
    private static RenderType laserGlow;
    private static RenderType laserUi;

    public static RenderType getLaserBeamTranslucent() {
        if (laserBeamTranslucent == null) {
            laserBeamTranslucent = RenderType.create(
                    "laser_beam_translucent",
                    RenderSetup.builder(RenderPipelines.BEACON_BEAM_TRANSLUCENT)
                            .withTexture("Sampler0", BEAM_TEXTURE)
                            .setOutputTarget(OutputTarget.MAIN_TARGET)
                            .createRenderSetup()
            );
        }
        return laserBeamTranslucent;
    }

    public static RenderType getLaserGlow() {
        if (laserGlow == null) {
            laserGlow = RenderType.create("laser_glow", RenderSetup.builder(LASER_GLOW_PIPELINE).createRenderSetup());
        }
        return laserGlow;
    }

    public static RenderType getLaserUi() {
        if (laserUi == null) {
            laserUi = RenderType.create("laser_ui", RenderSetup.builder(LASER_UI_PIPELINE).createRenderSetup());
        }
        return laserUi;
    }
}
