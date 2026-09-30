package net.ryzlar.entities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.ryzlar.items.ModItems;

public class StrategemBallEntity extends ThrowableProjectile implements ItemSupplier {

    public StrategemBallEntity(Level level, Player player) {
        super(ModEntities.STRATAGEM_BALL, player.getX(), player.getEyeY() - 0.1, player.getZ(), level);
        this.setOwner(player);
    }

    public StrategemBallEntity(EntityType<? extends StrategemBallEntity> type, Level level) {
        super(type, level);
    }

    // Drag (0.99 / 0.8 in water) is applied by ThrowableProjectile itself, see StrategemBallPhysics
    @Override
    protected double getDefaultGravity() {
        return StrategemBallPhysics.GRAVITY;
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (level().isClientSide()) return;

        // Beam starts in the block space next to the face that was hit (on top of it for floors)
        BlockPos base = result.getBlockPos().relative(result.getDirection());
        OrbitalLaserEntity.spawn((ServerLevel) level(), Vec3.atBottomCenterOf(base),
                getOwner() != null ? getOwner().getUUID() : null,
                OrbitalLaserEntity.DEFAULT_COLOR, OrbitalLaserEntity.DEFAULT_DURATION);

        this.discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(ModItems.STRATEGEM_BALL);
    }
}