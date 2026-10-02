package net.ryzlar.strategem;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.ryzlar.LaserMod;
import net.ryzlar.network.StrategemCooldownPayload;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-player stratagem cooldowns, stored on the player (survives relogs, restarts and death)
 * as the game time at which each stratagem is ready again. Synced to the owning client for the menu.
 */
public final class StrategemCooldowns {

    private static final AttachmentType<Map<Identifier, Long>> READY_AT = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "strategem_cooldowns"),
            builder -> builder
                    .persistent(Codec.unboundedMap(Identifier.CODEC, Codec.LONG))
                    .initializer(HashMap::new)
                    .copyOnDeath());

    private StrategemCooldowns() {
    }

    public static void initialize() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.getPlayer()));
    }

    public static long remainingTicks(ServerPlayer player, Strategem strategem) {
        Long readyAt = player.getAttachedOrCreate(READY_AT).get(strategem.id());
        return readyAt == null ? 0L : Math.max(0L, readyAt - player.level().getGameTime());
    }

    public static boolean isOnCooldown(ServerPlayer player, Strategem strategem) {
        return remainingTicks(player, strategem) > 0;
    }

    public static void start(ServerPlayer player, Strategem strategem) {
        Map<Identifier, Long> map = new HashMap<>(player.getAttachedOrCreate(READY_AT));
        map.put(strategem.id(), player.level().getGameTime() + strategem.level().cooldownTicks());
        player.setAttached(READY_AT, map);
        sync(player);
    }

    public static void reset(ServerPlayer player) {
        player.setAttached(READY_AT, new HashMap<>());
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        Map<Identifier, Long> remaining = new HashMap<>();
        for (Strategem strategem : Strategems.all()) {
            long ticks = remainingTicks(player, strategem);
            if (ticks > 0) remaining.put(strategem.id(), ticks);
        }
        ServerPlayNetworking.send(player, new StrategemCooldownPayload(remaining));
    }

    /** "4:05" style time for messages and the menu. */
    public static String format(long ticks) {
        long seconds = (ticks + 19) / 20;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
