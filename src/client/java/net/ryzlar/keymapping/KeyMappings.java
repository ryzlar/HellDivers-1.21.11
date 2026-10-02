package net.ryzlar.keymapping;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.ryzlar.LaserMod;
import net.ryzlar.strategem.StrategemInput;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.Map;

public class KeyMappings {

    public static KeyMapping STRATEGEM_MENU_KEY;
    public static KeyMapping STRATEGEM_PREV_PAGE;
    public static KeyMapping STRATEGEM_NEXT_PAGE;

    /** Arrow inputs used while the stratagem menu is open (arrow keys by default, rebindable). */
    public static final Map<StrategemInput, KeyMapping> STRATEGEM_INPUT_KEYS = new EnumMap<>(StrategemInput.class);

    public static KeyMapping.Category STRATEGEM =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "category.lasermod.general"));

    public static void initialize() {
        STRATEGEM_MENU_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.lasermod.stratagem_menu",
                GLFW.GLFW_KEY_LEFT_CONTROL,
                STRATEGEM
        ));

        // Pages also flip with the scroll wheel while the menu is open
        STRATEGEM_PREV_PAGE = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.lasermod.stratagem_prev_page", GLFW.GLFW_KEY_LEFT_BRACKET, STRATEGEM));
        STRATEGEM_NEXT_PAGE = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.lasermod.stratagem_next_page", GLFW.GLFW_KEY_RIGHT_BRACKET, STRATEGEM));

        registerInput(StrategemInput.UP, "key.lasermod.stratagem_up", GLFW.GLFW_KEY_UP);
        registerInput(StrategemInput.DOWN, "key.lasermod.stratagem_down", GLFW.GLFW_KEY_DOWN);
        registerInput(StrategemInput.LEFT, "key.lasermod.stratagem_left", GLFW.GLFW_KEY_LEFT);
        registerInput(StrategemInput.RIGHT, "key.lasermod.stratagem_right", GLFW.GLFW_KEY_RIGHT);
    }

    private static void registerInput(StrategemInput input, String name, int defaultKey) {
        STRATEGEM_INPUT_KEYS.put(input, KeyBindingHelper.registerKeyBinding(new KeyMapping(name, defaultKey, STRATEGEM)));
    }
}
