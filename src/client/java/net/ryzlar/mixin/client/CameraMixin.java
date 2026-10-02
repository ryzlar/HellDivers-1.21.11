package net.ryzlar.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.ryzlar.fx.CameraFx;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies {@link CameraFx} screen shake to the view only (the player's real rotation is untouched). */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    public abstract float yRot();

    @Shadow
    public abstract float xRot();

    @Inject(method = "setup", at = @At("TAIL"))
    private void lasermod$applyShake(Level level, Entity entity, boolean detached, boolean mirrored, float partialTick,
                                     CallbackInfo ci) {
        float[] offset = CameraFx.shakeOffset(partialTick);
        if (offset != null) {
            setRotation(yRot() + offset[0], xRot() + offset[1]);
        }
    }
}
