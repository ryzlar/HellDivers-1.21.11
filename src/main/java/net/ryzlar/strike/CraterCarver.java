package net.ryzlar.strike;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Digs an irregular bowl-shaped crater over several ticks (center first, outwards), lines it with a
 * scorched palette and throws some of the removed blocks out as debris. Never touches unbreakable blocks
 * (bedrock, barriers, ...) and stays inside its radius, so damage to the world is always bounded.
 */
public class CraterCarver {

    /** Picks the block used to line the crater; {@code edge} is 0 at the center and 1 at the rim. */
    @FunctionalInterface
    public interface Palette {
        BlockState pick(RandomSource random, double edge);
    }

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private final ServerLevel level;
    private final BlockPos center;
    private final Palette palette;
    private final RandomSource random;
    private final LongArrayList carve = new LongArrayList();
    private final LongArrayList lining = new LongArrayList();
    private final float radius;
    private final float debrisChance;
    private final double debrisSpeed;
    private int index = 0;
    private BiFunction<Vec3, RandomSource, Vec3> debrisVelocity;
    private Consumer<FallingBlockEntity> onDebris = debris -> {
    };

    /**
     * @param radius      horizontal radius
     * @param depth       how deep the bowl goes below the center
     * @param upHeight    how far above the center blocks get blasted away (trees, buildings)
     * @param debrisCount roughly how many blocks fly out as debris
     */
    public CraterCarver(ServerLevel level, BlockPos center, float radius, float depth, float upHeight,
                        Palette palette, long seed, int debrisCount, double debrisSpeed) {
        this.level = level;
        this.center = center;
        this.palette = palette;
        this.radius = radius;
        this.random = RandomSource.create(seed);
        this.debrisSpeed = debrisSpeed;

        record Entry(long pos, double dist) {
        }
        List<Entry> carveEntries = new ArrayList<>();
        List<Entry> liningEntries = new ArrayList<>();
        int r = (int) Math.ceil(radius * 1.15f);
        int down = (int) Math.ceil(depth * 1.3f);
        int up = (int) Math.ceil(upHeight);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                // Wobbly rim so the crater does not look like a perfect circle
                double angle = Math.atan2(dz, dx);
                double wobble = 1.0 + 0.12 * Math.sin(angle * 3 + seed % 7) + 0.07 * Math.sin(angle * 7 + seed % 13);
                double h = (dx * dx + dz * dz) / (radius * radius * wobble * wobble);
                for (int dy = -down; dy <= up; dy++) {
                    double v = dy < 0 ? (double) dy * dy / (depth * depth) : (double) dy * dy / (upHeight * upHeight);
                    double shape = h + v;
                    long pos = center.offset(dx, dy, dz).asLong();
                    if (shape <= 1.0) {
                        carveEntries.add(new Entry(pos, shape));
                    } else if (shape <= 1.35 && dy <= 1) {
                        liningEntries.add(new Entry(pos, Math.sqrt(h)));
                    }
                }
            }
        }
        carveEntries.sort(Comparator.comparingDouble(Entry::dist));
        liningEntries.sort(Comparator.comparingDouble(Entry::dist));
        carveEntries.forEach(e -> carve.add(e.pos()));
        liningEntries.forEach(e -> lining.add(e.pos()));
        this.debrisChance = carve.isEmpty() ? 0 : Math.min(1.0f, debrisCount / (float) carve.size());
    }

    /**
     * Custom debris behaviour: {@code velocity} gets the block offset from the crater center,
     * {@code onDebris} receives every launched block (e.g. to make it float and track it).
     */
    public CraterCarver withDebris(BiFunction<Vec3, RandomSource, Vec3> velocity, Consumer<FallingBlockEntity> onDebris) {
        this.debrisVelocity = velocity;
        this.onDebris = onDebris;
        return this;
    }

    public int size() {
        return carve.size() + lining.size();
    }

    public boolean isDone() {
        return index >= carve.size() + lining.size();
    }

    /** Processes up to {@code budget} positions; returns true once the crater is finished. */
    public boolean step(int budget) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int total = carve.size() + lining.size();
        int end = Math.min(total, index + budget);
        for (; index < end; index++) {
            if (index < carve.size()) {
                pos.set(carve.getLong(index));
                carveAt(pos);
            } else {
                pos.set(lining.getLong(index - carve.size()));
                lineAt(pos);
            }
        }
        return isDone();
    }

    /** Finishes everything at once (used when a sequence ends early). */
    public void finishNow() {
        step(Integer.MAX_VALUE);
    }

    private void carveAt(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!StrikeTools.canDestroy(level, pos, state)) return;

        if (random.nextFloat() < debrisChance) {
            Vec3 out = Vec3.atCenterOf(pos).subtract(Vec3.atCenterOf(center));
            Vec3 horizontal = new Vec3(out.x, 0, out.z);
            horizontal = horizontal.lengthSqr() < 1.0e-3
                    ? new Vec3(random.nextGaussian(), 0, random.nextGaussian()).normalize()
                    : horizontal.normalize();
            double speed = debrisSpeed * (0.5 + random.nextDouble() * 0.6);
            Vec3 velocity = debrisVelocity != null
                    ? debrisVelocity.apply(out, random)
                    : horizontal.scale(speed).add(0, debrisSpeed * (0.6 + random.nextDouble() * 0.7), 0);
            FallingBlockEntity debris = StrikeTools.launchDebris(level, pos.immutable(), velocity);
            if (debris != null) {
                onDebris.accept(debris);
                return;
            }
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
    }

    private void lineAt(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!StrikeTools.canDestroy(level, pos, state) || !state.isSolidRender()) return;
        double edge = Math.sqrt(pos.distSqr(center)) / radius;
        level.setBlock(pos, palette.pick(random, Math.min(1.0, edge)), FLAGS);
    }
}
