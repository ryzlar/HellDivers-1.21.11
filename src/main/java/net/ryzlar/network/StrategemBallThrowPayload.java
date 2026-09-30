package net.ryzlar.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.ryzlar.LaserMod;

public record StrategemBallThrowPayload(float power) implements CustomPacketPayload {

    public static final Type<StrategemBallThrowPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "stratagem_throw"));

    public static final StreamCodec<FriendlyByteBuf, StrategemBallThrowPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeFloat(payload.power()),
            buf -> new StrategemBallThrowPayload(buf.readFloat())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

