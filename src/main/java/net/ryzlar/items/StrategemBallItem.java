package net.ryzlar.items;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.ryzlar.strategem.Strategem;
import net.ryzlar.strategem.Strategems;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * One universal ball. What it calls in is stored per stack in {@link ModComponents#STRATEGEM},
 * written by programming it through the stratagem menu.
 */
public class StrategemBallItem extends Item {
    public StrategemBallItem(Item.Properties properties) {
        super(properties);
    }

    @Nullable
    public static Strategem getStrategem(ItemStack stack) {
        return Strategems.get(stack.get(ModComponents.STRATEGEM));
    }

    public static void program(ItemStack stack, Strategem strategem) {
        stack.set(ModComponents.STRATEGEM, strategem.id());
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.TRIDENT; // overhand wind-up, like actually throwing a beacon
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        // Like a bow: hold as long as you want, power is clamped to MAX_CHARGE on release.
        // Returning MAX_CHARGE here would auto-finish the use and throw the ball by itself.
        return 72000;
    }

    /** Programmed balls glint, so you can tell them apart from empty ones at a glance. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return getStrategem(stack) != null || super.isFoil(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        Strategem strategem = getStrategem(stack);
        if (strategem == null) {
            return Component.translatable("item.lasermod.strategem_ball.empty");
        }
        return Component.translatable("item.lasermod.strategem_ball.programmed",
                strategem.displayName().copy().withColor(strategem.color()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        Strategem strategem = getStrategem(stack);
        if (strategem == null) {
            tooltip.accept(Component.translatable("item.lasermod.strategem_ball.empty.hint").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.accept(Component.literal(strategem.arrows()).withColor(strategem.color()));
        }
    }
}
