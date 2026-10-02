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
import net.ryzlar.fx.SceneTextures;

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

    /** Opaque, depth-writing quads: 3D rocks and the rift surface that hides whatever passes through it. */
    private static final RenderPipeline FX_SOLID_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "pipeline/fx_solid"))
            .withoutBlend()
            .withDepthWrite(true)
            .withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .build();

    /**
     * World effects, additive (fire, energy, flashes). Soft depth: fades out against the scene behind it instead of
     * being cut by block faces. UV0.x = softness in blocks. Registered so the shader is compiled with the vanilla ones
     * at resource load (errors show up at startup, not mid-game).
     */
    private static final RenderPipeline FX_SOFT_GLOW_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "pipeline/fx_soft_glow"))
            .withVertexShader(Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "core/fx_soft"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "core/fx_soft"))
            .withSampler("SceneDepth")
            .withBlend(BlendFunction.LIGHTNING)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .build());

    /** World effects, alpha blended (smoke, dust, ash, the void), with the same soft depth. */
    private static final RenderPipeline FX_SOFT_MATTER_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "pipeline/fx_soft_matter"))
            .withVertexShader(Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "core/fx_soft"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "core/fx_soft"))
            .withSampler("SceneDepth")
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .build());

    /** Heat haze and spatial lensing: re-draws the scene snapshot behind it with bent coordinates. */
    private static final RenderPipeline FX_DISTORT_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "pipeline/fx_distort"))
            .withVertexShader(Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "core/fx_distort"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "core/fx_distort"))
            .withSampler("SceneColor")
            .withSampler("SceneDepth")
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .build());

    private static RenderType fxSoftGlow;
    private static RenderType fxSoftMatter;
    private static RenderType fxDistort;

    public static RenderType getFxSoftGlow() {
        if (fxSoftGlow == null) {
            fxSoftGlow = RenderType.create("fx_soft_glow", RenderSetup.builder(FX_SOFT_GLOW_PIPELINE)
                    .withTexture("SceneDepth", SceneTextures.DEPTH)
                    .bufferSize(1 << 21)
                    .createRenderSetup());
        }
        return fxSoftGlow;
    }

    public static RenderType getFxSoftMatter() {
        if (fxSoftMatter == null) {
            fxSoftMatter = RenderType.create("fx_soft_matter", RenderSetup.builder(FX_SOFT_MATTER_PIPELINE)
                    .withTexture("SceneDepth", SceneTextures.DEPTH)
                    .bufferSize(1 << 21)
                    .createRenderSetup());
        }
        return fxSoftMatter;
    }

    public static RenderType getFxDistort() {
        if (fxDistort == null) {
            fxDistort = RenderType.create("fx_distort", RenderSetup.builder(FX_DISTORT_PIPELINE)
                    .withTexture("SceneColor", SceneTextures.COLOR)
                    .withTexture("SceneDepth", SceneTextures.DEPTH)
                    .createRenderSetup());
        }
        return fxDistort;
    }

    /** Forces class loading during client init, so the pipelines are registered before shaders load. */
    public static void initialize() {
    }

    private static RenderType laserBeamTranslucent;
    private static RenderType fxSolid;
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

    public static RenderType getFxSolid() {
        if (fxSolid == null) {
            fxSolid = RenderType.create("fx_solid", RenderSetup.builder(FX_SOLID_PIPELINE).createRenderSetup());
        }
        return fxSolid;
    }

    public static RenderType getLaserUi() {
        if (laserUi == null) {
            laserUi = RenderType.create("laser_ui", RenderSetup.builder(LASER_UI_PIPELINE).createRenderSetup());
        }
        return laserUi;
    }
}
