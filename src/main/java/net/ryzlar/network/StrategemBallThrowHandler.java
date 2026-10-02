package net.ryzlar.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.ryzlar.items.effects.StrategemBall;

public class StrategemBallThrowHandler {
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(StrategemBallThrowPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            StrategemBall.throwBall(player, payload.power());
        });
        ServerPlayNetworking.registerGlobalReceiver(StrategemProgramPayload.TYPE, (payload, context) ->
                StrategemBall.programBall(context.player(), payload.strategem()));
    }
}
