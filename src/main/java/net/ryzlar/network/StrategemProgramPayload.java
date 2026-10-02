package net.ryzlar.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.ryzlar.LaserMod;

/** Client -> server: the player entered a stratagem code; program the ball in their hand with it. */
public record StrategemProgramPayload(Identifier strategem) implements CustomPacketPayload {

    public static final Type<StrategemProgramPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "stratagem_program"));

    public static final StreamCodec<FriendlyByteBuf, StrategemProgramPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeIdentifier(payload.strategem()),
            buf -> new StrategemProgramPayload(buf.readIdentifier())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
