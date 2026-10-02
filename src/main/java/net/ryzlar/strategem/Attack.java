package net.ryzlar.strategem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** What a stratagem does when its ball lands. Implementations live in {@link Attacks}. */
@FunctionalInterface
public interface Attack {

    void execute(Context context);

    /**
     * @param level     the server level
     * @param strategem the stratagem being called in (color, name, ...)
     * @param impact    exact point where the ball hit
     * @param ground    the free block space next to the face that was hit (on top of it for floors)
     * @param face      face of the block that was hit
     * @param owner     whoever threw the ball, if known
     */
    record Context(ServerLevel level, Strategem strategem, Vec3 impact, BlockPos ground, Direction face,
                   @Nullable Entity owner) {
    }
}
