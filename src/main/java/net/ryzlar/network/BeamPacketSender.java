package net.ryzlar.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.ryzlar.laser.BeamData;

import java.util.List;

public class BeamPacketSender {

    public static void sendBeamsToClient(ServerPlayer player, List<BeamData> beams) {
        ServerPlayNetworking.send(player, new BeamPayload(beams));
    }
}