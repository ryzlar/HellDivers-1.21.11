package net.ryzlar.strike;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.ryzlar.sound.ModSounds;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Volcanic Eruption (extreme). The ground itself opens and the furnace below erupts.
 *
 * <p>Timeline (ticks):
 * <ol>
 *     <li>{@code 0..CRACKS_START} warning: the ground rumbles, harder and harder.</li>
 *     <li>{@code CRACKS_START..CRACKS_END} cracks split the ground outward from the center (real holes with magma at
 *     the bottom); side vents along them start spitting rock.</li>
 *     <li>{@code OPENING..ERUPTION} the center caves in into a chasm while chunks of ground lift off on the heat;
 *     lava floods the bottom and lights its walls.</li>
 *     <li>{@code ERUPTION} the plug blasts out; then a fountain of lava in pulses ({@link #power}): lava bombs,
 *     an ash column, and a pyroclastic surge rolling out to {@link #MAX_RADIUS}.</li>
 *     <li>{@code ERUPTION_END..} it wanes, steam rises, the lava cools to a crust.</li>
 * </ol>
 * Damage: the fountain itself (by distance to the column), the opening blast, radiant heat, the surge, the bombs.
 */
public class VolcanicEruptionStrike implements StrikeSequence {

    public static final int CRACKS_START = 20;
    public static final int CRACKS_END = 260;
    public static final int BUILDUP = 300;
    /** The ground starts opening into the chasm (and chunks of it lift off) before the eruption. */
    public static final int OPENING = 250;
    public static final int ERUPTION = 360;
    public static final int ERUPTION_END = 720;
    public static final int DURATION = 1000;
    public static final float SURGE_START = ERUPTION + 25;
    public static final float SURGE_SPEED = 1.3f;
    public static final float MAX_RADIUS = 100.0f;
    public static final int CALDERA_RADIUS = 13;
    public static final int PIT_DEPTH = 16;
    public static final float CRACK_MAX_LENGTH = 46.0f;

    /** A point on a crack: offset from the center, when it opens (0..1 of the crack phase) and its width. */
    public record CrackPoint(float x, float z, float appear, float width) {
    }

    /** A small side eruption on one of the cracks. */
    public record Vent(int tick, float x, float z) {
    }

    /** Side vents: deadly right on top, a scorching blast around. */
    public static final DamageProfile VENT_DAMAGE = DamageProfile.of(0, 45, 2, 26, 4, 9, 7, 0);
    /** The main eruption blast: the vent itself is lethal, the caldera rim is devastating. */
    public static final DamageProfile ERUPTION_BLAST = DamageProfile.of(0, 1200, 5, 450, 10, 100, 18, 40, 30, 10, 40, 0);
    /** Radiant heat around the open chasm, every 10 ticks during the eruption. */
    public static final DamageProfile HEAT = DamageProfile.of(0, 40, CALDERA_RADIUS, 12, CALDERA_RADIUS + 8, 3, 32, 0);
    /** Standing in or next to the lava fountain, every 5 ticks while it is strong (by distance to the column). */
    public static final DamageProfile LAVA_COLUMN = DamageProfile.of(0, 30, 2, 18, 4, 6, 6, 0);
    /** Pyroclastic surge: a burning ash wave rolling out to MAX_RADIUS, hits once. */
    public static final DamageProfile SURGE = DamageProfile.of(0, 70, 20, 45, 45, 20, 75, 7, 100, 1);

    private static final CraterCarver.Palette PALETTE = (random, edge) -> {
        if (edge < 0.5 && random.nextInt(3) == 0) return Blocks.MAGMA_BLOCK.defaultBlockState();
        BlockState[] options = {Blocks.BASALT.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(),
                Blocks.SMOOTH_BASALT.defaultBlockState(), Blocks.MAGMA_BLOCK.defaultBlockState()};
        return options[random.nextInt(options.length)];
    };

    private static final BlockState[] BOMBS = {
            Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.MAGMA_BLOCK.defaultBlockState(),
            Blocks.BLACKSTONE.defaultBlockState(), Blocks.BASALT.defaultBlockState()};

    /** Crack layout, identical on server and client. */
    public static List<List<CrackPoint>> cracks(int seed) {
        Random random = new Random(seed);
        List<List<CrackPoint>> cracks = new ArrayList<>();
        int main = 7;
        for (int i = 0; i < main; i++) {
            double heading = (i + random.nextDouble() * 0.6) / main * Math.PI * 2;
            float length = 24 + random.nextFloat() * (CRACK_MAX_LENGTH - 24);
            growCrack(cracks, random, 2.5f, heading, length, 0.0f, 1.4f, 0);
        }
        return cracks;
    }

    private static void growCrack(List<List<CrackPoint>> out, Random random, float startDist, double heading,
                                  float length, float startAppear, float width, int depth) {
        List<CrackPoint> crack = new ArrayList<>();
        double x = Math.cos(heading) * startDist;
        double z = Math.sin(heading) * startDist;
        float travelled = 0;
        while (travelled < length) {
            float appear = startAppear + travelled / CRACK_MAX_LENGTH;
            float taper = 1.0f - 0.7f * travelled / length;
            crack.add(new CrackPoint((float) x, (float) z, Math.min(1.0f, appear), width * taper));
            heading += (random.nextDouble() - 0.5) * 0.7;
            x += Math.cos(heading) * 1.3;
            z += Math.sin(heading) * 1.3;
            travelled += 1.3f;
            if (depth < 2 && random.nextFloat() < 0.07f) {
                List<List<CrackPoint>> branch = new ArrayList<>();
                double side = heading + (random.nextBoolean() ? 1 : -1) * (0.5 + random.nextDouble() * 0.6);
                growBranch(branch, random, x, z, side, 6 + random.nextFloat() * 10, appear, width * taper * 0.6f);
                out.addAll(branch);
            }
        }
        if (!crack.isEmpty()) out.add(crack);
    }

    private static void growBranch(List<List<CrackPoint>> out, Random random, double x, double z, double heading,
                                   float length, float startAppear, float width) {
        List<CrackPoint> crack = new ArrayList<>();
        float travelled = 0;
        while (travelled < length) {
            crack.add(new CrackPoint((float) x, (float) z, Math.min(1.0f, startAppear + travelled / CRACK_MAX_LENGTH),
                    width * (1.0f - 0.8f * travelled / length)));
            heading += (random.nextDouble() - 0.5) * 0.8;
            x += Math.cos(heading) * 1.2;
            z += Math.sin(heading) * 1.2;
            travelled += 1.2f;
        }
        out.add(crack);
    }

    /** Side vents sit on the cracks and pop between the crack phase and the main eruption. */
    public static List<Vent> vents(int seed) {
        Random random = new Random(seed * 31L + 7);
        List<List<CrackPoint>> cracks = cracks(seed);
        List<Vent> vents = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            List<CrackPoint> crack = cracks.get(random.nextInt(cracks.size()));
            CrackPoint point = crack.get(random.nextInt(crack.size()));
            int tick = 140 + random.nextInt(190);
            vents.add(new Vent(tick, point.x(), point.z()));
        }
        vents.sort((a, b) -> Integer.compare(a.tick(), b.tick()));
        return vents;
    }

    /**
     * Eruption intensity 0..1: the plug blast, then pulses roughly every three seconds on a slowly waning base.
     * Shared with the client (column height, bombs, sound and camera follow the same pulses).
     */
    public static float power(float age) {
        if (age < ERUPTION) return 0.0f;
        float d = age - ERUPTION;
        float rise = Math.min(1.0f, d / 12.0f);
        float wane = (float) Math.pow(Mth.clamp(1.0f - d / (ERUPTION_END - ERUPTION), 0.0f, 1.0f), 0.6);
        float pulse = 0.72f + 0.28f * (float) Math.pow(Math.max(0.0f, Mth.sin(d * 0.105f)), 3);
        return rise * wane * pulse;
    }

    /** Height of the lava fountain above the ground. */
    public static float fountainHeight(float age) {
        return 14 + 52 * power(age);
    }

    /** Time (age) at which a crack point opens. */
    public static float appearTick(CrackPoint point) {
        return CRACKS_START + point.appear() * (CRACKS_END - CRACKS_START);
    }

    private final Set<UUID> hitBySurge = new HashSet<>();
    private List<List<CrackPoint>> cracks;
    private int[] crackProgress;
    private List<Vent> vents;
    private CraterCarver caldera;
    private final List<net.minecraft.world.entity.item.FallingBlockEntity> risingRock = new ArrayList<>();
    private boolean lavaPlaced;
    private RandomSource random;

    @Override
    public int duration() {
        return DURATION;
    }

    @Override
    public void tick(StrikeEntity strike, ServerLevel level, int age) {
        Vec3 center = strike.position();
        if (cracks == null) {
            cracks = cracks(strike.getSeed());
            crackProgress = new int[cracks.size()];
            vents = vents(strike.getSeed());
            random = RandomSource.create(strike.getSeed());
        }

        if (age < ERUPTION && age % 70 == 0) {
            StrikeTools.sound(level, center, ModSounds.VOLCANO_RUMBLE, 4.0f + 12.0f * age / ERUPTION, 0.9f + 0.2f * age / ERUPTION);
        }
        if (age > CRACKS_START && age < CRACKS_END && age % 32 == 8) {
            StrikeTools.sound(level, center, ModSounds.VOLCANO_CRACK, 8.0f, 0.8f + random.nextFloat() * 0.4f);
        }
        if (age % 40 == 0 && age > CRACKS_START && age < ERUPTION_END) {
            StrikeTools.sound(level, center, SoundEvents.LAVA_AMBIENT, 8.0f, 0.5f);
        }

        openCracks(level, center, age);

        for (Vent vent : vents) {
            if (vent.tick() == age) ventEruption(strike, level, center, vent);
        }

        if (age == BUILDUP) StrikeTools.sound(level, center, ModSounds.STEAM_HISS, 12.0f, 0.7f);
        if (age > BUILDUP && age < ERUPTION && age % 10 == 0) {
            StrikeTools.sound(level, center, SoundEvents.LAVA_POP, 6.0f, 0.4f);
        }

        if (age == OPENING) openChasm(strike, level, center);
        if (age == ERUPTION) erupt(strike, level, center);
        if (age > ERUPTION && age < ERUPTION_END) eruptionTick(strike, level, center, age);

        if (caldera != null && !caldera.isDone()) {
            // Opens steadily so it is (nearly) done when the eruption hits
            caldera.step(Math.max(200, caldera.size() / (ERUPTION - OPENING) + 1));
            if (age > OPENING) levitateRocks(age);
        } else if (caldera != null && !lavaPlaced) {
            placeLavaPool(level, BlockPos.containing(center));
            lavaPlaced = true;
        }

        if (age == ERUPTION_END + 120) coolDown(level, BlockPos.containing(center));
    }

    private void openCracks(ServerLevel level, Vec3 center, int age) {
        for (int i = 0; i < cracks.size(); i++) {
            List<CrackPoint> crack = cracks.get(i);
            while (crackProgress[i] < crack.size() && appearTick(crack.get(crackProgress[i])) <= age) {
                CrackPoint point = crack.get(crackProgress[i]);
                // Also cut between this point and the previous one, so the crack is continuous
                CrackPoint previous = crackProgress[i] > 0 ? crack.get(crackProgress[i] - 1) : point;
                crackProgress[i]++;
                for (int step = 0; step < 3; step++) {
                    float t = previous == point ? 1.0f : (step + 1) / 3.0f;
                    cutCrack(level, center, Mth.lerp(t, previous.x(), point.x()), Mth.lerp(t, previous.z(), point.z()), point.width());
                    if (previous == point) break;
                }
            }
        }
    }

    /** Cuts one column of a crack: a real hole 0-2 blocks deep (by crack width) with glowing magma at the bottom. */
    private void cutCrack(ServerLevel level, Vec3 center, float x, float z, float width) {
        BlockPos top = StrikeTools.surface(level, Mth.floor(center.x + x), Mth.floor(center.z + z));
        if (Math.abs(top.getY() - center.y) > 12) return; // do not crack cliffs far above/below
        BlockState state = level.getBlockState(top);
        if (state.is(Blocks.MAGMA_BLOCK)) return; // already cracked here
        if (!StrikeTools.canDestroy(level, top, state) || !state.isSolidRender()) return;
        int depth = width > 1.05f ? 2 : width > 0.6f ? 1 : 0;
        for (int d = 0; d < depth; d++) {
            BlockPos pos = top.below(d);
            if (StrikeTools.canDestroy(level, pos, level.getBlockState(pos))) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        BlockPos floor = top.below(depth);
        if (StrikeTools.canDestroy(level, floor, level.getBlockState(floor))) {
            level.setBlock(floor, Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private void ventEruption(StrikeEntity strike, ServerLevel level, Vec3 center, Vent vent) {
        BlockPos ground = StrikeTools.surface(level, Mth.floor(center.x + vent.x()), Mth.floor(center.z + vent.z()));
        Vec3 pos = Vec3.atBottomCenterOf(ground.above());
        new CraterCarver(level, ground, 2.3f, 2.0f, 3.0f, PALETTE, strike.getSeed() + vent.tick(), 4, 0.9).finishNow();
        StrikeTools.blast(level, pos, VENT_DAMAGE, level.damageSources().inFire(), 0.5f, 0.9, 6);
        for (int i = 0; i < 3; i++) {
            StrikeTools.throwBlock(level, ground.above(2), BOMBS[random.nextInt(BOMBS.length)],
                    new Vec3(random.nextGaussian() * 0.25, 0.9 + random.nextDouble() * 0.5, random.nextGaussian() * 0.25));
        }
        StrikeTools.sound(level, pos, ModSounds.VOLCANO_VENT, 8.0f, 0.9f + random.nextFloat() * 0.2f);
        StrikeTools.sound(level, pos, SoundEvents.LAVA_POP, 4.0f, 0.5f);
    }

    private void erupt(StrikeEntity strike, ServerLevel level, Vec3 center) {
        StrikeTools.sound(level, center, ModSounds.VOLCANO_ERUPTION, 40.0f, 1.0f);
        StrikeTools.sound(level, center, ModSounds.VOLCANO_BURST, 30.0f, 0.8f);
        StrikeTools.blast(level, center, ERUPTION_BLAST, level.damageSources().explosion(strike, strike.getOwner()), 0.6f, 2.8, 12, true);
        if (caldera != null && !caldera.isDone()) caldera.finishNow();

        // The lifted terrain gets thrown out
        for (var rock : risingRock) {
            if (!rock.isAlive()) continue;
            rock.setNoGravity(false);
            Vec3 out = rock.position().subtract(center);
            Vec3 horizontal = new Vec3(out.x, 0, out.z);
            horizontal = horizontal.lengthSqr() < 1.0e-3 ? new Vec3(1, 0, 0) : horizontal.normalize();
            rock.setDeltaMovement(horizontal.scale(0.5 + random.nextDouble() * 0.9).add(0, 1.0 + random.nextDouble() * 0.9, 0));
            rock.hurtMarked = true;
        }
        risingRock.clear();
    }

    /** The ground caves in from the center out; chunks of the surface tear loose and float up on the heat. */
    private void openChasm(StrikeEntity strike, ServerLevel level, Vec3 center) {
        StrikeTools.sound(level, center, ModSounds.VOLCANO_CRACK, 18.0f, 0.6f);
        StrikeTools.sound(level, center, ModSounds.VOLCANO_BURST, 14.0f, 0.6f);
        caldera = new CraterCarver(level, BlockPos.containing(center), CALDERA_RADIUS, PIT_DEPTH, 6, PALETTE,
                strike.getSeed(), 70, 0.0)
                .withDebris((offset, rnd) -> new Vec3(0, 0.05 + rnd.nextDouble() * 0.08, 0), rock -> {
                    rock.setNoGravity(true);
                    risingRock.add(rock);
                });
    }

    /** Floating chunks drift and wobble while the chasm opens beneath them. */
    private void levitateRocks(int age) {
        for (var rock : risingRock) {
            if (!rock.isAlive()) continue;
            Vec3 v = rock.getDeltaMovement();
            double wobble = Math.sin(age * 0.3 + rock.getId()) * 0.01;
            rock.setDeltaMovement(v.x * 0.9 + wobble, Math.min(0.12, v.y * 0.98 + 0.002), v.z * 0.9 - wobble);
            rock.time = 1; // keep it from expiring while it floats
        }
    }

    private void eruptionTick(StrikeEntity strike, ServerLevel level, Vec3 center, int age) {
        int sinceEruption = age - ERUPTION;
        float intensity = 1.0f - sinceEruption / (float) (ERUPTION_END - ERUPTION);
        float power = power(age);

        // Lava bombs: lots at first and on every pulse, tapering off
        int bombs = power > 0.85f ? 3 : sinceEruption < 160 ? 2 : (age % 2 == 0 ? 1 : 0);
        for (int i = 0; i < bombs; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 0.5 + random.nextDouble() * 1.3 * (0.4 + 0.6 * intensity);
            Vec3 velocity = new Vec3(Math.cos(angle) * speed, 1.3 + random.nextDouble() * 1.1, Math.sin(angle) * speed);
            BlockPos launch = BlockPos.containing(center).above(3 + random.nextInt(3));
            StrikeTools.throwBlock(level, launch, BOMBS[random.nextInt(BOMBS.length)], velocity);
        }

        if (age % 10 == 0) {
            StrikeTools.blast(level, center, HEAT, level.damageSources().lava(), 0.3f, 0.3 * intensity, 8);
        }
        // The fountain itself: deadly to stand in, measured to the column from the pit floor to its top
        if (age % 5 == 0 && power > 0.2f) {
            StrikeTools.columnBlast(level, center.add(0, -PIT_DEPTH + 2, 0), fountainHeight(age) + PIT_DEPTH - 2, LAVA_COLUMN,
                    level.damageSources().lava(), 1.0f, 0.5, 10, false);
        }
        // every pulse of the eruption roars
        if (sinceEruption > 20 && sinceEruption % 60 == 12) {
            StrikeTools.sound(level, center, ModSounds.VOLCANO_BURST, 14.0f + 16.0f * intensity, 0.85f + random.nextFloat() * 0.25f);
        }
        if (age % 14 == 0) StrikeTools.sound(level, center, SoundEvents.LAVA_POP, 8.0f, 0.4f);
        if (sinceEruption == 120 || sinceEruption == 240) StrikeTools.sound(level, center, ModSounds.VOLCANO_ERUPTION, 22.0f * intensity + 8.0f, 0.9f);

        // Pyroclastic surge: a burning ground-hugging wave, hits everything once
        float front = (age - SURGE_START) * SURGE_SPEED;
        if (front > 0 && front <= MAX_RADIUS + SURGE_SPEED) {
            for (Entity entity : StrikeTools.entitiesAround(level, center, Math.min(front, MAX_RADIUS))) {
                if (!hitBySurge.add(entity.getUUID())) continue;
                StrikeTools.hit(level, entity, center, SURGE, level.damageSources().inFire(), 0.35f, 1.4, 10);
            }
            if (age % 4 == 0) {
                StrikeTools.scorchColumns(level, center, Math.max(CALDERA_RADIUS, front - 6), Math.min(front, 60), 60, random, 10);
            }
            // the surge sets the ground it rolls over alight here and there
            if (age % 10 == 0 && front < 60) {
                StrikeTools.igniteGround(level, center, Math.max(CALDERA_RADIUS + 2, front - 5), Math.max(CALDERA_RADIUS + 3, front), 5, random);
            }
        }
    }

    private void placeLavaPool(ServerLevel level, BlockPos center) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if (dx * dx + dz * dz > 36) continue;
                pos.set(center.getX() + dx, center.getY(), center.getZ() + dz);
                int steps = 0;
                while (steps++ < PIT_DEPTH + 6 && level.getBlockState(pos).isAir()) pos.move(0, -1, 0);
                pos.move(0, 1, 0);
                if (level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    /** The eruption settles: lava in the caldera turns into a crust. */
    private void coolDown(ServerLevel level, BlockPos center) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int r = CALDERA_RADIUS + 4;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -PIT_DEPTH - 4; dy <= 4; dy++) {
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (!level.isLoaded(pos) || !level.getFluidState(pos).is(Fluids.LAVA) && !level.getFluidState(pos).is(Fluids.FLOWING_LAVA)) {
                        continue;
                    }
                    boolean source = level.getFluidState(pos).isSource();
                    BlockState crust = dx * dx + dz * dz < 9 ? Blocks.MAGMA_BLOCK.defaultBlockState()
                            : source ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.BASALT.defaultBlockState();
                    level.setBlock(pos, crust, Block.UPDATE_CLIENTS);
                }
            }
        }
        StrikeTools.sound(level, Vec3.atCenterOf(center), SoundEvents.LAVA_EXTINGUISH, 12.0f, 0.5f);
        StrikeTools.sound(level, Vec3.atCenterOf(center), ModSounds.STEAM_HISS, 16.0f, 0.8f);
    }

    @Override
    public void finish(StrikeEntity strike, ServerLevel level) {
        if (caldera != null) caldera.finishNow();
        // never leave lifted ground hanging in the air if the strike ends early
        for (var rock : risingRock) {
            if (rock.isAlive()) rock.setNoGravity(false);
        }
        risingRock.clear();
    }
}
