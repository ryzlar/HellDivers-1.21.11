package net.ryzlar.laser;


import net.minecraft.core.BlockPos;

public class BeamData {

    public final float width;
    public final double x;
    public final double startY;
    public final double z;
    public final double endY;

    public final float r;
    public final float g;
    public final float b;

    public BeamData(double x, double startY, double z, double endY, float r, float g, float b, float width)
    {
        this.width = width;
        this.x = x;
        this.startY = startY;
        this.z = z;
        this.endY = endY;
        this.r = r;
        this.g = g;
        this.b = b;
    }

    public static BeamData calculateBeam(BlockPos pos, int color, int height, float width) {
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;

        float r = (float)((color >> 16) & 255) / 255.0F;
        float g = (float)((color >> 8) & 255) / 255.0F;
        float b = (float)(color & 255) / 255.0F;

        double startY = y;
        double endY = y + height;

        return new BeamData(x, startY, z, endY, r, g, b, width );

    }
}
