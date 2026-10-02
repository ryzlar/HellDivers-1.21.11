package net.ryzlar.strike;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.ryzlar.fx.FxContext;

/**
 * Client half of a multi-stage attack. Follows the same timeline as the server {@link StrikeSequence}
 * (same stratagem id, seed and start time), so what you see matches what happens.
 */
public interface StrikeVisual {

    /**
     * Heat haze and spatial lensing ({@link net.ryzlar.fx.FxDraw#distortion}), drawn first so it bends only the world
     * behind the strike, never the strike's own effects.
     */
    default void renderDistortion(FxContext ctx, StrikeEntity strike, float age) {
    }

    /**
     * Opaque, depth-writing geometry (rocks, the rift surface), drawn before all translucent effects of every strike
     * so smoke and glow blend over it correctly.
     */
    default void renderSolid(FxContext ctx, StrikeEntity strike, float age) {
    }

    /** World-space effects, every frame. {@code age} is in ticks, with partial tick. */
    void render(FxContext ctx, StrikeEntity strike, float age);

    /** In-world UI (countdowns etc.), drawn without fog after all world effects. */
    default void renderUi(FxContext ctx, StrikeEntity strike, float age) {
    }

    /** Once per tick of age: particles, camera effects, one-shot events (check {@code age == X}). */
    default void tick(StrikeEntity strike, ClientLevel level, int age, RandomSource random) {
    }
}
