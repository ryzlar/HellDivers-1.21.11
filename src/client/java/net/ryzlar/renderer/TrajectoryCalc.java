package net.ryzlar.renderer;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.ryzlar.entities.StrategemBallPhysics;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class TrajectoryCalc {

    private static final int MAX_TICKS = 200; // 10 seconds of flight

    /**
     * @param points one point per tick, starting at the spawn position; the last point is the impact point if {@code hit} is set
     * @param hit    the block the ball will land on, or null if it flies out of range / out of the world
     */
    public record Result(List<Vec3> points, @Nullable BlockHitResult hit) {
    }

    /**
     * Simulates the ball tick by tick, mirroring ThrowableProjectile.tick():
     * gravity -> drag -> raycast from old to new position (same COLLIDER clip the entity uses).
     */
    public static Result calculate(Player player, float power, float partialTick) {
        Level level = player.level();
        List<Vec3> points = new ArrayList<>();

        Vec3 look = player.getViewVector(partialTick);
        Vec3 pos = StrategemBallPhysics.spawnPos(player.getEyePosition(partialTick), look);
        Vec3 velocity = StrategemBallPhysics.launchVelocity(look, power);
        points.add(pos);

        for (int tick = 0; tick < MAX_TICKS; tick++) {
            boolean inWater = level.getFluidState(BlockPos.containing(pos)).is(FluidTags.WATER);
            velocity = StrategemBallPhysics.nextVelocity(velocity, inWater);

            Vec3 next = pos.add(velocity);
            BlockHitResult clip = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (clip.getType() != HitResult.Type.MISS) {
                points.add(clip.getLocation());
                return new Result(points, clip);
            }

            pos = next;
            points.add(pos);

            if (pos.y < level.getMinY() - 16) break;
        }

        return new Result(points, null);
    }
}
