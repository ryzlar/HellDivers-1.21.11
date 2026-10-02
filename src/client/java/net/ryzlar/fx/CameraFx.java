package net.ryzlar.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Reusable client-side camera/screen effects for strikes: shake, flash, FOV pulse, tint and vignette.
 *
 * <p>Two kinds of input: impulses ({@link #shake}, {@link #flash}, {@link #fovKick}) that decay on their own,
 * and sustained values ({@link #rumble}, {@link #tint}, {@link #fovSqueeze}) that a visual sets every tick
 * while it wants them; the strongest request of the tick wins and they ease out when no longer requested.
 * The {@code ...At} variants scale the effect by the player's distance to the source.
 */
public final class CameraFx {

    private static final float MAX_YAW = 6.0f;    // degrees at full shake
    private static final float MAX_PITCH = 4.5f;

    // Impulses
    private static float trauma;
    private static float flashAlpha;
    private static int flashColor = 0xFFFFFF;
    private static float flashDecay = 0.05f;
    private static float fovKick;

    // Sustained (requested this tick -> eased current)
    private static float rumbleRequest, rumble;
    private static float tintRequest, tint;
    private static int tintColorRequest, tintColor;
    private static float squeezeRequest, squeeze;

    private static long ticks;

    private CameraFx() {
    }

    // ---------------------------------------------------------------- input

    /** Adds a shake impulse (0..1+, ~1 is violent). */
    public static void shake(float amount) {
        trauma = Math.min(1.4f, trauma + amount);
    }

    public static void shakeAt(Vec3 source, float amount, float radius) {
        shake(amount * proximity(source, radius));
    }

    /** Full-screen flash of {@code rgb} at {@code alpha}, fading out over {@code ticks}. */
    public static void flash(int rgb, float alpha, int ticks) {
        if (alpha <= flashAlpha) return;
        flashColor = rgb;
        flashAlpha = Math.min(1.0f, alpha);
        flashDecay = flashAlpha / Math.max(1, ticks);
    }

    public static void flashAt(Vec3 source, int rgb, float alpha, float radius, int ticks) {
        float p = proximity(source, radius);
        if (p > 0) flash(rgb, alpha * (0.35f + 0.65f * p), ticks);
    }

    /** FOV punch: positive widens the view for a moment, negative narrows it. */
    public static void fovKick(float amount) {
        if (Math.abs(amount) > Math.abs(fovKick)) fovKick = amount;
    }

    public static void fovKickAt(Vec3 source, float amount, float radius) {
        fovKick(amount * proximity(source, radius));
    }

    /** Continuous shake for this tick (e.g. ground rumbling). */
    public static void rumble(float amount) {
        rumbleRequest = Math.max(rumbleRequest, amount);
    }

    public static void rumbleAt(Vec3 source, float amount, float radius) {
        rumble(amount * proximity(source, radius));
    }

    /** Screen tint for this tick (darkening, heat haze, void...). */
    public static void tint(int rgb, float alpha) {
        if (alpha > tintRequest) {
            tintRequest = alpha;
            tintColorRequest = rgb;
        }
    }

    /** Narrows the FOV for this tick (tension, suction). */
    public static void fovSqueeze(float amount) {
        squeezeRequest = Math.max(squeezeRequest, amount);
    }

    /**
     * 1 when the player can see {@code source}, {@code occluded} when terrain is in the way. A flash you are hiding
     * from behind a hill should not blind you as much as one you are staring at.
     */
    public static float visibility(Vec3 source, float occluded) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return 0.0f;
        Vec3 eye = mc.player.getEyePosition();
        var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, source,
                net.minecraft.world.level.ClipContext.Block.VISUAL, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
        boolean blocked = hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                && hit.getLocation().distanceToSqr(source) > 4.0;
        return blocked ? occluded : 1.0f;
    }

    /** A flash scaled by distance and by whether the player can see the source. */
    public static void flashSeen(Vec3 source, int rgb, float alpha, float radius, int ticks) {
        float p = proximity(source, radius);
        if (p > 0) flash(rgb, alpha * (0.35f + 0.65f * p) * visibility(source, 0.3f), ticks);
    }

    /** 1 at the source, 0 at {@code radius} and beyond. */
    public static float proximity(Vec3 source, float radius) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0.0f;
        double distance = mc.player.position().distanceTo(source);
        return Mth.clamp(1.0f - (float) (distance / radius), 0.0f, 1.0f);
    }

    // ---------------------------------------------------------------- update

    public static void tick() {
        ticks++;
        trauma = Math.max(0.0f, trauma - 0.035f - trauma * 0.06f);
        flashAlpha = Math.max(0.0f, flashAlpha - flashDecay);
        fovKick *= 0.86f;
        if (Math.abs(fovKick) < 0.001f) fovKick = 0.0f;

        rumble += (rumbleRequest - rumble) * 0.3f;
        tint += (tintRequest - tint) * 0.15f;
        if (tintRequest > 0) tintColor = tintColorRequest;
        squeeze += (squeezeRequest - squeeze) * 0.12f;
        rumbleRequest = 0;
        tintRequest = 0;
        squeezeRequest = 0;
    }

    /** Yaw/pitch offset in degrees for this frame (smooth noise, stronger with trauma²). */
    public static float[] shakeOffset(float partialTick) {
        float strength = Math.min(1.0f, trauma * trauma + rumble);
        if (strength <= 0.0001f) return null;
        float t = (ticks + partialTick) * 0.9f;
        float yaw = MAX_YAW * strength * noise(t, 0.0f);
        float pitch = MAX_PITCH * strength * noise(t, 37.0f);
        return new float[]{yaw, pitch};
    }

    public static float modifyFov(float fov) {
        return fov * (1.0f + fovKick) * (1.0f - squeeze);
    }

    /** Draws tint, vignette and flash over the whole screen. */
    public static void renderOverlay(GuiGraphics graphics) {
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        if (tint > 0.005f) {
            graphics.fill(0, 0, w, h, argb(tint * 0.6f, tintColor));
            int band = h / 3;
            graphics.fillGradient(0, 0, w, band, argb(tint, tintColor), argb(0, tintColor));
            graphics.fillGradient(0, h - band, w, h, argb(0, tintColor), argb(tint, tintColor));
        }
        if (flashAlpha > 0.005f) {
            graphics.fill(0, 0, w, h, argb(flashAlpha, flashColor));
        }
    }

    private static float noise(float t, float seed) {
        return (Mth.sin(t * 1.7f + seed) * 0.5f + Mth.sin(t * 3.1f + seed * 1.3f) * 0.3f
                + Mth.sin(t * 7.3f + seed * 2.1f) * 0.2f);
    }

    private static int argb(float alpha, int rgb) {
        return ((int) (Mth.clamp(alpha, 0.0f, 1.0f) * 255) << 24) | (rgb & 0xFFFFFF);
    }
}
