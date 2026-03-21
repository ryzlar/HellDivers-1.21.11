package net.ryzlar.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.ryzlar.LaserMod;

public record ArmPiecePayload(boolean hasArmPiece) implements CustomPacketPayload {

    public static final Type<ArmPiecePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "armpiece_data"));

    public static final StreamCodec<FriendlyByteBuf, ArmPiecePayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBoolean(payload.hasArmPiece()),
            buf -> new ArmPiecePayload(buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}