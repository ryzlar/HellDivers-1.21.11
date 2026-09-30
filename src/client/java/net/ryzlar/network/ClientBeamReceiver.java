package net.ryzlar.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ryzlar.ModClient.ModBeams;

public class ClientBeamReceiver {

    public static void registerArmPiece() {
        ClientPlayNetworking.registerGlobalReceiver(ArmPiecePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                ModBeams.HAS_ARM_PIECE = payload.hasArmPiece();
            });
        });
    }
}