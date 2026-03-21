package net.ryzlar;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.ryzlar.ModClient.ModBeams;
import net.ryzlar.entities.ModEntities;
import net.ryzlar.gui.StrategemUI;
import net.ryzlar.items.effects.StrategemBall;
import net.ryzlar.keymapping.KeyMappings;
import net.ryzlar.laser.BeamData;
import net.ryzlar.network.ClientBeamReceiver;
import net.ryzlar.renderer.LaserRenderer;
import net.ryzlar.renderer.TrajectoryRenderer;
import org.joml.Matrix4f;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

import java.security.Key;

public class LaserModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {

		ClientBeamReceiver.registerBeam();
		ClientBeamReceiver.registerArmPiece();

		KeyMappings.initialize();


		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "stratagem_hud_unfocused"),
				(graphics, tickCounter) -> {
					if (ModBeams.HAS_ARM_PIECE) {
						StrategemUI.renderUnfocusedMenu(graphics, tickCounter, "STRATAGEMS");
						StrategemUI.renderFocusedMenu(graphics, tickCounter, "STRATAGEMS");
					}
				}
		);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (ModBeams.HAS_ARM_PIECE) {
				StrategemUI.menuOpen = KeyMappings.STRATEGEM_MENU_KEY.isDown();
			} else {
				StrategemUI.menuOpen = false;
			}
		});

		WorldRenderEvents.END_MAIN.register(context -> {
			if (context.consumers() == null) return;
			LaserRenderer.render(
					context.matrices(),
					context.consumers()
			);
		});

		EntityRendererRegistry.register(ModEntities.STRATAGEM_BALL, ThrownItemRenderer::new);


		WorldRenderEvents.END_MAIN.register(context -> {
			if (context.consumers() == null) return;

			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null) return;

			if (mc.player.isUsingItem() && mc.player.getUseItem().getItem() instanceof StrategemBall) {
				int timeLeft = mc.player.getUseItemRemainingTicks();
				int charge = mc.player.getUseItem().getUseDuration(mc.player) - timeLeft;
				float power = Math.min(1.0f, charge / (float) StrategemBall.MAX_CHARGE);

				TrajectoryRenderer.render(context.matrices(), context.consumers(), power);
			}
		});

	}
}