package net.ryzlar.keymapping;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.feline.Cat;
import net.ryzlar.LaserMod;
import org.lwjgl.glfw.GLFW;

public class KeyMappings {

    public static KeyMapping STRATEGEM_MENU_KEY;

    public static KeyMapping.Category STRATEGEM =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "category.lasermod.general"));

    public static void initialize() {
        STRATEGEM_MENU_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.lasermod.stratagem_menu",
                GLFW.GLFW_KEY_LEFT_CONTROL,
                STRATEGEM
        ));
    }

}
