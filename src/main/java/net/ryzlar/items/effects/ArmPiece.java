package net.ryzlar.items.effects;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.ryzlar.items.ModItems;
import net.ryzlar.network.ArmPiecePayload;

public class ArmPiece {

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                boolean hasArmPiece = player.getOffhandItem().getItem() == ModItems.ARM_PIECE;
                ServerPlayNetworking.send(player, new ArmPiecePayload(hasArmPiece));
            }
        });
    }
}