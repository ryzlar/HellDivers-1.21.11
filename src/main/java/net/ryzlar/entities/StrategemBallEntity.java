package net.ryzlar.entities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.ryzlar.items.ModItems;
import net.ryzlar.items.StrategemBallItem;
import net.ryzlar.strategem.Attack;
import net.ryzlar.strategem.Attacks;
import net.ryzlar.strategem.Strategem;

/**
 * Thrown Strategem Ball. Carries (and syncs) the thrown item stack, so the stratagem it was
 * programmed with travels with it; on impact that stratagem's attack is executed.
 */
public class StrategemBallEntity extends ThrowableItemProjectile {

    public StrategemBallEntity(Level level, Player player, ItemStack ball) {
        super(ModEntities.STRATAGEM_BALL, player, level, ball);
    }

    public StrategemBallEntity(EntityType<? extends StrategemBallEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.STRATEGEM_BALL;
    }

    // Drag (0.99 / 0.8 in water) is applied by ThrowableProjectile itself, see StrategemBallPhysics
    @Override
    protected double getDefaultGravity() {
        return StrategemBallPhysics.GRAVITY;
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!(level() instanceof ServerLevel serverLevel)) return;

        Strategem strategem = StrategemBallItem.getStrategem(getItem());
        if (strategem == null) {
            Attacks.fizzle(serverLevel, result.getLocation()); // empty ball: nothing happens
        } else {
            BlockPos ground = result.getBlockPos().relative(result.getDirection());
            strategem.attack().execute(new Attack.Context(serverLevel, strategem, result.getLocation(), ground,
                    result.getDirection(), getOwner()));
        }

        this.discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
    }
}
