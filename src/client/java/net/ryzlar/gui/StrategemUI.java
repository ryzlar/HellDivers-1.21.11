package net.ryzlar.gui;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.ryzlar.sound.ModSounds;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.ryzlar.LaserMod;
import net.ryzlar.entities.StrategemBallPhysics;
import net.ryzlar.fx.DeviceFx;
import net.ryzlar.items.StrategemBallItem;
import net.ryzlar.items.effects.StrategemBall;
import net.ryzlar.keymapping.KeyMappings;
import net.ryzlar.strategem.DestructionLevel;
import net.ryzlar.strategem.Strategem;
import net.ryzlar.strategem.StrategemCooldowns;
import net.ryzlar.strategem.StrategemInput;
import net.ryzlar.strategem.Strategems;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Helldivers-style tactical HUD for the stratagem system (only while the Arm Piece is worn):
 * <ul>
 *     <li>a compact status chip (top left): menu key, the ball in hand and its readiness;</li>
 *     <li>the stratagem menu expanding out of it: page, emblems, destruction level, arrow-code input boxes,
 *     live input progress, cooldown bars, success/error feedback;</li>
 *     <li>call-in / programmed / ready / error toasts (top center, see {@link HudToasts});</li>
 *     <li>a throw-charge gauge under the crosshair while a ball is wound up.</li>
 * </ul>
 * All animation is time based, so it runs the same at any frame rate. Controls and logic live in
 * {@link StrategemInputHandler}; this class only presents them.
 */
public class StrategemUI implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
    }

    /** Set every tick from the menu key (only with the Arm Piece worn). Read by the input handler. */
    public static boolean menuOpen = false;
    /** 0..1 open animation of the menu. */
    public static float menuAnimation = 0.0f;

    // Palette
    private static final int YELLOW = DeviceFx.HD_YELLOW;
    private static final int PANEL = 0x0B0E12;
    private static final int PANEL_LIGHT = 0x1A1F26;
    private static final int TEXT = 0xF2F2F2;
    private static final int TEXT_DIM = 0x8C949C;
    private static final int RED = DeviceFx.DANGER_RED;
    private static final int GREEN = DeviceFx.READY_GREEN;

    // Layout
    private static final int X = 8;
    private static final int Y = 8;
    private static final int WIDTH = 168;
    private static final int CHIP_H = 26;
    private static final int HEADER_H = 14;
    private static final int ROW_H = 27;
    private static final int FOOTER_H = 14;
    private static final int BOX = 9;

    private static final Identifier ICON = Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "textures/icons/unfoc_strat.png");

    private static final float[] ROW_DIM = new float[Strategems.PAGE_SIZE];
    private static final float[] ROW_SHIFT = new float[Strategems.PAGE_SIZE];
    private static final Set<String> COOLING = new HashSet<>();
    private static double lastFrame = 0;
    /** 0..1 boot animation of the whole HUD when the Arm Piece is strapped on / taken off. */
    private static float chipAnimation = 0.0f;
    /** Global alpha multiplier applied by {@link #argb} while the device HUD fades in and out. */
    private static float fade = 1.0f;
    private static int lastTyped = 0;
    private static double typedAt = -10;

    // ---------------------------------------------------------------- per tick

    /** Toasts for cooldowns that just finished. */
    public static void tick(Minecraft mc) {
        if (mc.player == null) return;
        for (Strategem strategem : Strategems.all()) {
            String id = strategem.id().toString();
            if (ClientCooldowns.remainingTicks(strategem) > 0) {
                COOLING.add(id);
            } else if (COOLING.remove(id) && DeviceFx.wearingArmPiece()) {
                HudToasts.push("STRATAGEM READY", strategem.displayName().getString(), strategem.color(), GREEN);
                DeviceFx.playUi(ModSounds.STRATAGEM_READY, 1.0f, 0.5f);
            }
        }
    }

    // ---------------------------------------------------------------- render

    public static void render(GuiGraphics graphics, DeltaTracker delta, boolean wearingArmPiece) {
        double now = DeviceFx.now();
        float dt = lastFrame == 0 ? 0 : (float) Math.min(0.1, now - lastFrame);
        lastFrame = now;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        float target = menuOpen && wearingArmPiece ? 1.0f : 0.0f;
        menuAnimation += (target - menuAnimation) * (1.0f - (float) Math.exp(-dt * (target > menuAnimation ? 14 : 20)));
        if (Math.abs(target - menuAnimation) < 0.002f) menuAnimation = target;

        float chipTarget = wearingArmPiece ? 1.0f : 0.0f;
        chipAnimation += (chipTarget - chipAnimation) * (1.0f - (float) Math.exp(-dt * 9));
        if (Math.abs(chipTarget - chipAnimation) < 0.002f) chipAnimation = chipTarget;

        int typed = StrategemInputHandler.INPUT.size();
        if (typed > lastTyped) typedAt = now;
        lastTyped = typed;

        if (chipAnimation > 0.01f) {
            // the device HUD slides in from the screen edge and fades up, instead of popping in
            fade = ease(chipAnimation);
            graphics.pose().pushMatrix();
            graphics.pose().translate(-(1.0f - fade) * 14.0f, 0.0f);
            renderChip(graphics, mc, now);
            if (menuAnimation > 0.01f) renderMenu(graphics, mc, now, dt);
            graphics.pose().popMatrix();
            fade = 1.0f;
        }
        renderChargeGauge(graphics, mc, now);
        HudToasts.render(graphics, mc.font, now);
    }

    // ---------------------------------------------------------------- status chip

    private static void renderChip(GuiGraphics g, Minecraft mc, double now) {
        Font font = mc.font;
        int x = X, y = Y;
        panel(g, x, y, WIDTH, CHIP_H, 0.78f);
        float error = (float) Mth.clamp(1.0 - DeviceFx.sinceError() / 0.45, 0.0, 1.0);
        int accent = lerpColor(YELLOW, RED, error);
        g.fill(x, y, x + 2, y + CHIP_H, argb(1, accent));
        corners(g, x - 1, y - 1, WIDTH + 2, CHIP_H + 2, argb(0.55f + 0.45f * error, accent));
        if (chipAnimation < 0.995f) {
            // boot scan across the chip while the device powers up
            int sweep = x + (int) (WIDTH * chipAnimation);
            g.fill(Math.max(x, sweep - 18), y, sweep, y + CHIP_H, argb(0.14f, 0xFFFFFF));
            g.fill(Math.max(x, sweep - 1), y, sweep, y + CHIP_H, argb(0.7f, YELLOW));
        }

        g.blit(RenderPipelines.GUI_TEXTURED, ICON, x + 6, y + 4, 0, 0, 9, 9, 11, 11, 11, 11);
        small(g, font, "STRATAGEMS", x + 18, y + 5, argb(1, YELLOW));
        String key = "[" + KeyMappings.STRATEGEM_MENU_KEY.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT) + "]";
        small(g, font, key, x + WIDTH - 6 - (int) (font.width(key) * 0.75f), y + 5, argb(0.8f, TEXT_DIM));

        // Ball status
        LocalPlayer player = mc.player;
        ItemStack held = player.getMainHandItem();
        int lineY = y + 14;
        if (held.getItem() instanceof StrategemBallItem) {
            Strategem strategem = StrategemBallItem.getStrategem(held);
            if (strategem == null) {
                diamond(g, x + 9, lineY + 4, 3, argb(0.8f, DeviceFx.EMPTY_GREY));
                g.drawString(font, "Ball: empty", x + 16, lineY, argb(0.9f, TEXT_DIM), false);
            } else {
                g.blit(RenderPipelines.GUI_TEXTURED, glyph(strategem), x + 5, lineY, 0, 0, 9, 9, 16, 16, 16, 16, argb(1, strategem.color()));
                String name = font.plainSubstrByWidth(strategem.displayName().getString(), WIDTH - 70);
                g.drawString(font, name, x + 16, lineY, argb(1, TEXT), false);
                float upload = DeviceFx.uploadProgress();
                if (upload < 1.0f && StrategemInputHandler.feedbackStrategem == strategem) {
                    // data transfer into the ball in hand
                    String pct = "UPLOAD " + (int) (upload * 100) + "%";
                    small(g, font, pct, x + WIDTH - 6 - (int) (font.width(pct) * 0.75f), lineY + 1, argb(1, strategem.color()));
                    g.fill(x + 2, y + CHIP_H - 2, x + 2 + (int) ((WIDTH - 2) * upload), y + CHIP_H - 1, argb(0.9f, strategem.color()));
                } else {
                    readiness(g, font, strategem, x + WIDTH - 6, lineY, now);
                    long cooldown = ClientCooldowns.remainingTicks(strategem);
                    if (cooldown > 0) {
                        float progress = 1.0f - cooldown / (float) strategem.level().cooldownTicks();
                        g.fill(x + 2, y + CHIP_H - 2, x + WIDTH, y + CHIP_H - 1, argb(0.2f, TEXT));
                        g.fill(x + 2, y + CHIP_H - 2, x + 2 + (int) ((WIDTH - 2) * progress), y + CHIP_H - 1, argb(0.8f, RED));
                    }
                }
            }
        } else {
            diamond(g, x + 9, lineY + 4, 3, argb(0.4f, TEXT_DIM));
            g.drawString(font, "No ball in hand", x + 16, lineY, argb(0.7f, TEXT_DIM), false);
        }
    }

    private static void readiness(GuiGraphics g, Font font, Strategem strategem, int right, int y, double now) {
        long cooldown = ClientCooldowns.remainingTicks(strategem);
        String text = cooldown > 0 ? StrategemCooldowns.format(cooldown) : "READY";
        int color = cooldown > 0 ? RED : GREEN;
        float alpha = cooldown > 0 ? 1 : 0.75f + 0.25f * Mth.sin((float) now * 3);
        g.drawString(font, text, right - font.width(text), y, argb(alpha, color), false);
    }

    // ---------------------------------------------------------------- menu

    private static void renderMenu(GuiGraphics g, Minecraft mc, double now, float dt) {
        Font font = mc.font;
        List<Strategem> page = StrategemInputHandler.currentPage();
        float open = ease(menuAnimation);
        int fullHeight = HEADER_H + Strategems.PAGE_SIZE * ROW_H + FOOTER_H;
        int height = Math.max(2, (int) (fullHeight * open));
        int x = X;
        int y = Y + CHIP_H + 3;

        // Wrong code: the whole panel jolts and flushes red
        StrategemInputHandler.Feedback feedback = StrategemInputHandler.feedback;
        float fb = StrategemInputHandler.feedbackTicks / (float) StrategemInputHandler.FEEDBACK_TICKS;
        if (feedback == StrategemInputHandler.Feedback.WRONG) {
            x += (int) (Mth.sin(StrategemInputHandler.feedbackTicks * 2.4f) * 3 * fb);
        }

        panel(g, x, y, WIDTH, height, 0.82f);
        g.fill(x, y, x + 2, y + height, argb(1, YELLOW));
        if (feedback == StrategemInputHandler.Feedback.WRONG || feedback == StrategemInputHandler.Feedback.NO_BALL
                || feedback == StrategemInputHandler.Feedback.COOLDOWN) {
            g.fill(x, y, x + WIDTH, y + height, argb(0.25f * fb, RED));
        }
        if (open < 0.98f) {
            // the leading edge of the panel sweeping open
            g.fill(x, y + height - 1, x + WIDTH, y + height, argb(0.9f, YELLOW));
        }
        corners(g, x - 1, y - 1, WIDTH + 2, height + 2, argb(0.55f, YELLOW));
        if (open < 0.35f) return;

        g.enableScissor(x, y, x + WIDTH, y + height);
        float content = Mth.clamp((open - 0.35f) / 0.65f, 0.0f, 1.0f);

        // Header: page + paging hint
        int pages = Strategems.pageCount();
        small(g, font, "PAGE " + (StrategemInputHandler.page + 1) + "/" + pages, x + 8, y + 4, argb(content, TEXT_DIM));
        if (pages > 1) {
            String hint = "SCROLL / [ ] : PAGE";
            small(g, font, hint, x + WIDTH - 6 - (int) (font.width(hint) * 0.75f), y + 4, argb(0.6f * content, TEXT_DIM));
        }
        g.fill(x + 6, y + HEADER_H - 2, x + WIDTH - 6, y + HEADER_H - 1, argb(0.18f * content, TEXT));

        int rowY = y + HEADER_H;
        for (int i = 0; i < Strategems.PAGE_SIZE; i++) {
            // rows cascade in one after another
            float rowIn = Mth.clamp((menuAnimation - 0.4f - i * 0.08f) / 0.3f, 0.0f, 1.0f);
            if (i < page.size() && rowIn > 0) {
                renderRow(g, font, page.get(i), i, x, rowY, rowIn * content, now, dt);
            }
            rowY += ROW_H;
        }
        renderFooter(g, font, mc, x, y + HEADER_H + Strategems.PAGE_SIZE * ROW_H, content, now);
        double since = DeviceFx.sinceMenuOpened();
        float sweepT = (float) ((since - 0.15) / 0.45);
        float sweepAlpha = 0.5f;
        if (sweepT > 1.0f) {
            sweepT = (float) (((since - 0.6) % 4.0) / 0.9); // idle: a faint scan every few seconds
            sweepAlpha = 0.12f;
        }
        if (sweepT >= 0.0f && sweepT <= 1.0f && StrategemInputHandler.isMenuActive()) {
            int sy = y + (int) (height * sweepT);
            g.fillGradient(x + 2, sy - 8, x + WIDTH, sy, argb(0.0f, YELLOW), argb(sweepAlpha * 0.3f, YELLOW));
            g.fill(x + 2, sy, x + WIDTH, sy + 1, argb(sweepAlpha, YELLOW));
        }
        g.disableScissor();
    }

    private static void renderRow(GuiGraphics g, Font font, Strategem s, int index, int px, int py, float alpha, double now, float dt) {
        boolean candidate = StrategemInputHandler.isCandidate(s);
        long cooldown = ClientCooldowns.remainingTicks(s);
        int typed = candidate ? StrategemInputHandler.INPUT.size() : 0;
        boolean focused = candidate && typed > 0 && cooldown == 0;

        // Rows that no longer match fade back and slide aside, smoothly
        float dimTarget = candidate && cooldown == 0 ? 1.0f : 0.38f;
        float shiftTarget = candidate ? 0.0f : -4.0f;
        float k = 1.0f - (float) Math.exp(-dt * 16);
        ROW_DIM[index] += (dimTarget - ROW_DIM[index]) * k;
        ROW_SHIFT[index] += (shiftTarget - ROW_SHIFT[index]) * k;
        float a = alpha * ROW_DIM[index];
        int x = px + 6 + Math.round(ROW_SHIFT[index]);
        int y = py + 2;

        // Programmed: the row flashes white into the stratagem color
        boolean flash = StrategemInputHandler.feedback == StrategemInputHandler.Feedback.PROGRAMMED
                && StrategemInputHandler.feedbackStrategem == s;
        float fb = StrategemInputHandler.feedbackTicks / (float) StrategemInputHandler.FEEDBACK_TICKS;
        if (flash) {
            int c = lerpColor(0xFFFFFF, s.color(), 1.0f - fb);
            g.fill(px + 3, py, px + WIDTH - 3, py + ROW_H - 1, argb(0.45f * fb * alpha, c));
        } else if (focused) {
            g.fill(px + 3, py, px + WIDTH - 3, py + ROW_H - 1, argb(0.12f * alpha, YELLOW));
            g.fill(px + 3, py, px + 4, py + ROW_H - 1, argb(alpha, YELLOW));
        }

        emblem(g, s, x, y, a, cooldown > 0);

        // Name + destruction level pips
        int tx = x + 25;
        String name = font.plainSubstrByWidth(s.displayName().getString(), WIDTH - 72);
        g.drawString(font, name, tx, y + 1, argb(a, TEXT), false);
        levelPips(g, s.level(), px + WIDTH - 8, y + 2, a);

        int lineY = y + 12;
        if (cooldown > 0) {
            small(g, font, "COOLDOWN", tx, lineY + 1, argb(alpha * 0.9f, RED));
            String time = StrategemCooldowns.format(cooldown);
            g.drawString(font, time, px + WIDTH - 8 - font.width(time), lineY, argb(alpha, RED), false);
            float progress = 1.0f - cooldown / (float) s.level().cooldownTicks();
            int barX0 = tx, barX1 = px + WIDTH - 8, barY = lineY + 10;
            g.fill(barX0, barY, barX1, barY + 1, argb(0.25f * alpha, TEXT));
            g.fill(barX0, barY, barX0 + (int) ((barX1 - barX0) * progress), barY + 1, argb(0.9f * alpha, RED));
            return;
        }

        // Arrow code: entered inputs fill in yellow, the next one blinks
        List<StrategemInput> code = s.code();
        for (int i = 0; i < code.size(); i++) {
            int bx = tx + i * (BOX + 2);
            int state;
            if (flash) state = 3;
            else if (i < typed) state = 2;
            else if (focused && i == typed) state = ((int) (now * 4)) % 2 == 0 ? 1 : 0;
            else state = 0;
            arrowBox(g, bx, lineY, code.get(i), state, a);
            double age = now - typedAt;
            if (focused && i == typed - 1 && age < 0.18) {
                // the key press "lands" in its box: a quick white ring that grows and fades
                float pop = (float) (age / 0.18);
                int grow = 1 + (int) (pop * 2);
                outline(g, bx - grow, lineY - grow, BOX + grow * 2, BOX + grow * 2, argb((1.0f - pop) * a, 0xFFFFFF));
            }
        }
    }

    /** Stratagem emblem: tile in its color with a diamond core and destruction-level ticks. */
    private static void emblem(GuiGraphics g, Strategem s, int x, int y, float a, boolean cooling) {
        int color = cooling ? lerpColor(s.color(), 0x555555, 0.7f) : s.color();
        g.fill(x, y, x + 20, y + 20, argb(a, PANEL_LIGHT));
        outline(g, x, y, 20, 20, argb(a, color));
        g.fill(x + 1, y + 1, x + 19, y + 3, argb(0.5f * a, color));
        g.blit(RenderPipelines.GUI_TEXTURED, glyph(s), x + 2, y + 1, 0, 0, 16, 16, 16, 16, argb(a, color));
        int tier = s.level().tier();
        for (int i = 0; i < tier; i++) {
            g.fill(x + 3 + i * 3, y + 16, x + 5 + i * 3, y + 18, argb(a, TEXT));
        }
    }

    private static final Set<String> GLYPHS = Set.of("void_rift", "meteor_strike", "orbital_lightning",
            "volcanic_eruption", "nuclear_strike");

    /** The stratagem's glyph (the same one shown on the ball's display windows). */
    static Identifier glyph(Strategem s) {
        String name = GLYPHS.contains(s.id().getPath()) ? s.id().getPath() : "generic";
        return Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "textures/gui/glyph/" + name + ".png");
    }

    private static void levelPips(GuiGraphics g, DestructionLevel level, int right, int y, float a) {
        for (int i = 0; i < 5; i++) {
            int x = right - (5 - i) * 4;
            boolean on = i < level.tier();
            g.fill(x, y, x + 3, y + 5, argb(on ? a : 0.25f * a, on ? level.color() : TEXT_DIM));
        }
    }

    private static void renderFooter(GuiGraphics g, Font font, Minecraft mc, int x, int y, float a, double now) {
        g.fill(x + 6, y + 1, x + WIDTH - 6, y + 2, argb(0.18f * a, TEXT));
        String text;
        int color;
        switch (StrategemInputHandler.feedback) {
            case PROGRAMMED -> {
                Strategem s = StrategemInputHandler.feedbackStrategem;
                color = s != null ? s.color() : GREEN;
                float upload = DeviceFx.uploadProgress();
                if (upload < 1.0f) {
                    // segmented upload bar while the code transfers into the ball
                    text = "UPLOADING";
                    int segments = 10, bx = x + 8 + (int) (font.width(text) * 0.75f) + 6;
                    int lit = (int) (upload * segments);
                    for (int i = 0; i < segments; i++) {
                        g.fill(bx + i * 5, y + 6, bx + i * 5 + 4, y + 10, argb(a * (i < lit ? 0.95f : 0.2f), i < lit ? color : TEXT_DIM));
                    }
                } else {
                    text = "BALL PROGRAMMED";
                }
            }
            case NO_BALL -> {
                text = "NO STRATEGEM BALL IN HAND";
                color = RED;
            }
            case COOLDOWN -> {
                text = "STRATAGEM ON COOLDOWN";
                color = RED;
            }
            case WRONG -> {
                text = "INVALID INPUT";
                color = RED;
            }
            default -> {
                boolean holding = mc.player.getMainHandItem().getItem() instanceof StrategemBallItem;
                text = holding ? "ENTER CODE TO PROGRAM BALL" : "HOLD A STRATEGEM BALL";
                color = holding ? TEXT_DIM : DeviceFx.WARN_AMBER;
            }
        }
        float pulse = StrategemInputHandler.feedback == StrategemInputHandler.Feedback.NONE
                ? 0.85f : 0.75f + 0.25f * Mth.sin((float) now * 12);
        small(g, font, text, x + 8, y + 5, argb(a * pulse, color));
    }

    // ---------------------------------------------------------------- throw gauge

    private static void renderChargeGauge(GuiGraphics g, Minecraft mc, double now) {
        LocalPlayer player = mc.player;
        if (!player.isUsingItem() || !(player.getUseItem().getItem() instanceof StrategemBallItem)) return;
        Strategem s = StrategemBallItem.getStrategem(player.getUseItem());
        int color = s != null ? s.color() : DeviceFx.EMPTY_GREY;
        float power = Math.min(1.0f, player.getTicksUsingItem() / (float) StrategemBall.MAX_CHARGE);
        float curve = StrategemBallPhysics.chargeCurve(power);

        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        int segments = 12, segW = 5, gap = 1;
        int total = segments * (segW + gap) - gap;
        int x0 = cx - total / 2, y0 = cy + 14;
        g.fill(x0 - 3, y0 - 3, x0 + total + 3, y0 + 6, argb(0.55f, PANEL));
        int lit = Math.round(curve * segments);
        boolean full = curve >= 0.999f;
        for (int i = 0; i < segments; i++) {
            boolean on = i < lit;
            int c = full ? lerpColor(color, 0xFFFFFF, 0.5f + 0.5f * Mth.sin((float) now * 10)) : color;
            g.fill(x0 + i * (segW + gap), y0, x0 + i * (segW + gap) + segW, y0 + 3, argb(on ? 0.95f : 0.2f, on ? c : TEXT_DIM));
        }
        String label = s != null ? s.displayName().getString().toUpperCase(Locale.ROOT) : "EMPTY BALL";
        small(g, mc.font, label, cx - (int) (mc.font.width(label) * 0.75f / 2), y0 + 7, argb(0.85f, s != null ? color : TEXT_DIM));

        // Reticle brackets tightening with charge
        int r = 9 - Math.round(curve * 3);
        int c = argb(0.7f, color);
        bracket(g, cx - r - 3, cy - r, 1, 1, c);
        bracket(g, cx + r + 3, cy - r, -1, 1, c);
        bracket(g, cx - r - 3, cy + r, 1, -1, c);
        bracket(g, cx + r + 3, cy + r, -1, -1, c);
    }

    private static void bracket(GuiGraphics g, int x, int y, int dx, int dy, int color) {
        g.fill(Math.min(x, x + dx * 4), Math.min(y, y + dy * 2), Math.max(x, x + dx * 4), Math.max(y, y + dy * 2), color);
        g.fill(Math.min(x, x + dx * 2), Math.min(y, y + dy * 4), Math.max(x, x + dx * 2), Math.max(y, y + dy * 4), color);
    }

    // ---------------------------------------------------------------- drawing helpers

    /** Arrow input box. state: 0 pending, 1 next (blink), 2 entered, 3 success flash. */
    private static void arrowBox(GuiGraphics g, int x, int y, StrategemInput input, int state, float a) {
        int bg, fg, border;
        switch (state) {
            case 3 -> {
                bg = 0xFFFFFF;
                fg = PANEL;
                border = 0xFFFFFF;
            }
            case 2 -> {
                bg = YELLOW;
                fg = PANEL;
                border = YELLOW;
            }
            case 1 -> {
                bg = PANEL_LIGHT;
                fg = YELLOW;
                border = YELLOW;
            }
            default -> {
                bg = PANEL_LIGHT;
                fg = TEXT_DIM;
                border = 0x3A424A;
            }
        }
        g.fill(x, y, x + BOX, y + BOX, argb(a, bg));
        outline(g, x, y, BOX, BOX, argb(a, border));
        arrow(g, x + 1, y + 1, input, argb(a, fg));
    }

    private static final String[] ARROW_UP = {
            "...#...",
            "..###..",
            ".#####.",
            "#######",
            "..###..",
            "..###..",
            "..###.."};

    /** Crisp 7x7 pixel arrow, rotated per direction. */
    private static void arrow(GuiGraphics g, int x, int y, StrategemInput input, int color) {
        for (int row = 0; row < 7; row++) {
            for (int col = 0; col < 7; col++) {
                if (ARROW_UP[row].charAt(col) != '#') continue;
                int px, py;
                switch (input) {
                    case DOWN -> {
                        px = col;
                        py = 6 - row;
                    }
                    case LEFT -> {
                        px = row;
                        py = col;
                    }
                    case RIGHT -> {
                        px = 6 - row;
                        py = col;
                    }
                    default -> {
                        px = col;
                        py = row;
                    }
                }
                g.fill(x + px, y + py, x + px + 1, y + py + 1, color);
            }
        }
    }

    static void panel(GuiGraphics g, int x, int y, int w, int h, float alpha) {
        g.fill(x, y, x + w, y + h, argb(alpha, PANEL));
        g.fillGradient(x, y, x + w, y + Math.min(h, 10), argb(0.08f, 0xFFFFFF), argb(0.0f, 0xFFFFFF));
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** Tactical corner brackets. */
    static void corners(GuiGraphics g, int x, int y, int w, int h, int color) {
        int l = 5;
        g.fill(x, y, x + l, y + 1, color);
        g.fill(x, y, x + 1, y + l, color);
        g.fill(x + w - l, y, x + w, y + 1, color);
        g.fill(x + w - 1, y, x + w, y + l, color);
        g.fill(x, y + h - 1, x + l, y + h, color);
        g.fill(x, y + h - l, x + 1, y + h, color);
        g.fill(x + w - l, y + h - 1, x + w, y + h, color);
        g.fill(x + w - 1, y + h - l, x + w, y + h, color);
    }

    private static void diamond(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int half = r - Math.abs(dy);
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    /** Small caps label (75% scale). */
    static void small(GuiGraphics g, Font font, String text, int x, int y, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(0.75f, 0.75f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popMatrix();
    }

    private static float ease(float t) {
        return 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
    }

    static int argb(float alpha, int rgb) {
        return ((int) (Mth.clamp(alpha * fade, 0.0f, 1.0f) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    static int lerpColor(int from, int to, float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        int r = (int) Mth.lerp(t, (from >> 16) & 255, (to >> 16) & 255);
        int gg = (int) Mth.lerp(t, (from >> 8) & 255, (to >> 8) & 255);
        int b = (int) Mth.lerp(t, from & 255, to & 255);
        return (r << 16) | (gg << 8) | b;
    }
}
