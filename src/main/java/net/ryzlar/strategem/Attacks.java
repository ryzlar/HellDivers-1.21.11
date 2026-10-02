package net.ryzlar.strategem;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.ryzlar.strike.MeteorStrike;
import net.ryzlar.strike.NuclearStrike;
import net.ryzlar.strike.OrbitalLightningStrike;
import net.ryzlar.strike.StrikeEntity;
import net.ryzlar.strike.VoidRiftStrike;
import net.ryzlar.strike.VolcanicEruptionStrike;

/**
 * Every attack a stratagem ball can call in. The ball only knows which {@link Strategem} it carries;
 * on impact it runs that stratagem's attack, which points to one of these methods.
 *
 * <p>Simple attacks happen right here. Multi-stage attacks start a {@link StrikeEntity} that runs a
 * {@link net.ryzlar.strike.StrikeSequence} on the server (damage, terrain) while every client renders
 * the matching visual for the same stratagem id (see the client {@code StrikeVisuals}).
 *
 * <p>To add an attack: write a {@code public static void name(Attack.Context ctx)} here
 * and register it in {@link Strategems}.
 */
public final class Attacks {

    private Attacks() {
    }

    // ---------------------------------------------------------------- page 1

    public static void voidRift(Attack.Context ctx) {
        StrikeEntity.launch(ctx, new VoidRiftStrike());
    }

    public static void meteorStrike(Attack.Context ctx) {
        StrikeEntity.launch(ctx, new MeteorStrike());
    }

    public static void orbitalLightning(Attack.Context ctx) {
        StrikeEntity.launch(ctx, new OrbitalLightningStrike());
    }

    public static void volcanicEruption(Attack.Context ctx) {
        StrikeEntity.launch(ctx, new VolcanicEruptionStrike());
    }

    public static void nuclearStrike(Attack.Context ctx) {
        StrikeEntity.launch(ctx, new NuclearStrike());
    }

    // ---------------------------------------------------------------- empty ball

    /** What happens when an unprogrammed ball lands: a harmless fizzle. */
    public static void fizzle(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y + 0.1, pos.z, 12, 0.15, 0.1, 0.15, 0.01);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.PLAYERS, 0.5f, 1.6f);
    }
}
