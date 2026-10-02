package net.ryzlar.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.ryzlar.LaserMod;

import java.util.HashMap;
import java.util.Map;

/** Server -> client: remaining cooldown (ticks) per stratagem that is cooling down. */
public record StrategemCooldownPayload(Map<Identifier, Long> remainingTicks) implements CustomPacketPayload {

    public static final Type<StrategemCooldownPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "stratagem_cooldowns"));

    public static final StreamCodec<FriendlyByteBuf, StrategemCooldownPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.remainingTicks().size());
                payload.remainingTicks().forEach((id, ticks) -> {
                    buf.writeIdentifier(id);
                    buf.writeVarLong(ticks);
                });
            },
            buf -> {
                int size = buf.readVarInt();
                Map<Identifier, Long> map = new HashMap<>();
                for (int i = 0; i < size; i++) {
                    map.put(buf.readIdentifier(), buf.readVarLong());
                }
                return new StrategemCooldownPayload(map);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
