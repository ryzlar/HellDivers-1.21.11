package net.ryzlar.strike;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.ryzlar.sound.ModSounds;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Orbital Lightning (moderate). A weapon platform in orbit, not weather.
 *
 * <p>Timeline (ticks):
 * <ol>
 *     <li>{@code LOCK}: the platform acquires the target; a targeting laser comes down from orbit and locks on.</li>
 *     <li>{@code CHARGE_START..FIRST_BOLT}: the platform charges; an ionized vortex forms in the atmosphere.</li>
 *     <li>Controlled strikes ({@link #schedule}): a few probing bolts, an escalating barrage, two rings of
 *     simultaneous strikes closing in around the target, one heavy central bolt. Every bolt is telegraphed on the
 *     ground half a second before it lands.</li>
 *     <li>{@code FINAL_CHARGE..FINAL}: the column charges; {@code FINAL}: the full discharge - a column of energy, an
 *     electrical explosion and a shock front.</li>
 *     <li>aftermath: the ground stays charged for a few seconds.</li>
 * </ol>
 * Damage is measured to the actual channel (a vertical segment), not to a point on the ground.
 */
public class OrbitalLightningStrike implements StrikeSequence {

    public static final int LOCK = 10;
    public static final int CHARGE_START = 20;
    public static final int FIRST_BOLT = 100;
    public static final int FINAL_CHARGE = 212;
    public static final int FINAL = 240;
    public static final int DURATION = 370;
    public static final int TELEGRAPH = 10;
    public static final float AREA_RADIUS = 20.0f;
    public static final float FINAL_RADIUS = 25.0f;
    public static final float VORTEX_HEIGHT = 70.0f;
    public static final float ORBIT_HEIGHT = 280.0f;
    public static final float FINAL_SHOCK_SPEED = 1.4f;

    /** One bolt: when, where (relative to the target) and how strong (0..1). */
    public record Bolt(int tick, float dx, float dz, float power) {
    }

    /** The final discharge, by distance to the column: the column itself is lethal, it falls off quickly. */
    public static final DamageProfile FINAL_DAMAGE = DamageProfile.of(0, 900, 3, 300, 7, 70, 12, 35, 18, 14, 25, 2);
    /** The shock front of the final discharge: hits once as it passes. */
    public static final DamageProfile FINAL_SHOCK = DamageProfile.of(0, 20, 10, 14, 20, 6, 32, 1);
    /** Charged ground after the final strike. */
    public static final DamageProfile RESIDUAL = DamageProfile.of(0, 5, 4, 3, 8, 0);

    /** A single bolt by distance to its channel: brutal on a direct hit, a jolt nearby; scales with power. */
    public static DamageProfile boltDamage(float power) {
        return DamageProfile.of(0, 22 + 38 * power, 1.6f, 14 + 22 * power, 3.5f, 5 + 7 * power, 6 + 2 * power, 0);
    }

    private static final CraterCarver.Palette SCORCH = (random, edge) -> {
        int roll = random.nextInt(6);
        return roll == 0 ? Blocks.TINTED_GLASS.defaultBlockState() // fulgurite
                : roll < 3 ? Blocks.BLACKSTONE.defaultBlockState()
                : Blocks.COARSE_DIRT.defaultBlockState();
    };

    private static final CraterCarver.Palette FINAL_SCORCH = (random, edge) -> {
        int roll = random.nextInt(8);
        BlockState state = roll < 2 ? Blocks.TINTED_GLASS.defaultBlockState()
                : roll < 3 ? Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState()
                : roll < 6 ? Blocks.BLACKSTONE.defaultBlockState()
                : Blocks.BASALT.defaultBlockState();
        return edge > 0.8 ? Blocks.COARSE_DIRT.defaultBlockState() : state;
    };

    /** The strike plan, identical on server and client: probe, escalate, encircle, hit the center. */
    public static List<Bolt> schedule(int seed) {
        Random random = new Random(seed);
        List<Bolt> bolts = new ArrayList<>();
        // probing shots, far out
        for (int tick : new int[]{FIRST_BOLT, FIRST_BOLT + 10, FIRST_BOLT + 19}) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 10 + random.nextDouble() * 8;
            bolts.add(new Bolt(tick, (float) (Math.cos(angle) * dist), (float) (Math.sin(angle) * dist), 0.15f));
        }
        // escalating barrage, getting faster, heavier and closer
        float tick = FIRST_BOLT + 30;
        float interval = 9.0f;
        while (tick < 180) {
            float p = (tick - FIRST_BOLT - 30) / (180 - FIRST_BOLT - 30.0f);
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = Mth.lerp(p, 16, 5) * (0.5 + 0.5 * random.nextDouble());
            bolts.add(new Bolt((int) tick, (float) (Math.cos(angle) * dist), (float) (Math.sin(angle) * dist), 0.3f + 0.3f * p));
            interval = Math.max(4.5f, interval * 0.88f);
            tick += interval;
        }
        // two rings of simultaneous strikes closing in
        double offset = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < 6; i++) {
            double a = offset + i * Math.PI / 3;
            bolts.add(new Bolt(186, (float) (Math.cos(a) * 11), (float) (Math.sin(a) * 11), 0.7f));
        }
        for (int i = 0; i < 6; i++) {
            double a = offset + Math.PI / 6 + i * Math.PI / 3;
            bolts.add(new Bolt(196, (float) (Math.cos(a) * 6), (float) (Math.sin(a) * 6), 0.8f));
        }
        // one heavy bolt on the target itself
        bolts.add(new Bolt(204, (float) (random.nextGaussian() * 0.8), (float) (random.nextGaussian() * 0.8), 1.0f));
        return bolts;
    }

    public static float finalShockRadius(float age) {
        return Math.max(0.0f, (age - FINAL) * FINAL_SHOCK_SPEED);
    }

    private List<Bolt> bolts;
    private final Set<UUID> hitByShock = new HashSet<>();

    @Override
    public int duration() {
        return DURATION;
    }

    @Override
    public void tick(StrikeEntity strike, ServerLevel level, int age) {
        Vec3 target = strike.position();
        if (bolts == null) bolts = schedule(strike.getSeed());

        if (age == CHARGE_START) StrikeTools.sound(level, target, ModSounds.LIGHTNING_CHARGE, 12.0f, 1.0f);
        if (age == CHARGE_START + 90) StrikeTools.sound(level, target, ModSounds.LIGHTNING_CHARGE, 10.0f, 1.25f);

        boolean zapped = false;
        for (Bolt bolt : bolts) {
            if (bolt.tick() == age) {
                strikeBolt(strike, level, target, bolt, !zapped);
                zapped = true; // a ring of simultaneous bolts sounds as one big strike
            }
        }

        if (age == FINAL - 34) StrikeTools.sound(level, target, ModSounds.LIGHTNING_FINAL_CHARGE, 14.0f, 1.0f);
        if (age == FINAL) finalStrike(strike, level, target);
        if (age > FINAL) {
            StrikeTools.shockFront(level, target, finalShockRadius(age - 1), finalShockRadius(age), FINAL_SHOCK,
                    level.damageSources().lightningBolt(), 0.6f, 1.2, hitByShock);
        }
        if (age == FINAL + 20 || age == FINAL + 70) StrikeTools.sound(level, target, ModSounds.LIGHTNING_CRACKLE, 6.0f, 1.0f);
        // Charged ground keeps zapping for a few seconds
        if (age > FINAL && age < FINAL + 70 && age % 6 == 0) {
            StrikeTools.blast(level, target, RESIDUAL, level.damageSources().lightningBolt(), 1.0f, 0.0, 0);
        }
    }

    private void strikeBolt(StrikeEntity strike, ServerLevel level, Vec3 target, Bolt bolt, boolean withSound) {
        BlockPos ground = StrikeTools.surface(level, Mth.floor(target.x + bolt.dx()), Mth.floor(target.z + bolt.dz()));
        Vec3 hit = Vec3.atBottomCenterOf(ground.above());
        DamageSource source = level.damageSources().lightningBolt();
        // Electricity arcs around cover, so cover only helps a little
        StrikeTools.columnBlast(level, hit, 60, boltDamage(bolt.power()), source, 0.7f, 0.8, 2, true);
        new CraterCarver(level, ground, 1.4f + bolt.power() * 1.4f, 1.6f, 2.0f, SCORCH, strike.getSeed() + bolt.tick(), 0, 0)
                .finishNow();
        if (withSound) {
            if (bolt.power() < 0.5f) {
                StrikeTools.sound(level, hit, ModSounds.LIGHTNING_ZAP, 6.0f + bolt.power() * 6.0f, 1.1f - bolt.power() * 0.3f);
            } else {
                StrikeTools.sound(level, hit, ModSounds.LIGHTNING_STRIKE, 10.0f + bolt.power() * 12.0f, 1.15f - bolt.power() * 0.25f);
            }
        }
    }

    private void finalStrike(StrikeEntity strike, ServerLevel level, Vec3 target) {
        StrikeTools.sound(level, target, ModSounds.LIGHTNING_DISCHARGE, 40.0f, 1.0f);
        StrikeTools.columnBlast(level, target, VORTEX_HEIGHT, FINAL_DAMAGE, level.damageSources().lightningBolt(), 0.6f, 2.4, 4, true);
        new CraterCarver(level, BlockPos.containing(target).below(), 6, 4, 6, FINAL_SCORCH, strike.getSeed(), 14, 0.8)
                .finishNow();
    }
}
