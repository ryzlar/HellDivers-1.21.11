package net.ryzlar.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.ryzlar.items.StrategemBallItem;
import net.ryzlar.keymapping.KeyMappings;
import net.ryzlar.network.StrategemProgramPayload;
import net.ryzlar.fx.DeviceFx;
import net.ryzlar.sound.ModSounds;
import net.ryzlar.strategem.Strategem;
import net.ryzlar.strategem.StrategemCooldowns;
import net.ryzlar.strategem.StrategemInput;
import net.ryzlar.strategem.Strategems;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Arrow-code input while the stratagem menu is open. A complete code selects that stratagem (on the
 * current page) and, if a Strategem Ball is in the main hand, programs it (server validates and applies).
 * Also owns the menu page and the movement lock.
 */
public class StrategemInputHandler {

    public enum Feedback { NONE, PROGRAMMED, NO_BALL, WRONG, COOLDOWN }

    public static final int FEEDBACK_TICKS = 30;

    /** Arrows entered so far for the current code. */
    public static final List<StrategemInput> INPUT = new ArrayList<>();

    public static Feedback feedback = Feedback.NONE;
    public static int feedbackTicks = 0;
    @Nullable
    public static Strategem feedbackStrategem = null;

    /** Current menu page, 0-based. */
    public static int page = 0;

    private static boolean wasOpen = false;

    /** The menu is open and in control of the keyboard (no other screen on top). */
    public static boolean isMenuActive() {
        Minecraft mc = Minecraft.getInstance();
        return StrategemUI.menuOpen && mc.player != null && mc.screen == null;
    }

    public static boolean isMovementLocked() {
        return isMenuActive();
    }

    public static void tick(Minecraft client) {
        boolean open = isMenuActive();
        if (open != wasOpen) {
            INPUT.clear(); // every time the menu opens you start a fresh code
        }
        wasOpen = open;

        if (open) holdStill(client.player);

        if (feedbackTicks > 0 && --feedbackTicks == 0) {
            feedback = Feedback.NONE;
        }

        for (Map.Entry<StrategemInput, KeyMapping> entry : KeyMappings.STRATEGEM_INPUT_KEYS.entrySet()) {
            while (entry.getValue().consumeClick()) { // always drain, so presses don't queue up while closed
                if (open) onInput(client, entry.getKey());
            }
        }
        while (KeyMappings.STRATEGEM_PREV_PAGE.consumeClick()) {
            if (open) changePage(-1);
        }
        while (KeyMappings.STRATEGEM_NEXT_PAGE.consumeClick()) {
            if (open) changePage(1);
        }
    }

    /** Movement lock: inputs are blanked in KeyboardInputMixin, this stops leftover momentum and sprinting. */
    private static void holdStill(LocalPlayer player) {
        player.setSprinting(false);
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(0, Math.min(motion.y, 0.0), 0);
    }

    public static void changePage(int delta) {
        int pages = Strategems.pageCount();
        int next = Math.floorMod(page + delta, pages);
        if (next == page) return;
        page = next;
        INPUT.clear();
        playUi(ModSounds.PAGE, 1.0f);
    }

    public static List<Strategem> currentPage() {
        page = Math.min(page, Strategems.pageCount() - 1);
        return Strategems.page(page);
    }

    /** Stratagems that still match what has been typed so far. */
    public static boolean isCandidate(Strategem strategem) {
        return strategem.matchesPrefix(INPUT);
    }

    private static void onInput(Minecraft client, StrategemInput input) {
        INPUT.add(input);

        List<Strategem> candidates = currentPage().stream().filter(StrategemInputHandler::isCandidate).toList();
        if (candidates.isEmpty()) {
            INPUT.clear();
            show(Feedback.WRONG, null);
            playUi(ModSounds.INPUT_ERROR, 1.0f);
            return;
        }

        Strategem complete = candidates.stream().filter(s -> s.code().size() == INPUT.size()).findFirst().orElse(null);
        if (complete == null) {
            playUi(ModSounds.INPUT, 0.92f + INPUT.size() * 0.06f); // each step of the code climbs a little
            return;
        }

        INPUT.clear();
        if (ClientCooldowns.remainingTicks(complete) > 0) {
            show(Feedback.COOLDOWN, complete);
            playUi(ModSounds.INPUT_ERROR, 0.85f);
            return;
        }
        ItemStack held = client.player.getMainHandItem();
        if (held.getItem() instanceof StrategemBallItem) {
            ClientPlayNetworking.send(new StrategemProgramPayload(complete.id()));
            playUi(ModSounds.INPUT, 1.4f);
            show(Feedback.PROGRAMMED, complete); // starts the upload sound (DeviceFx)
        } else {
            show(Feedback.NO_BALL, complete);
            playUi(ModSounds.INPUT_ERROR, 1.15f);
        }
    }

    private static void show(Feedback type, @Nullable Strategem strategem) {
        feedback = type;
        feedbackStrategem = strategem;
        feedbackTicks = FEEDBACK_TICKS;

        // Presentation: the Arm Piece screen and the HUD react
        switch (type) {
            case PROGRAMMED -> {
                if (strategem != null) {
                    DeviceFx.onProgrammed(strategem);
                    HudToasts.push("STRATAGEM PROGRAMMED", strategem.displayName().getString(), strategem.color(), DeviceFx.HD_YELLOW);
                }
            }
            case COOLDOWN -> {
                DeviceFx.onError();
                if (strategem != null) {
                    HudToasts.push("ON COOLDOWN  " + StrategemCooldowns.format(ClientCooldowns.remainingTicks(strategem)),
                            strategem.displayName().getString(), DeviceFx.DANGER_RED, DeviceFx.DANGER_RED);
                }
            }
            case NO_BALL -> {
                DeviceFx.onError();
                HudToasts.push("NO STRATEGEM BALL IN HAND", "", DeviceFx.DANGER_RED, DeviceFx.DANGER_RED);
            }
            case WRONG -> DeviceFx.onError();
            default -> {
            }
        }
    }

    private static void playUi(SoundEvent sound, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
    }
}
