package net.ryzlar.items.effects;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.ryzlar.entities.StrategemBallEntity;
import net.ryzlar.entities.StrategemBallPhysics;

public class StrategemBall {
    public static final int MAX_CHARGE = 72; // 3.6 seconds at 20 ticks/second

    /**
     * Throws the stratagem ball using StrategemBallPhysics, the same code the trajectory preview runs.
     */
    public static void throwBall(Player player, float power) {
        if (player.level().isClientSide()) return;

        Vec3 look = player.getLookAngle();

        StrategemBallEntity ballEntity = new StrategemBallEntity(player.level(), player);
        ballEntity.setPos(StrategemBallPhysics.spawnPos(player.getEyePosition(), look));
        ballEntity.setDeltaMovement(StrategemBallPhysics.launchVelocity(look, power));
        player.level().addFreshEntity(ballEntity);
    }
}
