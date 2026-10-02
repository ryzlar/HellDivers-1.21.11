package net.ryzlar.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.ryzlar.gui.ClientCooldowns;
import net.ryzlar.gui.StrategemInputHandler;
import net.ryzlar.gui.StrategemUI;
import net.ryzlar.items.ModItems;
import net.ryzlar.items.StrategemBallItem;
import net.ryzlar.sound.ModSounds;
import net.ryzlar.strategem.Strategem;
import net.ryzlar.strategem.Strategems;
import org.jetbrains.annotations.Nullable;

/**
 * State and animation curves for the Helldivers equipment itself (Arm Piece screen + LEDs, Strategem Ball core).
 * The item models read these every frame through custom tint sources / properties (see DeviceModelProperties),
 * so the animations live on the actual item, in hand, on the ground and in the inventory.
 */
public final class DeviceFx {

    public static final int HD_YELLOW = 0xFFE14D;
    public static final int IDLE_TEAL = 0x3FBFA0;
    public static final int READY_GREEN = 0x6BFF8C;
    public static final int WARN_AMBER = 0xFFA526;
    public static final int DANGER_RED = 0xFF4040;
    public static final int EMPTY_GREY = 0x5A6168;

    private static boolean menuWasOpen;
    private static boolean wasWorn;
    private static double equippedAt = -10;
    private static boolean uploadPending;
    private static double menuOpenedAt = -10;
    private static double menuClosedAt = -10;
    private static double programmedAt = -10;
    private static int programmedColor = HD_YELLOW;
    private static double errorAt = -10;
    private static double throwAt = -10;
    private static float raise; // smoothed 0..1 for the first-person raise

    private DeviceFx() {
    }

    public static double now() {
        return System.nanoTime() / 1.0e9;
    }

    // ---------------------------------------------------------------- events

    /** Called every client tick: menu open/close edges (sounds) and the smoothed raise. */
    public static void tick(Minecraft mc) {
        // Strapping the Arm Piece on boots it (screen flicker + a quiet power-up)
        boolean worn = wearingArmPiece();
        if (worn && !wasWorn && mc.player != null && mc.player.tickCount > 20) {
            equippedAt = now();
            playUi(ModSounds.UPLINK_OPEN, 0.8f, 0.3f);
        }
        wasWorn = worn;

        boolean open = StrategemInputHandler.isMenuActive();
        if (open && !menuWasOpen) {
            menuOpenedAt = now();
            playUi(ModSounds.UPLINK_OPEN, 1.0f, 0.55f);
        } else if (!open && menuWasOpen) {
            menuClosedAt = now();
            playUi(ModSounds.UPLINK_CLOSE, 1.0f, 0.45f);
        }
        menuWasOpen = open;
        raise += ((open ? 1.0f : 0.0f) - raise) * 0.45f;
        // the data transfer into the ball ends with a confirm tone
        if (uploadPending && now() - programmedAt >= PROGRAM_SECONDS) {
            uploadPending = false;
            playUi(ModSounds.PROGRAM_COMPLETE, 1.0f, 0.55f);
        }
        if (Math.abs(raise) < 0.01f) raise = 0.0f;
    }

    public static void onProgrammed(Strategem strategem) {
        programmedAt = now();
        programmedColor = strategem.color();
        uploadPending = true;
        playUi(ModSounds.PROGRAM_UPLOAD, 1.0f, 0.5f);
    }

    public static final double PROGRAM_SECONDS = 0.9;

    /** 0..1 progress of the last data upload into a ball (1 when none is running). */
    public static float uploadProgress() {
        return (float) Mth.clamp((now() - programmedAt) / PROGRAM_SECONDS, 0.0, 1.0);
    }

    public static double sinceMenuOpened() {
        return now() - menuOpenedAt;
    }

    public static double sinceError() {
        return now() - errorAt;
    }

    public static double sinceEquipped() {
        return now() - equippedAt;
    }

    /** 0..1 while the ball in the local main hand is being programmed (the data scan sweeps up), else 0. */
    public static float programProgress(ItemStack stack, @Nullable Entity holder) {
        if (!isLocal(holder)) return 0.0f;
        double since = now() - programmedAt;
        if (since >= PROGRAM_SECONDS || stack != Minecraft.getInstance().player.getMainHandItem()) return 0.0f;
        return (float) Math.max(0.011, since / PROGRAM_SECONDS);
    }

    public static void onError() {
        errorAt = now();
    }

    public static void onThrow() {
        throwAt = now();
    }

    // ---------------------------------------------------------------- queries for the item models

    /** 0..1, how far the Arm Piece is raised toward the eye (first person). */
    public static float menuRaise(@Nullable Entity holder) {
        return isLocal(holder) ? raise : 0.0f;
    }

    /** Arm Piece screen: boots up when the menu opens, flashes on programming/errors, breathes when idle. */
    public static int armScreen(@Nullable Entity holder) {
        double t = now();
        if (!isLocal(holder)) return scale(IDLE_TEAL, 0.45f + 0.1f * Mth.sin((float) t * 1.6f));

        double sinceProgram = t - programmedAt;
        if (sinceProgram < 0.7) {
            float k = (float) (sinceProgram / 0.7);
            return FxDraw.lerpColor(0xFFFFFF, programmedColor, k);
        }
        if (t - errorAt < 0.45) {
            boolean on = ((int) ((t - errorAt) * 14)) % 2 == 0;
            return on ? DANGER_RED : scale(DANGER_RED, 0.3f);
        }
        double sinceEquip = t - equippedAt;
        if (sinceEquip < 0.6) {
            boolean on = ((int) (sinceEquip * 24)) % 4 != 1;
            return on ? scale(IDLE_TEAL, (float) (0.2 + sinceEquip)) : 0x000000;
        }
        if (StrategemInputHandler.isMenuActive()) {
            double boot = t - menuOpenedAt;
            if (boot < 0.3) {
                // power-on flicker
                boolean on = ((int) (boot * 30)) % 3 != 1;
                return on ? scale(HD_YELLOW, (float) (0.4 + boot * 2)) : scale(HD_YELLOW, 0.15f);
            }
            float scan = 0.88f + 0.12f * Mth.sin((float) t * 6.0f);
            return scale(HD_YELLOW, scan);
        }
        double sinceClose = t - menuClosedAt;
        if (sinceClose < 0.35) {
            return FxDraw.lerpColor(HD_YELLOW, scale(IDLE_TEAL, 0.5f), (float) (sinceClose / 0.35));
        }
        return scale(IDLE_TEAL, 0.45f + 0.12f * Mth.sin((float) t * 1.6f));
    }

    /** Arm Piece LEDs: green = something ready, amber blink = a ball is armed, red = everything cooling down. */
    public static int armLed(@Nullable Entity holder) {
        double t = now();
        if (!isLocal(holder)) return scale(READY_GREEN, 0.5f);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.isUsingItem() && player.getUseItem().getItem() instanceof StrategemBallItem) {
            return ((int) (t * 8)) % 2 == 0 ? WARN_AMBER : scale(WARN_AMBER, 0.25f);
        }
        boolean anyReady = false;
        for (Strategem strategem : Strategems.all()) {
            if (ClientCooldowns.remainingTicks(strategem) == 0) {
                anyReady = true;
                break;
            }
        }
        if (!anyReady) return scale(DANGER_RED, 0.5f + 0.3f * Mth.sin((float) t * 2.5f));
        // a short blink every two seconds, like a status light
        boolean blink = (t % 2.0) < 0.12;
        return blink ? READY_GREEN : scale(READY_GREEN, 0.55f);
    }

    /** Strategem Ball core: stratagem color when programmed, grey when empty; reacts to charging and cooldowns. */
    public static int ballCore(ItemStack stack, @Nullable Entity holder) {
        double t = now();
        Strategem strategem = StrategemBallItem.getStrategem(stack);
        if (strategem == null) {
            return scale(EMPTY_GREY, 0.55f + 0.1f * Mth.sin((float) t * 1.3f));
        }
        if (holder == null) {
            // thrown / dropped: an urgent beacon pulse
            return scale(strategem.color(), 0.6f + 0.4f * Math.abs(Mth.sin((float) t * 7.0f)));
        }
        if (isLocal(holder)) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (t - programmedAt < 0.7 && stack == player.getMainHandItem()) {
                return FxDraw.lerpColor(0xFFFFFF, strategem.color(), (float) ((t - programmedAt) / 0.7));
            }
            if (ClientCooldowns.remainingTicks(strategem) > 0) {
                boolean on = ((int) (t * 2)) % 2 == 0;
                return on ? scale(DANGER_RED, 0.7f) : scale(strategem.color(), 0.25f);
            }
            if (player.isUsingItem() && player.getUseItem() == stack) {
                float charge = Mth.clamp(player.getTicksUsingItem() / 72.0f, 0.0f, 1.0f);
                float speed = 4.0f + charge * 18.0f;
                return FxDraw.lerpColor(strategem.color(), 0xFFFFFF,
                        0.15f + 0.35f * charge * (0.5f + 0.5f * Mth.sin((float) t * speed)));
            }
        }
        return scale(strategem.color(), 0.65f + 0.35f * (0.5f + 0.5f * Mth.sin((float) t * 2.2f)));
    }

    // ---------------------------------------------------------------- helpers

    private static boolean isLocal(@Nullable Entity holder) {
        Minecraft mc = Minecraft.getInstance();
        return holder != null && holder == mc.player;
    }

    /** The local player is wearing the Arm Piece (off hand), so the device is "on". */
    public static boolean wearingArmPiece() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.getOffhandItem().getItem() == ModItems.ARM_PIECE;
    }

    public static int scale(int rgb, float factor) {
        factor = Mth.clamp(factor, 0.0f, 1.0f);
        int r = (int) (((rgb >> 16) & 255) * factor);
        int g = (int) (((rgb >> 8) & 255) * factor);
        int b = (int) ((rgb & 255) * factor);
        return (r << 16) | (g << 8) | b;
    }

    public static void playUi(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }
}
