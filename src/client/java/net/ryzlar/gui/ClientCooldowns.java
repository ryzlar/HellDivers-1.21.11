package net.ryzlar.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.ryzlar.network.StrategemCooldownPayload;
import net.ryzlar.strategem.Strategem;

import java.util.HashMap;
import java.util.Map;

/** Client copy of this player's stratagem cooldowns, for the menu. The server stays authoritative. */
public final class ClientCooldowns {

    private static final Map<Identifier, Long> READY_AT = new HashMap<>();

    private ClientCooldowns() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(StrategemCooldownPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    READY_AT.clear();
                    long now = now();
                    payload.remainingTicks().forEach((id, ticks) -> READY_AT.put(id, now + ticks));
                }));
    }

    public static long remainingTicks(Strategem strategem) {
        Long readyAt = READY_AT.get(strategem.id());
        return readyAt == null ? 0L : Math.max(0L, readyAt - now());
    }

    private static long now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.getGameTime() : 0L;
    }
}
