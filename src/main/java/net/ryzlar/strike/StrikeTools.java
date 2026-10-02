package net.ryzlar.strike;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Shared server-side building blocks for strikes: falloff damage, push/pull, debris, sounds, terrain queries. */
public final class StrikeTools {

    private StrikeTools() {
    }

    // ---------------------------------------------------------------- entities

    /** Everything a strike can affect around {@code center}: no spectators, no other strikes. */
    public static List<Entity> entitiesAround(ServerLevel level, Vec3 center, double radius) {
        return level.getEntities((Entity) null, new AABB(center, center).inflate(radius), e ->
                !(e instanceof StrikeEntity) && e.isAlive() && !e.isSpectator()
                        && e.distanceToSqr(center) <= radius * radius);
    }

    /** Distance from {@code center} to the closest point of the entity's hitbox, so large mobs are hit at their edge. */
    public static double distanceTo(Entity entity, Vec3 center) {
        AABB box = entity.getBoundingBox();
        double dx = Math.max(Math.max(box.minX - center.x, 0), center.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - center.y, 0), center.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - center.z, 0), center.z - box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Applies a {@link DamageProfile} to everything in range.
     *
     * @param coverMin  how much damage still gets through full cover (1 = cover does not help, e.g. the fireball itself;
     *                  0.3 = hiding behind a wall stops 70%). Uses the same line-of-sight test as vanilla explosions.
     * @param knockback push at full strength; scales with how hard the entity was hit
     */
    public static void blast(ServerLevel level, Vec3 center, DamageProfile profile, DamageSource source,
                             float coverMin, double knockback, int fireSeconds) {
        blast(level, center, profile, source, coverMin, knockback, fireSeconds, false);
    }

    /** {@link #blast}, optionally as the primary blow of a stage (see {@link #hit} with {@code primary}). */
    public static void blast(ServerLevel level, Vec3 center, DamageProfile profile, DamageSource source,
                             float coverMin, double knockback, int fireSeconds, boolean primary) {
        for (Entity entity : entitiesAround(level, center, profile.maxRadius() + 2)) {
            hit(level, entity, center, distanceTo(entity, center), profile, source, coverMin, knockback, fireSeconds, primary);
        }
    }

    /** One entity, one profile. Returns the damage dealt before armor (0 if out of range). */
    public static float hit(ServerLevel level, Entity entity, Vec3 center, DamageProfile profile, DamageSource source,
                            float coverMin, double knockback, int fireSeconds) {
        return hit(level, entity, center, distanceTo(entity, center), profile, source, coverMin, knockback, fireSeconds, false);
    }

    /**
     * Full form. {@code distance} is measured by the caller (to a point, a column, a surface...).
     * {@code primary} marks the main blow of an attack stage: it ignores the victim's damage cooldown, so a big hit
     * that lands a few ticks after a small one (heat, a bolt) is not swallowed by vanilla invulnerability frames.
     */
    public static float hit(ServerLevel level, Entity entity, Vec3 center, double distance, DamageProfile profile,
                            DamageSource source, float coverMin, double knockback, int fireSeconds, boolean primary) {
        float damage = profile.at(distance);
        if (damage <= 0.0f) return 0.0f;
        float strength = damage / profile.peak();
        float exposure = coverMin >= 1.0f ? 1.0f : ServerExplosion.getSeenPercent(center, entity);
        float factor = coverMin + (1.0f - coverMin) * exposure;

        if (entity instanceof LivingEntity living) {
            if (primary) living.invulnerableTime = 0;
            living.hurtServer(level, source, damage * factor);
            if (fireSeconds > 0 && factor > 0.4f) living.igniteForSeconds(fireSeconds * Mth.sqrt(strength));
        }
        if (knockback > 0) {
            Vec3 away = entity.position().subtract(center);
            Vec3 dir = away.lengthSqr() < 1.0e-4 ? new Vec3(0, 1, 0) : away.normalize();
            double push = knockback * Math.sqrt(strength) * factor;
            push(entity, dir.scale(push).add(0, 0.35 * push, 0));
        }
        return damage * factor;
    }

    /**
     * Damage along a vertical column (a lightning channel, a lava fountain): distance is measured to the segment
     * from {@code bottom} up {@code height} blocks, so standing on a pillar next to it is no safer than below it.
     */
    public static void columnBlast(ServerLevel level, Vec3 bottom, double height, DamageProfile profile, DamageSource source,
                                   float coverMin, double knockback, int fireSeconds, boolean primary) {
        Vec3 top = bottom.add(0, height, 0);
        Vec3 middle = bottom.add(0, height * 0.5, 0);
        double reach = profile.maxRadius() + height * 0.5 + 2;
        for (Entity entity : entitiesAround(level, middle, reach)) {
            double distance = distanceToSegment(entity, bottom, top);
            Vec3 nearest = closestOnSegment(entity.getBoundingBox().getCenter(), bottom, top);
            hit(level, entity, nearest, distance, profile, source, coverMin, knockback, fireSeconds, primary);
        }
    }

    /** Distance from the entity's hitbox to the segment a-b. */
    public static double distanceToSegment(Entity entity, Vec3 a, Vec3 b) {
        return distanceTo(entity, closestOnSegment(entity.getBoundingBox().getCenter(), a, b));
    }

    public static Vec3 closestOnSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double lengthSqr = ab.lengthSqr();
        if (lengthSqr < 1.0e-8) return a;
        double t = Mth.clamp(p.subtract(a).dot(ab) / lengthSqr, 0.0, 1.0);
        return a.add(ab.scale(t));
    }

    /**
     * A shock front expanding from {@code center}: everything the front passed since last tick (between
     * {@code previousRadius} and {@code radius}) is hit once with the profile and thrown outward.
     */
    public static void shockFront(ServerLevel level, Vec3 center, float previousRadius, float radius, DamageProfile profile,
                                  DamageSource source, float coverMin, double knockback, java.util.Set<java.util.UUID> alreadyHit) {
        if (radius <= 0 || previousRadius > profile.maxRadius()) return;
        for (Entity entity : entitiesAround(level, center, Math.min(radius, profile.maxRadius()) + 1)) {
            double distance = distanceTo(entity, center);
            if (distance < previousRadius - 1 || !alreadyHit.add(entity.getUUID())) continue;
            hit(level, entity, center, distance, profile, source, coverMin, knockback, 0, false);
        }
    }

    /** Adds velocity, including for players (whose movement is client side). */
    public static void push(Entity entity, Vec3 velocity) {
        entity.setDeltaMovement(entity.getDeltaMovement().add(velocity));
        entity.hurtMarked = true;
        if (velocity.y > 0) entity.resetFallDistance();
    }

    // ---------------------------------------------------------------- blocks

    public static boolean canDestroy(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir() && level.isLoaded(pos) && state.getDestroySpeed(level, pos) >= 0.0f;
    }

    /** Position of the highest solid-ish block at x/z (the ground surface). */
    public static BlockPos surface(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
    }

    /**
     * Turns a block into flying debris. Only plain full blocks fly (no chests, plants, fluids);
     * returns null for anything else.
     */
    @Nullable
    public static FallingBlockEntity launchDebris(ServerLevel level, BlockPos pos, Vec3 velocity) {
        BlockState state = level.getBlockState(pos);
        if (!canDestroy(level, pos, state) || state.hasBlockEntity() || !state.isCollisionShapeFullBlock(level, pos)) {
            return null;
        }
        FallingBlockEntity debris = FallingBlockEntity.fall(level, pos, state);
        debris.setDeltaMovement(velocity);
        debris.hurtMarked = true;
        debris.disableDrop();
        debris.setHurtsEntities(1.5f, 30);
        return debris;
    }

    /**
     * Flings a brand-new block (not taken from the world) from {@code pos}, e.g. a lava bomb.
     * Uses an air block as launch pad because FallingBlockEntity can only be created from the world.
     */
    @Nullable
    public static FallingBlockEntity throwBlock(ServerLevel level, BlockPos pos, BlockState state, Vec3 velocity) {
        if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()) return null;
        FallingBlockEntity block = FallingBlockEntity.fall(level, pos, state);
        block.setDeltaMovement(velocity);
        block.hurtMarked = true;
        block.disableDrop();
        block.setHurtsEntities(2.0f, 40);
        return block;
    }

    /**
     * Blast-zone scorch: on random columns in a ring, strips vegetation and trees (up to {@code maxStrip} blocks)
     * and replaces the ground with a burnt version.
     */
    public static void scorchColumns(ServerLevel level, Vec3 center, double minRadius, double maxRadius, int columns,
                                     RandomSource random, int maxStrip) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < columns; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = Mth.lerp(Math.sqrt(random.nextDouble()), minRadius, maxRadius);
            int x = Mth.floor(center.x + Math.cos(angle) * dist);
            int z = Mth.floor(center.z + Math.sin(angle) * dist);
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            pos.set(x, top, z);
            if (!level.isLoaded(pos)) continue;

            for (int strip = 0; strip < maxStrip; strip++) {
                BlockState state = level.getBlockState(pos);
                if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.canBeReplaced()
                        || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)) {
                    if (!state.isAir() && canDestroy(level, pos, state)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                    pos.move(0, -1, 0);
                } else {
                    break;
                }
            }
            BlockState ground = level.getBlockState(pos);
            BlockState burnt = scorched(ground, random);
            if (burnt != null && canDestroy(level, pos, ground)) {
                level.setBlock(pos, burnt, Block.UPDATE_CLIENTS);
            }
        }
    }

    @Nullable
    private static BlockState scorched(BlockState ground, RandomSource random) {
        if (ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.PODZOL) || ground.is(Blocks.MYCELIUM) || ground.is(Blocks.DIRT)) {
            return random.nextInt(4) == 0 ? Blocks.DIRT.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
        }
        if (ground.is(BlockTags.SAND)) {
            return random.nextInt(3) == 0 ? Blocks.GLASS.defaultBlockState() : null;
        }
        if (ground.is(Blocks.SNOW_BLOCK) || ground.is(Blocks.ICE) || ground.is(Blocks.PACKED_ICE)) {
            return Blocks.WATER.defaultBlockState();
        }
        return null;
    }

    /**
     * Sets fires on the ground in a ring: only on top of solid, non-flammable ground (scorched soil, stone, sand)
     * with air above, so the fire marks the blast zone without instantly turning whole forests into a firestorm.
     */
    public static void igniteGround(ServerLevel level, Vec3 center, double minRadius, double maxRadius, int attempts,
                                    RandomSource random) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < attempts; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = Mth.lerp(Math.sqrt(random.nextDouble()), minRadius, maxRadius);
            int x = Mth.floor(center.x + Math.cos(angle) * dist);
            int z = Mth.floor(center.z + Math.sin(angle) * dist);
            BlockPos ground = surface(level, x, z);
            pos.set(ground).move(0, 1, 0);
            if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()) continue;
            BlockState below = level.getBlockState(ground);
            if (!below.isFaceSturdy(level, ground, net.minecraft.core.Direction.UP) || below.ignitedByLava()) continue;
            level.setBlock(pos, net.minecraft.world.level.block.BaseFireBlock.getState(level, pos), Block.UPDATE_ALL);
        }
    }

    // ---------------------------------------------------------------- sound

    /** Big, far-reaching sound (range grows with volume: ~16 blocks per unit). */
    public static void sound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    public static void sound(ServerLevel level, Vec3 pos, Holder<SoundEvent> sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    public static boolean isPlayerInCreative(Entity entity) {
        return entity instanceof Player player && (player.isCreative() || player.isSpectator());
    }
}
