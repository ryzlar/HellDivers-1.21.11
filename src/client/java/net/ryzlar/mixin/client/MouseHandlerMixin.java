package net.ryzlar.mixin.client;

import net.minecraft.client.MouseHandler;
import net.ryzlar.gui.StrategemInputHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scroll wheel flips stratagem menu pages while the menu is open (instead of changing hotbar slot). */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void lasermod$scrollPages(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (yOffset != 0 && StrategemInputHandler.isMenuActive()) {
            StrategemInputHandler.changePage(yOffset > 0 ? -1 : 1);
            ci.cancel();
        }
    }
}
