package net.ryzlar.items.effects;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import net.ryzlar.entities.StrategemBallEntity;
import net.ryzlar.laser.BeamData;

import java.util.ArrayList;
import java.util.List;

public class StrategemBall extends Item
{

    public static final int MAX_CHARGE = 10;

//    TEMP LIST
//    private static List<BeamData> beams = new ArrayList<>();


    public StrategemBall(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand)
    {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;

    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return false;
        if (level.isClientSide()) return false;

        int charge = getUseDuration(stack, entity) - timeLeft;
        float power = Math.min(1.0f, charge / (float) MAX_CHARGE);

        if (power > 0.1f) {
            StrategemBallEntity ball = new StrategemBallEntity(level, player);
            ball.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, power * 0.8f, 0.5f);
            level.addFreshEntity(ball);
        }

        return true;
    }

    @Override
    public boolean useOnRelease(ItemStack stack) {
        return true;
    }

    @Override
    public int getUseDuration(ItemStack stack, net.minecraft.world.entity.LivingEntity entity) {
        return 72000;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW;
    }

}
