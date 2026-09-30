package net.ryzlar.laser;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerLevel;
import net.ryzlar.entities.OrbitalLaserEntity;

public final class OrbitalLaserEvents {

    /**
     * Fired on the server right before an orbital laser is removed because its timer ran out.
     * Hook follow-up effects in here: {@code OrbitalLaserEvents.EXPIRED.register((level, laser) -> ...)}.
     */
    public static final Event<Expired> EXPIRED = EventFactory.createArrayBacked(Expired.class,
            listeners -> (level, laser) -> {
                for (Expired listener : listeners) {
                    listener.onExpired(level, laser);
                }
            });

    private OrbitalLaserEvents() {
    }

    @FunctionalInterface
    public interface Expired {
        void onExpired(ServerLevel level, OrbitalLaserEntity laser);
    }
}
