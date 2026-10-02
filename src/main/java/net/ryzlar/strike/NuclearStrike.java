package net.ryzlar.strike;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.ryzlar.sound.ModSounds;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Nuclear Strike (catastrophic).
 *
 * <p>Timeline (ticks):
 * <ol>
 *     <li>{@code 0..DETONATION} countdown: alarm, the light drains away, a heartbeat, the warhead streaks down in
 *     the last second and a half.</li>
 *     <li>{@code DETONATION}: thermal flash (burns whatever can see the fireball), ground zero is vaporized, the
 *     fireball forms and burns everything inside it for three seconds ({@link #FIREBALL}).</li>
 *     <li>The blast wave rolls out at {@link #SHOCKWAVE_SPEED} to {@link #MAX_RADIUS}; damage arrives with it and falls
 *     off with distance ({@link #BLAST}): lethal at the fireball, crushing close by, severe at medium range,
 *     meaningful at the edge. Cover helps only outside the inner zone.</li>
 *     <li>Crater, scorched ring, fires on the burnt ground; the mushroom cloud rises for half a minute and drifts
 *     apart; ash falls.</li>
 * </ol>
 * Terrain damage is bounded: nothing outside {@link #MAX_RADIUS} is touched.
 */
public class NuclearStrike implements StrikeSequence {

    public static final int DETONATION = 200;            // 10 s countdown
    public static final float SHOCKWAVE_SPEED = 2.6f;    // blocks per tick
    public static final float MAX_RADIUS = 150.0f;
    public static final float VAPORIZE_RADIUS = 18.0f;
    public static final float FIREBALL_RADIUS = 30.0f;
    public static final float FIREBALL_ALTITUDE = 8.0f;
    public static final int CRATER_RADIUS = 30;
    public static final int DURATION = 1500;

    /** The blast wave: lethal at the fireball, crushing in the inner zone, survivable toward the edge. */
    public static final DamageProfile BLAST = DamageProfile.of(0, 2500, 18, 1000, 30, 280, 50, 110, 80, 45, 115, 16, 150, 3);
    /** Thermal flash at detonation: only hurts what can see the fireball. */
    public static final DamageProfile THERMAL = DamageProfile.of(0, 60, 40, 24, 80, 8, 120, 2, 135, 0);
    /** Inside the fireball while it burns, every 5 ticks (by distance to its center). */
    public static final DamageProfile FIREBALL = DamageProfile.of(0, 400, 20, 200, 28, 60, 34, 0);

    private static final CraterCarver.Palette PALETTE = (random, edge) -> {
        if (edge < 0.45 && random.nextInt(14) == 0) return Blocks.LIME_STAINED_GLASS.defaultBlockState(); // trinitite
        if (edge < 0.35 && random.nextInt(5) == 0) return Blocks.MAGMA_BLOCK.defaultBlockState();
        BlockState[] options = edge < 0.7
                ? new BlockState[]{Blocks.BLACKSTONE.defaultBlockState(), Blocks.BASALT.defaultBlockState(), Blocks.TUFF.defaultBlockState()}
                : new BlockState[]{Blocks.TUFF.defaultBlockState(), Blocks.COARSE_DIRT.defaultBlockState(), Blocks.GRAVEL.defaultBlockState()};
        return options[random.nextInt(options.length)];
    };

    private final Set<UUID> hitByShockwave = new HashSet<>();
    private CraterCarver crater;
    private RandomSource random;

    // ---------------------------------------------------------------- shared timeline (server and client)

    /** Radius of the blast wave, {@code age} ticks into the strike. */
    public static float shockRadius(float age) {
        return Math.max(0.0f, (age - DETONATION) * SHOCKWAVE_SPEED);
    }

    /** Fireball radius: forms in under a second, then swells slowly as it rises. */
    public static float fireballRadius(float age) {
        float d = age - DETONATION;
        if (d < 0) return 0.0f;
        float grow = 1.0f - (1.0f - Math.min(1.0f, d / 16.0f)) * (1.0f - Math.min(1.0f, d / 16.0f)) * (1.0f - Math.min(1.0f, d / 16.0f));
        return FIREBALL_RADIUS * grow * (1.0f + d / 400.0f);
    }

    /** Fireball center above ground zero: it starts rising after a second and becomes the heart of the cap. */
    public static float fireballHeight(float age) {
        float d = age - DETONATION;
        return FIREBALL_ALTITUDE + Math.max(0, d - 25) * 0.32f;
    }

    @Override
    public int duration() {
        return DURATION;
    }

    @Override
    public void tick(StrikeEntity strike, ServerLevel level, int age) {
        Vec3 center = strike.position();
        if (random == null) random = RandomSource.create(strike.getSeed());

        if (age < DETONATION) {
            countdown(level, center, age);
            return;
        }
        if (age == DETONATION) detonate(strike, level, center);

        int since = age - DETONATION;
        if (crater != null && !crater.isDone()) crater.step(2500);
        if (since <= 25) StrikeTools.scorchColumns(level, center, CRATER_RADIUS * 0.9, 75, 320, random, 14);
        if (since >= 12 && since <= 32 && since % 2 == 0) StrikeTools.igniteGround(level, center, CRATER_RADIUS + 2, 95, 8, random);

        // the fireball itself burns everything inside it for three seconds
        if (since > 0 && since <= 60 && since % 5 == 0) {
            Vec3 ball = center.add(0, fireballHeight(age), 0);
            StrikeTools.blast(level, ball, FIREBALL, level.damageSources().inFire(), 1.0f, 0.0, 15);
        }

        // Damage rides on the blast wave: everything is hit once, when the front reaches it.
        // Inside the fireball cover does not matter; farther out, hard cover takes the edge off.
        float front = shockRadius(age);
        if (front <= MAX_RADIUS + SHOCKWAVE_SPEED) {
            DamageSource source = level.damageSources().explosion(strike, strike.getOwner());
            for (Entity entity : StrikeTools.entitiesAround(level, center, Math.min(front, MAX_RADIUS))) {
                if (!hitByShockwave.add(entity.getUUID())) continue;
                double distance = StrikeTools.distanceTo(entity, center);
                float coverMin = distance < 30 ? 1.0f : 0.45f;
                StrikeTools.hit(level, entity, center, distance, BLAST, source, coverMin, 3.6, distance < 90 ? 15 : 0, true);
            }
        }

        if (since == 60 || since == 200) StrikeTools.sound(level, center, ModSounds.NUKE_AFTERMATH, 30.0f, since == 60 ? 1.0f : 0.85f);
        if (since == 35 || since == 110) StrikeTools.sound(level, center, ModSounds.DEBRIS_RAIN, 16.0f, 0.8f);
    }

    private void countdown(ServerLevel level, Vec3 center, int age) {
        if (age < 140 && age % 20 == 0) StrikeTools.sound(level, center, ModSounds.NUKE_ALARM, 10.0f, 1.0f);
        if (age >= 140 && age < DETONATION - 70 && age % 10 == 0) StrikeTools.sound(level, center, ModSounds.NUKE_ALARM, 10.0f, 1.15f);
        if (age < 170 && age % 30 == 0) StrikeTools.sound(level, center, SoundEvents.WARDEN_HEARTBEAT, 5.0f, 0.6f);
        if (age == DETONATION - 70) StrikeTools.sound(level, center, ModSounds.NUKE_CHARGE, 18.0f, 1.0f);
    }

    private void detonate(StrikeEntity strike, ServerLevel level, Vec3 center) {
        StrikeTools.sound(level, center, ModSounds.NUKE_DETONATION, 64.0f, 1.0f);

        // Thermal flash: burns everything that has line of sight to the fireball
        StrikeTools.blast(level, center.add(0, FIREBALL_ALTITUDE, 0), THERMAL, level.damageSources().inFire(), 0.0f, 0.0, 10);

        // Ground zero: vaporized
        DamageSource source = level.damageSources().explosion(strike, strike.getOwner());
        for (Entity entity : StrikeTools.entitiesAround(level, center, VAPORIZE_RADIUS)) {
            if (entity instanceof LivingEntity living) {
                living.invulnerableTime = 0;
                living.hurtServer(level, source, BLAST.peak());
            } else if (!(entity instanceof Player)) {
                entity.discard();
            }
            hitByShockwave.add(entity.getUUID());
        }

        crater = new CraterCarver(level, BlockPos.containing(center), CRATER_RADIUS, 17, 24, PALETTE,
                strike.getSeed(), 170, 1.7);
    }

    @Override
    public void finish(StrikeEntity strike, ServerLevel level) {
        if (crater != null) crater.finishNow();
    }
}
