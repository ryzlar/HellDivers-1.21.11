package net.ryzlar.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Short tactical notifications at the top center: slide down, hold, fade. At most three at a time;
 * a new identical toast refreshes the old one instead of stacking.
 */
public final class HudToasts {

    private static final double LIFETIME = 2.6;
    private static final int MAX = 3;
    private static final int WIDTH_PAD = 14;

    private record Toast(String title, String subtitle, int accent, int titleColor, double born) {
    }

    private static final List<Toast> TOASTS = new ArrayList<>();

    private HudToasts() {
    }

    public static void push(String title, String subtitle, int accent, int titleColor) {
        double now = net.ryzlar.fx.DeviceFx.now();
        TOASTS.removeIf(t -> t.title().equals(title) && t.subtitle().equals(subtitle));
        TOASTS.add(0, new Toast(title, subtitle, accent, titleColor, now));
        while (TOASTS.size() > MAX) TOASTS.remove(TOASTS.size() - 1);
    }

    public static void render(GuiGraphics g, Font font, double now) {
        TOASTS.removeIf(t -> now - t.born() > LIFETIME);
        int y = 34;
        for (Toast toast : TOASTS) {
            double age = now - toast.born();
            float in = (float) Mth.clamp(age / 0.18, 0.0, 1.0);
            float out = (float) Mth.clamp((LIFETIME - age) / 0.4, 0.0, 1.0);
            float a = in * out;
            in = 1.0f - (1.0f - in) * (1.0f - in);

            String sub = toast.subtitle().toUpperCase(Locale.ROOT);
            int width = Math.max((int) (font.width(toast.title()) * 0.75f), font.width(sub)) + WIDTH_PAD * 2;
            int height = sub.isEmpty() ? 14 : 23;
            int x = g.guiWidth() / 2 - width / 2;
            int ty = y - Math.round((1.0f - in) * 8);

            StrategemUI.panel(g, x, ty, width, height, 0.8f * a);
            g.fill(x, ty, x + 2, ty + height, StrategemUI.argb(a, toast.accent()));
            StrategemUI.corners(g, x - 1, ty - 1, width + 2, height + 2, StrategemUI.argb(0.5f * a, toast.accent()));
            // a quick bright sweep across when it appears
            if (age < 0.25) {
                int sweep = x + (int) (width * (age / 0.25));
                g.fill(Math.max(x, sweep - 10), ty, Math.min(x + width, sweep), ty + height, StrategemUI.argb(0.18f * a, 0xFFFFFF));
            }
            StrategemUI.small(g, font, toast.title(), x + WIDTH_PAD, ty + 4, StrategemUI.argb(a, toast.titleColor()));
            if (!sub.isEmpty()) {
                g.drawString(font, sub, x + WIDTH_PAD, ty + 12, StrategemUI.argb(a, toast.accent()), false);
            }
            y += height + 4;
        }
    }
}
