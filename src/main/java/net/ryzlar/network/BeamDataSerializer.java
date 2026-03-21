package net.ryzlar.network;

import net.minecraft.network.FriendlyByteBuf;
import net.ryzlar.laser.BeamData;

public class BeamDataSerializer {

    public static void write(FriendlyByteBuf buf, BeamData beam) {
        buf.writeDouble(beam.x);
        buf.writeDouble(beam.startY);
        buf.writeDouble(beam.z);
        buf.writeDouble(beam.endY);
        buf.writeFloat(beam.r);
        buf.writeFloat(beam.g);
        buf.writeFloat(beam.b);
        buf.writeFloat(beam.width);
    }

    public static BeamData read(FriendlyByteBuf buf) {
        double x = buf.readDouble();
        double startY = buf.readDouble();
        double z = buf.readDouble();
        double endY = buf.readDouble();
        float r = buf.readFloat();
        float g = buf.readFloat();
        float b = buf.readFloat();
        float width = buf.readFloat();
        return new BeamData(x, startY, z, endY, r, g, b, width);
    }
}