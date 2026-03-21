package net.ryzlar.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.ryzlar.LaserMod;
import net.ryzlar.ModClient.ModBeams;

public class StrategemUI implements ClientModInitializer {


    @Override
    public void onInitializeClient() {
    }

    public static boolean menuOpen = false;
    public static float menuAnimation = 0.0f;

    public static void renderFocusedMenu(GuiGraphics graphics, DeltaTracker tickCounter, String txt) {

        // Animation
        if (menuOpen && menuAnimation < 1.0f) {
            menuAnimation = Math.min(1.0f, menuAnimation + 0.08f);

        }
        else if (!menuOpen && menuAnimation > 0.0f)
        {
            menuAnimation = Math.max(0.0f, menuAnimation - 0.8f);
        }

        if (menuAnimation <= 0.0f) return;

        Minecraft mc = Minecraft.getInstance();
        int padding = 2;
        int iconSize = 16;
        int textWidth = mc.font.width(txt);
        int fullWidth = padding + iconSize + padding + textWidth + padding + 1;
        int fullHeight = 100;
        int unfocusedHeight = iconSize + (padding * 2);

        int startSize = 10;
        int currentWidth = (int)(startSize + (fullWidth - startSize) * menuAnimation);
        int currentHeight = (int)(startSize + (fullHeight - startSize) * menuAnimation);

        int startX = -startSize;
        int targetX = 10;
        int x = (int)(startX + (targetX - startX) * menuAnimation);
        int y = 10 + unfocusedHeight + 5;

        graphics.fill(x - 2, y - 2, x + currentWidth + 2, y + currentHeight + 2, 0x33000000);
        graphics.fill(x - 1, y - 1, x + currentWidth + 1, y + currentHeight + 1, 0x44000000);
        graphics.fill(x, y, x + currentWidth, y + currentHeight, 0x88555555);


//        int iconX = x + padding;
//        int iconY = y + padding + 1;
//        Identifier ICON = Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "textures/icons/unfoc_strat.png");
//        graphics.blit(RenderPipelines.GUI_TEXTURED, ICON, iconX, iconY, 0, 0, iconSize, iconSize, iconSize, iconSize);


    }


    public static void renderUnfocusedMenu(GuiGraphics graphics, DeltaTracker tickCounter, String txt) {
        Minecraft mc = Minecraft.getInstance();

        int padding = 4;
        int iconSize = 11;
        int textWidth = mc.font.width(txt);
        int height = iconSize + (padding * 2);
        int width = padding + iconSize + padding + textWidth + padding;
        int x = 10;
        int y = 10;

        // Blur lagen
        graphics.fill(x - 2, y - 2, x + width + 2, y + height + 2, 0x33000000);
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0x44000000);
        // Achtergrond
        graphics.fill(x, y, x + width, y + height, 0x88555555);

        // Icon
        int iconX = x + padding;
        int iconY = y + padding + 1;
        Identifier ICON = Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "textures/icons/unfoc_strat.png");
        graphics.blit(RenderPipelines.GUI_TEXTURED, ICON, iconX, iconY, 0, 0, iconSize, iconSize, iconSize, iconSize);

        // Tekst verticaal gecentreerd
        int textX = x + padding + iconSize + padding;
        int textY = y + (height - mc.font.lineHeight) / 2 + (int)1.5;
        graphics.drawString(mc.font, txt, textX, textY, 0xFFFFFFFF, true);

    }



}
