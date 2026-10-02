package net.ryzlar.items.effects;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.ryzlar.sound.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.ryzlar.entities.StrategemBallEntity;
import net.ryzlar.entities.StrategemBallPhysics;
import net.ryzlar.items.ModItems;
import net.ryzlar.items.StrategemBallItem;
import net.ryzlar.strategem.Strategem;
import net.ryzlar.strategem.StrategemCooldowns;
import net.ryzlar.strategem.Strategems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

public class StrategemBall {
    public static final int MAX_CHARGE = 72; // 3.6 seconds at 20 ticks/second

    /**
     * Throws one ball from the player's hand using StrategemBallPhysics, the same code the trajectory preview runs.
     * The thrown ball keeps the stratagem it was programmed with.
     */
    public static void throwBall(Player player, float power) {
        if (player.level().isClientSide()) return;

        InteractionHand hand = findBallHand(player);
        if (hand == null) return;
        ItemStack held = player.getItemInHand(hand);
        Strategem strategem = StrategemBallItem.getStrategem(held);
        if (strategem != null && player instanceof ServerPlayer serverPlayer) {
            long remaining = StrategemCooldowns.remainingTicks(serverPlayer, strategem);
            if (remaining > 0) {
                cooldownMessage(serverPlayer, strategem, remaining);
                return; // the ball stays in hand
            }
            StrategemCooldowns.start(serverPlayer, strategem);
        }
        ItemStack thrown = held.copyWithCount(1);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }

        Vec3 look = player.getLookAngle();
        // Throw feedback: a programmed ball sounds armed, an empty one is just thrown
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                strategem != null ? SoundEvents.TRIDENT_THROW.value() : SoundEvents.SNOWBALL_THROW,
                SoundSource.PLAYERS, 0.6f, strategem != null ? 1.5f : 0.8f);
        if (strategem != null) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.BEACON_ARM,
                    SoundSource.PLAYERS, 0.7f, 1.0f);
        }
        StrategemBallEntity ballEntity = new StrategemBallEntity(player.level(), player, thrown);
        ballEntity.setPos(StrategemBallPhysics.spawnPos(player.getEyePosition(), look));
        ballEntity.setDeltaMovement(StrategemBallPhysics.launchVelocity(look, power));
        player.level().addFreshEntity(ballEntity);
    }

    /**
     * Programs the ball in the player's hand with a stratagem entered in the menu.
     * Requires the arm piece in the off hand, like the menu itself.
     */
    public static void programBall(ServerPlayer player, Identifier strategemId) {
        Strategem strategem = Strategems.get(strategemId);
        if (strategem == null || player.getOffhandItem().getItem() != ModItems.ARM_PIECE) return;

        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof StrategemBallItem)) return;
        if (strategem == StrategemBallItem.getStrategem(held)) return; // already programmed with this one
        long remaining = StrategemCooldowns.remainingTicks(player, strategem);
        if (remaining > 0) {
            cooldownMessage(player, strategem, remaining);
            return;
        }

        // Only the ball in hand gets programmed; the rest of the stack goes back into the inventory
        if (held.getCount() > 1) {
            ItemStack rest = held.copyWithCount(held.getCount() - 1);
            held.setCount(1);
            player.getInventory().placeItemBackInInventory(rest);
        }
        StrategemBallItem.program(held, strategem);

        // Feedback for everyone around: a burst in the stratagem's color at the hand and a power-up sound
        ServerLevel level = player.level();
        Vec3 hand = player.getEyePosition().add(player.getLookAngle().scale(0.6)).add(0, -0.4, 0);
        // A clean data ring in the stratagem's color around the ball, facing the player, plus a few sparks
        Vec3 look = player.getLookAngle();
        Vec3 side = look.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(look).normalize();
        DustParticleOptions dust = new DustParticleOptions(strategem.color(), 0.6f);
        for (int i = 0; i < 20; i++) {
            double a = Math.PI * 2 * i / 20;
            Vec3 p = hand.add(side.scale(Math.cos(a) * 0.32)).add(up.scale(Math.sin(a) * 0.32));
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hand.x, hand.y, hand.z, 4, 0.08, 0.08, 0.08, 0.05);
        // the programming player hears the upload through the Arm Piece (client UI sounds); everyone else hears this
        level.playSound(player, player.getX(), player.getY(), player.getZ(), ModSounds.PROGRAM_COMPLETE,
                SoundSource.PLAYERS, 0.8f, 1.0f);
    }

    private static void cooldownMessage(ServerPlayer player, Strategem strategem, long remaining) {
        player.displayClientMessage(Component.translatable("strategem.lasermod.cooldown_message",
                strategem.displayName(), StrategemCooldowns.format(remaining)).withStyle(ChatFormatting.RED), true);
    }

    @Nullable
    private static InteractionHand findBallHand(Player player) {
        if (player.getMainHandItem().getItem() instanceof StrategemBallItem) return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().getItem() instanceof StrategemBallItem) return InteractionHand.OFF_HAND;
        return null;
    }
}
