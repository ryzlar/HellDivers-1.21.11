package net.ryzlar.renderer;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public class ModRendererTypes {

    private static final Identifier BEAM_TEXTURE =
            Identifier.fromNamespaceAndPath("minecraft", "textures/entity/beacon_beam.png");

    private static RenderType laserBeamOpaque;
    private static RenderType laserBeamTranslucent;

    public static RenderType getLaserBeamOpaque() {
        if (laserBeamOpaque == null) {
            laserBeamOpaque = RenderType.create(
                    "laser_beam_opaque",
                    RenderSetup.builder(RenderPipelines.BEACON_BEAM_OPAQUE)
                            .withTexture("Sampler0", BEAM_TEXTURE)
                            .createRenderSetup()
            );
        }
        return laserBeamOpaque;
    }

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
}