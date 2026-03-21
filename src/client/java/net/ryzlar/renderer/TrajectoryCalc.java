package net.ryzlar.renderer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class TrajectoryCalc {

    private static final double GRAVITY = 0.06;
    private static final double DRAG = 0.97;
    private static final int MAX_STEPS = 80;

    public static List<Vec3> calculate(Player player, float power) {
        List<Vec3> points = new ArrayList<>();

        Vec3 pos = player.getEyePosition().subtract(0, 0.1, 0);
        Vec3 look = player.getLookAngle().normalize();
        Vec3 velocity = look.scale(power * 0.6);

        for (int i = 0; i < MAX_STEPS; i++) {
            points.add(pos);

            pos = pos.add(velocity);

            velocity = new Vec3(
                    velocity.x * DRAG,
                    velocity.y * DRAG - GRAVITY,
                    velocity.z * DRAG
            );

            // Stop als de bal de grond raakt
            if (player.level().getBlockState(BlockPos.containing(pos)).isSolid())
            {
                BlockPos blockPos = BlockPos.containing(pos);
                points.add(new Vec3(pos.x, blockPos.getY() + 1.0, pos.z));
                break;
            }
        }

        return points;
    }

    public static Vec3 getLandingPos(Player player, float power) {
        List<Vec3> points = calculate(player, power);
        return points.isEmpty() ? player.position() : points.get(points.size() - 1);
    }
}