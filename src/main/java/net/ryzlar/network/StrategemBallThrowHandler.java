package net.ryzlar.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.ryzlar.items.effects.StrategemBall;

public class StrategemBallThrowHandler {
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(StrategemBallThrowPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            System.out.println("[StrategemBall] Server received throw packet with power: " + payload.power());
            StrategemBall.throwBall(player, payload.power());
        });
    }
}
