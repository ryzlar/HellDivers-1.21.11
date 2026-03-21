package net.ryzlar.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.ryzlar.LaserMod;
import net.ryzlar.laser.BeamData;

import java.util.ArrayList;
import java.util.List;

public record BeamPayload(List<BeamData> beams) implements CustomPacketPayload {

    public static final Type<BeamPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "beam_data"));

    public static final StreamCodec<FriendlyByteBuf, BeamPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.beams().size());
                for (BeamData beam : payload.beams()) {
                    BeamDataSerializer.write(buf, beam);
                }
            },
            buf -> {
                int size = buf.readInt();
                List<BeamData> beams = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    beams.add(BeamDataSerializer.read(buf));
                }
                return new BeamPayload(beams);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}