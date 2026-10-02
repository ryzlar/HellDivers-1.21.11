package net.ryzlar.strike;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.ryzlar.LaserMod;
import net.ryzlar.sound.ModSounds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Void Rift (extreme). Reality is torn open where the beacon landed.
 *
 * <p>Timeline (ticks):
 * <ol>
 *     <li>{@code 0..TEAR} warning: space distorts, light bends, the ground starts to fracture.</li>
 *     <li>{@code TEAR..OPEN} tear: a white-hot fracture rips through the air <i>and the ground</i>, then widens into the
 *     rift. The opening is a fixed vertical tear (seeded orientation, identical on every client) whose lower part
 *     lies below the surface: the terrain inside the tear is consumed, so the void visibly continues into a real
 *     fissure in the ground.</li>
 *     <li>{@code OPEN..UNSTABLE} active: everything within {@link #PULL_RADIUS} is drawn toward the opening, harder
 *     the closer it gets. Anything that reaches the opening is pushed <i>through</i> its surface while it shrinks,
 *     and only removed once it has crossed.</li>
 *     <li>{@code UNSTABLE..CLOSING} the rift surges and lurches.</li>
 *     <li>{@code CLOSING..COMPRESS} the pull weakens to nothing, held debris drops, the interior contracts and the
 *     edges knit together into a blazing seam; {@code COMPRESS..SNAP} the seam compresses to a point.</li>
 *     <li>{@code SNAP} implosion pulse; then the scar fades ({@code SNAP..DURATION}).</li>
 * </ol>
 */
public class VoidRiftStrike implements StrikeSequence {

    public static final int TEAR = 70;
    public static final int OPEN = 150;
    public static final int UNSTABLE = 460;
    public static final int CLOSING = 540;
    public static final int COMPRESS = 610;
    public static final int SNAP = 632;
    public static final int DURATION = 780;

    /** Rift center above the ground: with {@link #FULL_HEIGHT} the bottom ~6 blocks of the tear are underground. */
    public static final float CENTER_ABOVE_GROUND = 4.5f;
    public static final float FULL_WIDTH = 9.0f;
    public static final float FULL_HEIGHT = 21.0f;
    public static final float PULL_RADIUS = 90.0f;
    public static final float RIP_RADIUS = 26.0f;
    /** Ticks an entity takes to pass through the rift surface. */
    public static final int CROSSING_TICKS = 16;

    /** Void erosion by distance to the opening: harmless far away, brutal at the edge. Every 10 ticks, ignores cover. */
    public static final DamageProfile EROSION = DamageProfile.of(0, 10, 2, 6, 6, 3, 14, 1, 24, 0);
    /** The implosion when the rift snaps shut. */
    public static final DamageProfile IMPLOSION = DamageProfile.of(0, 320, 5, 140, 12, 45, 24, 10, 36, 0);

    private static final Identifier SHRINK = Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "void_rift_shrink");

    private static final CraterCarver.Palette SCAR = (random, edge) -> {
        int roll = random.nextInt(10);
        return roll < 3 ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                : roll < 6 ? Blocks.BLACKSTONE.defaultBlockState()
                : roll < 8 ? Blocks.TUFF.defaultBlockState()
                : Blocks.OBSIDIAN.defaultBlockState();
    };

    // ---------------------------------------------------------------- shared timeline + geometry (server and client)

    public static Vec3 riftCenter(Vec3 base) {
        return base.add(0, CENTER_ABOVE_GROUND, 0);
    }

    /** Normal of the rift plane. The plane is vertical and never moves. */
    public static Vec3 normal(int seed) {
        double yaw = new Random(seed * 7919L + 3).nextDouble() * Math.PI * 2;
        return new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
    }

    /** Horizontal axis lying in the rift plane. */
    public static Vec3 right(int seed) {
        Vec3 n = normal(seed);
        return new Vec3(-n.z, 0, n.x);
    }

    /** How far the rift is open: 0 closed, ~0.12 a thin tear, 1 fully open. */
    public static float openness(float age) {
        if (age < TEAR) return 0.0f;
        if (age < TEAR + 25) return 0.12f * smooth((age - TEAR) / 25.0f);
        if (age < CLOSING) return 0.12f + 0.88f * smooth((age - TEAR - 25) / (OPEN - TEAR - 25.0f));
        if (age < COMPRESS) return 1.0f - 0.9f * smooth((age - CLOSING) / (float) (COMPRESS - CLOSING));
        if (age < SNAP) {
            float t = (age - COMPRESS) / (SNAP - COMPRESS);
            return 0.1f * (1.0f - t * t);
        }
        return 0.0f;
    }

    public static float width(float age) {
        float open = openness(age);
        return open < 0.12f ? 2.5f * open : 0.3f + (FULL_WIDTH - 0.3f) * (float) Math.pow((open - 0.12f) / 0.88f, 1.2);
    }

    public static float height(float age) {
        float open = openness(age);
        return open < 0.12f ? FULL_HEIGHT * 0.75f * open / 0.12f : FULL_HEIGHT * (0.75f + 0.25f * (open - 0.12f) / 0.88f);
    }

    /** Strength of the suction, 0..~1.6: builds while opening, surges when unstable, fades to 0 while closing. */
    public static float pull(float age) {
        if (age < TEAR) return 0.0f;
        if (age < OPEN) return openness(age);
        if (age < UNSTABLE) return 1.0f;
        if (age < CLOSING) return 1.0f + 0.6f * instability(age) * (0.55f + 0.45f * Mth.sin(age * 0.35f));
        if (age < COMPRESS) return 1.0f - (age - CLOSING) / (float) (COMPRESS - CLOSING);
        return 0.0f;
    }

    /** 0 while stable, rising to 1 before the closing, easing off while it closes. */
    public static float instability(float age) {
        if (age < UNSTABLE) return 0.0f;
        if (age < CLOSING) return (age - UNSTABLE) / (float) (CLOSING - UNSTABLE);
        if (age < COMPRESS) return 1.0f - 0.7f * (age - CLOSING) / (float) (COMPRESS - CLOSING);
        return 0.0f;
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        return t * t * (3 - 2 * t);
    }

    // ---------------------------------------------------------------- state

    /** An entity in the middle of passing through the rift surface. */
    private static final class Crossing {
        int ticks;
        final double side; // which side of the plane it came from (+1 / -1)

        Crossing(double side) {
            this.side = side;
        }
    }

    private final Map<Entity, Crossing> crossing = new HashMap<>();
    private final List<FallingBlockEntity> debris = new ArrayList<>();
    private final LongSet consumed = new LongOpenHashSet();
    private RandomSource random;
    private Vec3 normal;
    private Vec3 right;

    @Override
    public int duration() {
        return DURATION;
    }

    @Override
    public void tick(StrikeEntity strike, ServerLevel level, int age) {
        Vec3 base = strike.position();
        Vec3 rift = riftCenter(base);
        if (random == null) {
            random = RandomSource.create(strike.getSeed());
            normal = normal(strike.getSeed());
            right = right(strike.getSeed());
        }

        sounds(level, rift, age);

        float pull = pull(age);
        if (pull > 0.0f) suck(level, rift, age, pull);
        tickCrossing(level, rift, age);
        if (age >= TEAR && age < COMPRESS && age % 2 == 0) consumeTerrain(level, rift, age);
        if (age >= OPEN && age < CLOSING) ripGround(level, base, rift, age);
        if (age == CLOSING) releaseDebris(); // the pull lets go: whatever it held drops back down
        if (age == SNAP) snap(strike, level, base, rift);
    }

    private void sounds(ServerLevel level, Vec3 rift, int age) {
        if (age == 0) StrikeTools.sound(level, rift, ModSounds.RIFT_WARNING, 8.0f, 1.0f);
        if (age == TEAR - 7) StrikeTools.sound(level, rift, ModSounds.RIFT_TEAR, 12.0f, 1.0f);
        if (age == TEAR + 30) StrikeTools.sound(level, rift, ModSounds.RIFT_OPEN, 16.0f, 1.0f);
        if (age >= OPEN - 20 && age < CLOSING && (age - OPEN + 20) % 70 == 0) {
            StrikeTools.sound(level, rift, ModSounds.RIFT_LOOP, 12.0f, age >= UNSTABLE ? 0.85f : 1.0f);
        }
        if (age == UNSTABLE || age == UNSTABLE + 45) StrikeTools.sound(level, rift, ModSounds.RIFT_UNSTABLE, 12.0f, 1.0f);
        if (age == SNAP - 65) StrikeTools.sound(level, rift, ModSounds.RIFT_CLOSE, 14.0f, 1.0f);
        if (age == SNAP - 5) StrikeTools.sound(level, rift, ModSounds.RIFT_SNAP, 28.0f, 1.0f);
    }

    // ---------------------------------------------------------------- suction

    /** Everything is drawn toward the nearest point of the opening, harder and faster the closer it gets. */
    private void suck(ServerLevel level, Vec3 rift, int age, float pull) {
        float open = openness(age);
        float w = width(age);
        float h = height(age);
        boolean erode = level.getGameTime() % 10 == 0;

        for (Entity entity : StrikeTools.entitiesAround(level, rift, PULL_RADIUS)) {
            if (crossing.containsKey(entity)) continue;
            if (entity instanceof Player player && (player.isSpectator() || player.getAbilities().flying && player.isCreative())) {
                continue; // creative flyers can watch
            }
            Vec3 body = entity.getBoundingBox().getCenter();
            Vec3 rel = body.subtract(rift);
            double dn = rel.dot(normal);
            double du = rel.dot(right);
            double dv = rel.y;

            // Reached the opening: start passing through it
            double inside = sq(du / (w * 0.5)) + sq(dv / (h * 0.5));
            if (open > 0.3f && inside < 0.8 && Math.abs(dn) < 0.5 + entity.getBbWidth() * 0.5 && canBeSwallowed(entity)) {
                startCrossing(entity, dn);
                continue;
            }

            // Aim for the closest point on the opening (kept well inside the edge)
            double scale = Math.sqrt(sq(du / (w * 0.35)) + sq(dv / (h * 0.35)));
            double tu = scale > 1 ? du / scale : du;
            double tv = scale > 1 ? dv / scale : dv;
            Vec3 target = rift.add(right.scale(tu)).add(0, tv, 0);
            Vec3 toTarget = target.subtract(body);
            double distance = toTarget.length();
            if (distance < 1.0e-3) continue;
            Vec3 dir = toTarget.scale(1.0 / distance);

            double closeness = Mth.clamp(1.0 - distance / PULL_RADIUS, 0.0, 1.0);
            double strength = (0.015 + 0.08 * open) * pull * (0.15 + 0.85 * Math.pow(closeness, 2.5));
            if (distance < 14) strength += 0.07 * open * pull * (1.0 - distance / 14); // final, violent acceleration
            if (entity instanceof FallingBlockEntity) strength *= 1.8;

            // Swirl around the rift axis far out, straight in close by
            Vec3 swirl = normal.cross(dir).scale(0.5 * Math.min(1.0, distance / 20.0));
            if (entity instanceof Player) {
                // Players move client side: push them, the client integrates it
                Vec3 force = dir.add(swirl).scale(strength).add(0, 0.04 * closeness * pull, 0);
                StrikeTools.push(entity, force);
                capSpeed(entity, 0.9 + 1.6 * closeness);
            } else {
                // Everything else is steered: velocity bends toward the opening, so nothing ends up orbiting it
                double speed = Math.min(0.15 + strength * 18.0, (0.9 + 1.4 * closeness) * Math.min(1.0, pull + 0.2));
                Vec3 desired = dir.add(swirl.scale(0.6)).normalize().scale(speed);
                double steer = (0.06 + 0.25 * closeness) * Math.min(1.0, pull);
                Vec3 velocity = entity.getDeltaMovement().scale(1.0 - steer).add(desired.scale(steer));
                entity.setDeltaMovement(velocity.add(0, 0.04 * closeness * pull, 0));
                entity.hurtMarked = true;
                entity.resetFallDistance();
            }

            if (erode && entity instanceof LivingEntity) {
                double toEdge = StrikeTools.distanceTo(entity, target);
                StrikeTools.hit(level, entity, target, toEdge, EROSION, level.damageSources().magic(), 1.0f, 0.0, 0, false);
            }
        }
    }

    private static boolean canBeSwallowed(Entity entity) {
        return !(entity instanceof Player player) || !player.isCreative();
    }

    private void startCrossing(Entity entity, double dn) {
        crossing.put(entity, new Crossing(dn >= 0 ? 1.0 : -1.0));
        entity.setNoGravity(true);
        if (entity instanceof Mob mob) mob.setNoAi(true);
    }

    /**
     * Entities passing through the surface: carried along the normal through the plane, drawn to the middle of the
     * opening and shrunk, so the rift visibly swallows them (the opaque rift surface hides whatever is past it).
     * Removed only once their body has crossed.
     */
    private void tickCrossing(ServerLevel level, Vec3 rift, int age) {
        Iterator<Map.Entry<Entity, Crossing>> it = crossing.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Entity, Crossing> entry = it.next();
            Entity entity = entry.getKey();
            Crossing state = entry.getValue();
            if (!entity.isAlive()) {
                it.remove();
                continue;
            }
            state.ticks++;
            Vec3 body = entity.getBoundingBox().getCenter();
            Vec3 rel = body.subtract(rift);
            double dn = rel.dot(normal);

            Vec3 through = normal.scale(-state.side * (0.08 + state.ticks * 0.025));
            Vec3 inPlane = right.scale(-rel.dot(right) * 0.12).add(0, -rel.y * 0.12, 0);
            entity.setDeltaMovement(through.add(inPlane));
            entity.hurtMarked = true;
            entity.resetFallDistance();

            float shrink = Math.min(0.92f, state.ticks / (float) CROSSING_TICKS);
            if (entity instanceof LivingEntity living) setShrink(living, shrink);

            boolean crossed = dn * state.side < -(entity.getBbWidth() * 0.5 + 0.15);
            if (crossed || state.ticks >= CROSSING_TICKS + 6 || age >= SNAP) {
                it.remove();
                swallow(level, entity);
            }
        }
    }

    private void swallow(ServerLevel level, Entity entity) {
        if (entity instanceof Player player) {
            restore(player);
            player.hurtServer(level, level.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
        } else {
            entity.discard(); // gone into the void: no corpse, no drops
        }
    }

    private static void setShrink(LivingEntity living, float amount) {
        AttributeInstance scale = living.getAttribute(Attributes.SCALE);
        if (scale == null) return;
        scale.removeModifier(SHRINK);
        scale.addTransientModifier(new AttributeModifier(SHRINK, -amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void restore(Entity entity) {
        entity.setNoGravity(false);
        if (entity instanceof Mob mob) mob.setNoAi(false);
        if (entity instanceof LivingEntity living) {
            AttributeInstance scale = living.getAttribute(Attributes.SCALE);
            if (scale != null) scale.removeModifier(SHRINK);
        }
    }

    private static void capSpeed(Entity entity, double max) {
        Vec3 v = entity.getDeltaMovement();
        double speed = v.length();
        if (speed > max) entity.setDeltaMovement(v.scale(max / speed));
    }

    private static double sq(double v) {
        return v * v;
    }

    // ---------------------------------------------------------------- terrain

    /**
     * The tear is real: every block inside the current opening, in a slab one block either side of the rift plane,
     * is consumed (the terrain under the surface included). The underground half of the rift therefore sits in an
     * actual fissure, and the void visibly continues into the ground instead of being cut off by it.
     */
    private void consumeTerrain(ServerLevel level, Vec3 rift, int age) {
        float w = width(age) * 0.5f * 0.92f;
        float h = height(age) * 0.5f * 0.92f;
        if (w < 0.2f) return;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int budget = 90;
        for (float v = -h; v <= h && budget > 0; v += 0.5f) {
            double rowHalf = w * Math.sqrt(Math.max(0.0, 1.0 - sq(v / h)));
            for (float u = (float) -rowHalf; u <= rowHalf && budget > 0; u += 0.5f) {
                for (float n = -0.75f; n <= 0.75f; n += 0.75f) {
                    Vec3 p = rift.add(right.scale(u)).add(normal.scale(n)).add(0, v, 0);
                    pos.set(Mth.floor(p.x), Mth.floor(p.y), Mth.floor(p.z));
                    if (!consumed.add(pos.asLong())) continue;
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || !StrikeTools.canDestroy(level, pos, state)) continue;
                    budget--;
                    // a few pieces visibly tear loose and fall into the void, the rest is simply gone
                    if (random.nextInt(5) == 0) {
                        Vec3 toRift = rift.subtract(Vec3.atCenterOf(pos));
                        FallingBlockEntity block = StrikeTools.launchDebris(level, pos.immutable(),
                                toRift.normalize().scale(0.12).add(0, 0.08, 0));
                        if (block != null) {
                            block.setNoGravity(true);
                            debris.add(block);
                            continue;
                        }
                    }
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    /** Tears loose blocks out of the ground around it and lets the rift draw them in. */
    private void ripGround(ServerLevel level, Vec3 base, Vec3 rift, int age) {
        float growth = Mth.clamp((age - OPEN) / (float) (UNSTABLE - OPEN), 0.0f, 1.0f);
        float radius = Mth.lerp(growth, 7.0f, RIP_RADIUS);
        int count = 1 + Math.round(3 * instability(age));
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 3 + (radius - 3) * Math.sqrt(random.nextDouble());
            BlockPos top = StrikeTools.surface(level, Mth.floor(base.x + Math.cos(angle) * dist), Mth.floor(base.z + Math.sin(angle) * dist));
            Vec3 toRift = rift.subtract(Vec3.atCenterOf(top)).normalize();
            FallingBlockEntity block = StrikeTools.launchDebris(level, top, toRift.scale(0.2).add(0, 0.4, 0));
            if (block != null) {
                block.setNoGravity(true);
                debris.add(block);
            }
        }
        debris.removeIf(e -> !e.isAlive());
    }

    /** The seam snaps shut: an implosion pulse, then a small blackened scar where the tear met the ground. */
    private void snap(StrikeEntity strike, ServerLevel level, Vec3 base, Vec3 rift) {
        for (Entity entity : StrikeTools.entitiesAround(level, rift, IMPLOSION.maxRadius() + 2)) {
            StrikeTools.hit(level, entity, rift, StrikeTools.distanceTo(entity, rift), IMPLOSION,
                    level.damageSources().explosion(strike, strike.getOwner()), 0.5f, 2.0, 0, true);
        }
        new CraterCarver(level, BlockPos.containing(base), 4.0f, 2.5f, 2.0f, SCAR, strike.getSeed(), 0, 0).finishNow();
        releaseDebris();
    }

    /** Anything the rift had not swallowed yet falls back down. */
    private void releaseDebris() {
        for (FallingBlockEntity block : debris) {
            if (block.isAlive()) {
                block.setNoGravity(false);
                block.setDeltaMovement(block.getDeltaMovement().scale(0.3));
                block.hurtMarked = true;
            }
        }
        debris.clear();
    }

    @Override
    public void finish(StrikeEntity strike, ServerLevel level) {
        releaseDebris();
        for (Entity entity : crossing.keySet()) {
            if (entity.isAlive()) restore(entity);
        }
        crossing.clear();
    }
}
