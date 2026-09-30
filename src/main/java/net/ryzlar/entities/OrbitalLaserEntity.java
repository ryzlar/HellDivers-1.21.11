package net.ryzlar.entities;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.ryzlar.laser.OrbitalLaserEvents;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Invisible, server-authoritative anchor for an orbital laser beam.
 * The client renders the beam and timer from this entity's synced data; when the timer runs out
 * the entity fires {@link OrbitalLaserEvents#EXPIRED} and removes itself, which removes the beam for everyone.
 */
public class OrbitalLaserEntity extends Entity {

    public static final int DEFAULT_DURATION = 15 * 20; // 15 seconds
    public static final int DEFAULT_COLOR = 0xFF3333;

    private static final EntityDataAccessor<Integer> COLOR =
            SynchedEntityData.defineId(OrbitalLaserEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HEIGHT =
            SynchedEntityData.defineId(OrbitalLaserEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DURATION =
            SynchedEntityData.defineId(OrbitalLaserEntity.class, EntityDataSerializers.INT);
    // Level game time at which the laser expires; 0 = not started yet (e.g. spawned with /summon)
    private static final EntityDataAccessor<Long> END_TIME =
            SynchedEntityData.defineId(OrbitalLaserEntity.class, EntityDataSerializers.LONG);

    @Nullable
    private UUID ownerUUID;

    public OrbitalLaserEntity(EntityType<? extends OrbitalLaserEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static OrbitalLaserEntity spawn(ServerLevel level, Vec3 base, @Nullable UUID owner, int color, int durationTicks) {
        OrbitalLaserEntity laser = new OrbitalLaserEntity(ModEntities.ORBITAL_LASER, level);
        laser.setPos(base);
        laser.ownerUUID = owner;
        laser.entityData.set(COLOR, color);
        laser.entityData.set(DURATION, durationTicks);
        laser.entityData.set(HEIGHT, Math.max(64, level.getMaxY() + 128 - (int) base.y));
        laser.entityData.set(END_TIME, level.getGameTime() + durationTicks);
        level.addFreshEntity(laser);
        return laser;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(COLOR, DEFAULT_COLOR);
        builder.define(HEIGHT, 320);
        builder.define(DURATION, DEFAULT_DURATION);
        builder.define(END_TIME, 0L);
    }

    @Override
    public void tick() {
        // No super.tick(): the anchor never moves, burns, swims or goes through portals
        if (!(level() instanceof ServerLevel serverLevel)) return;

        if (getEndTime() == 0L) {
            entityData.set(END_TIME, serverLevel.getGameTime() + getDuration());
        }
        if (serverLevel.getGameTime() >= getEndTime()) {
            OrbitalLaserEvents.EXPIRED.invoker().onExpired(serverLevel, this);
            discard();
        }
    }

    // ---------------------------------------------------------------- accessors

    public int getColor() {
        return entityData.get(COLOR);
    }

    public int getBeamHeight() {
        return entityData.get(HEIGHT);
    }

    public int getDuration() {
        return entityData.get(DURATION);
    }

    public long getEndTime() {
        return entityData.get(END_TIME);
    }

    /** Remaining time in ticks; pass the partial tick for a smooth value while rendering. */
    public float getRemainingTicks(float partialTick) {
        if (getEndTime() == 0L) return getDuration();
        return Math.max(0.0f, getEndTime() - level().getGameTime() - partialTick);
    }

    @Nullable
    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    // ---------------------------------------------------------------- persistence

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("Color", getColor());
        output.putInt("Height", getBeamHeight());
        output.putInt("Duration", getDuration());
        output.putInt("Remaining", (int) getRemainingTicks(0.0f));
        output.storeNullable("Owner", UUIDUtil.CODEC, ownerUUID);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        entityData.set(COLOR, input.getIntOr("Color", DEFAULT_COLOR));
        entityData.set(HEIGHT, input.getIntOr("Height", 320));
        entityData.set(DURATION, input.getIntOr("Duration", DEFAULT_DURATION));
        int remaining = input.getIntOr("Remaining", getDuration());
        entityData.set(END_TIME, level().getGameTime() + remaining);
        ownerUUID = input.read("Owner", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true; // the beam is visible from far away; LaserRenderer draws it
    }
}
