package net.ryzlar.strike;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.ryzlar.entities.ModEntities;
import net.ryzlar.strategem.Attack;
import org.jetbrains.annotations.Nullable;

/**
 * Invisible anchor for a running multi-stage attack. The server runs its {@link StrikeSequence};
 * clients only see the synced stratagem id, seed and start time, and render the visual for that id.
 * Not saved: a strike that is interrupted by a restart simply ends.
 */
public class StrikeEntity extends Entity {

    private static final EntityDataAccessor<String> STRATEGEM =
            SynchedEntityData.defineId(StrikeEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> SEED =
            SynchedEntityData.defineId(StrikeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START_TIME =
            SynchedEntityData.defineId(StrikeEntity.class, EntityDataSerializers.LONG);

    @Nullable
    private StrikeSequence sequence;
    @Nullable
    private Entity owner;

    /** Client bookkeeping: last age the visual processed, so every tick event fires exactly once. */
    public int lastClientAge = Integer.MIN_VALUE;

    public StrikeEntity(EntityType<? extends StrikeEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static StrikeEntity launch(Attack.Context ctx, StrikeSequence sequence) {
        ServerLevel level = ctx.level();
        StrikeEntity strike = new StrikeEntity(ModEntities.STRIKE, level);
        strike.setPos(Vec3.atBottomCenterOf(ctx.ground()));
        strike.sequence = sequence;
        strike.owner = ctx.owner();
        strike.entityData.set(STRATEGEM, ctx.strategem().id().toString());
        strike.entityData.set(SEED, level.getRandom().nextInt());
        strike.entityData.set(START_TIME, level.getGameTime());
        level.addFreshEntity(strike);
        return strike;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STRATEGEM, "");
        builder.define(SEED, 0);
        builder.define(START_TIME, 0L);
    }

    @Override
    public void tick() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        if (sequence == null) {
            discard();
            return;
        }
        int age = (int) (serverLevel.getGameTime() - getStartTime());
        if (age >= sequence.duration()) {
            sequence.finish(this, serverLevel);
            sequence = null;
            discard();
            return;
        }
        sequence.tick(this, serverLevel, age);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (sequence != null && level() instanceof ServerLevel serverLevel) {
            StrikeSequence running = sequence;
            sequence = null;
            running.finish(this, serverLevel);
        }
        super.remove(reason);
    }

    // ---------------------------------------------------------------- accessors

    public Identifier getStrategemId() {
        Identifier id = Identifier.tryParse(entityData.get(STRATEGEM));
        return id != null ? id : Identifier.withDefaultNamespace("empty");
    }

    public int getSeed() {
        return entityData.get(SEED);
    }

    public long getStartTime() {
        return entityData.get(START_TIME);
    }

    /** Ticks since the strike started, smooth between ticks when given the partial tick. */
    public float getAge(float partialTick) {
        return level().getGameTime() - getStartTime() + partialTick;
    }

    @Nullable
    public Entity getOwner() {
        return owner;
    }

    // ---------------------------------------------------------------- entity plumbing

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }
}
