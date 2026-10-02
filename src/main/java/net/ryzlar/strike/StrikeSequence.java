package net.ryzlar.strike;

import net.minecraft.server.level.ServerLevel;

/**
 * Server half of a multi-stage attack: everything that physically happens (damage, terrain, suction, sounds),
 * driven tick by tick. The client renders the same timeline from the stratagem id + seed of the
 * {@link StrikeEntity}, so keep shared timings and random layouts in public static members.
 */
public interface StrikeSequence {

    /** Total length in ticks; the strike entity removes itself afterwards. */
    int duration();

    /** Called every server tick with age 0, 1, 2, ... */
    void tick(StrikeEntity strike, ServerLevel level, int age);

    /** Called once when the sequence ends or the strike is removed early. */
    default void finish(StrikeEntity strike, ServerLevel level) {
    }
}
