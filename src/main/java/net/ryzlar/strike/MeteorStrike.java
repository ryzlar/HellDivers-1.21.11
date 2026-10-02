package net.ryzlar.strike;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.ryzlar.sound.ModSounds;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Meteor Strike (high).
 *
 * <p>Timeline (ticks):
 * <ol>
 *     <li>{@code 0..FALL_START} warning: the target is designated, a distant rumble, the sky starts to burn where
 *     the meteor will enter.</li>
 *     <li>{@code FALL_START..IMPACT} entry: the meteor comes in at a shallow angle from far away, accelerating, its
 *     roar building. In the last second and a half the air pressure in front of it lifts dust off the ground.</li>
 *     <li>{@code IMPACT}: the blow itself (lethal at the point of impact, devastating close by), a crater is blasted
 *     out over a few ticks, ejecta fly. A separate shock front races outward and hits everything once as it passes
 *     ({@link #SHOCKWAVE}, with its own falloff).</li>
 *     <li>aftermath: the crater floor stays molten and burns whoever stands in it ({@link #HEAT}); fires on the
 *     scorched rim; smoke column; everything settles.</li>
 * </ol>
 */
public class MeteorStrike implements StrikeSequence {

    public static final int FALL_START = 60;
    public static final int IMPACT = 152;
    public static final int DURATION = 720;
    public static final float ENTRY_HEIGHT = 300.0f;
    public static final float ENTRY_DISTANCE = 210.0f;
    public static final float MAX_RADIUS = 60.0f;
    public static final float SHOCKWAVE_SPEED = 1.8f;
    public static final int CRATER_RADIUS = 13;
    public static final float METEOR_RADIUS = 3.0f;

    /** The impact itself: a direct hit is lethal, the inner ring devastating, falling off fast beyond the crater. */
    public static final DamageProfile IMPACT_DAMAGE = DamageProfile.of(0, 1500, 4, 700, 9, 180, 14, 60, 22, 12, 30, 0);
    /** The shock front: hits once as it passes, independent of the impact; reaches much further. */
    public static final DamageProfile SHOCKWAVE = DamageProfile.of(0, 45, 12, 35, 25, 16, 40, 6, 60, 1);
    /** Standing in the molten crater, every 10 ticks for 10 seconds after the impact. */
    public static final DamageProfile HEAT = DamageProfile.of(0, 6, 5, 4, 10, 1.5f, 14, 0);

    private static final CraterCarver.Palette PALETTE = (random, edge) -> {
        if (edge < 0.4 && random.nextInt(3) == 0) return Blocks.MAGMA_BLOCK.defaultBlockState();
        BlockState[] options = edge < 0.75
                ? new BlockState[]{Blocks.BLACKSTONE.defaultBlockState(), Blocks.BASALT.defaultBlockState(), Blocks.COBBLED_DEEPSLATE.defaultBlockState()}
                : new BlockState[]{Blocks.COARSE_DIRT.defaultBlockState(), Blocks.COBBLED_DEEPSLATE.defaultBlockState(), Blocks.GRAVEL.defaultBlockState()};
        return options[random.nextInt(options.length)];
    };

    private final Set<UUID> hitByShockwave = new HashSet<>();
    private CraterCarver crater;
    private boolean corePlaced;
    private RandomSource random;

    // ---------------------------------------------------------------- shared timeline (server and client)

    /** Where the meteor enters, relative to the target. Same on server and client (seeded). */
    public static Vec3 entryOffset(int seed) {
        Random random = new Random(seed);
        double angle = random.nextDouble() * Math.PI * 2;
        return new Vec3(Math.cos(angle) * ENTRY_DISTANCE, ENTRY_HEIGHT, Math.sin(angle) * ENTRY_DISTANCE);
    }

    /** 0 at the start of the fall, 1 at impact; accelerates like something falling out of the sky. */
    public static float fallProgress(float age) {
        float t = Mth.clamp((age - FALL_START) / (IMPACT - FALL_START), 0.0f, 1.0f);
        return (float) Math.pow(t, 1.6);
    }

    public static Vec3 meteorPosition(Vec3 target, int seed, float age) {
        return target.add(0, METEOR_RADIUS * 0.6, 0).add(entryOffset(seed).scale(1.0 - fallProgress(age)));
    }

    /** Radius of the shock front, {@code age} ticks into the strike. */
    public static float shockRadius(float age) {
        return Math.max(0.0f, (age - IMPACT) * SHOCKWAVE_SPEED);
    }

    @Override
    public int duration() {
        return DURATION;
    }

    @Override
    public void tick(StrikeEntity strike, ServerLevel level, int age) {
        Vec3 target = strike.position();
        if (random == null) random = RandomSource.create(strike.getSeed());

        if (age == 0) StrikeTools.sound(level, target, ModSounds.METEOR_WARNING, 10.0f, 1.0f);
        if (age == FALL_START - 20) StrikeTools.sound(level, target, ModSounds.METEOR_WARNING, 14.0f, 0.85f);
        if (age == IMPACT - 92) StrikeTools.sound(level, target, ModSounds.METEOR_INCOMING, 26.0f, 1.0f);

        if (age == IMPACT) impact(strike, level, target);
        if (age > IMPACT) aftermath(strike, level, target, age);

        if (crater != null && !crater.isDone()) {
            crater.step(1600);
        } else if (crater != null && !corePlaced) {
            placeCore(level, BlockPos.containing(target));
            corePlaced = true;
        }
    }

    private void impact(StrikeEntity strike, ServerLevel level, Vec3 target) {
        StrikeTools.sound(level, target, ModSounds.METEOR_IMPACT, 40.0f, 1.0f);

        DamageSource source = level.damageSources().explosion(strike, strike.getOwner());
        for (Entity entity : StrikeTools.entitiesAround(level, target, IMPACT_DAMAGE.maxRadius() + 2)) {
            double distance = StrikeTools.distanceTo(entity, target);
            // inside the fireball cover does not matter; farther out, a wall takes the edge off
            float coverMin = distance < 10 ? 1.0f : 0.4f;
            StrikeTools.hit(level, entity, target, distance, IMPACT_DAMAGE, source, coverMin, 2.8, 8, true);
        }
        crater = new CraterCarver(level, BlockPos.containing(target), CRATER_RADIUS, 9, 10, PALETTE,
                strike.getSeed(), 90, 1.25);
    }

    private void aftermath(StrikeEntity strike, ServerLevel level, Vec3 target, int age) {
        int since = age - IMPACT;
        // the shock front: once per entity, as it passes
        StrikeTools.shockFront(level, target, shockRadius(age - 1), shockRadius(age), SHOCKWAVE,
                level.damageSources().explosion(strike, strike.getOwner()), 0.4f, 1.4, hitByShockwave);
        if (since <= 20) StrikeTools.scorchColumns(level, target, CRATER_RADIUS * 0.9, 32, 110, random, 12);
        if (since == 10) StrikeTools.sound(level, target, ModSounds.DEBRIS_RAIN, 12.0f, 1.0f);
        if (since == 22) StrikeTools.igniteGround(level, target, CRATER_RADIUS + 1, 30, 50, random);
        if (since == 30 || since == 75) StrikeTools.sound(level, target, ModSounds.VOLCANO_RUMBLE, 10.0f, 1.2f);
        if (since > 0 && since <= 200 && since % 10 == 0) {
            Vec3 floor = target.add(0, -4, 0);
            StrikeTools.blast(level, floor, HEAT, level.damageSources().inFire(), 1.0f, 0.0, 4);
        }
    }

    /** The meteorite itself, half buried at the bottom of the crater, still glowing. */
    private void placeCore(ServerLevel level, BlockPos center) {
        BlockPos floor = center;
        for (int i = 0; i < 16 && level.getBlockState(floor).isAir(); i++) floor = floor.below();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx * dx + dy * dy * 1.6 + dz * dz > 5.5 + random.nextFloat() * 1.5) continue;
                    pos.set(floor.getX() + dx, floor.getY() + dy, floor.getZ() + dz);
                    if (!level.isLoaded(pos) || level.getBlockState(pos).getDestroySpeed(level, pos) < 0) continue;
                    int roll = random.nextInt(10);
                    BlockState state = roll < 4 ? Blocks.MAGMA_BLOCK.defaultBlockState()
                            : roll < 7 ? Blocks.OBSIDIAN.defaultBlockState()
                            : roll < 9 ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                            : Blocks.ANCIENT_DEBRIS.defaultBlockState();
                    level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    @Override
    public void finish(StrikeEntity strike, ServerLevel level) {
        if (crater != null) crater.finishNow();
    }
}
