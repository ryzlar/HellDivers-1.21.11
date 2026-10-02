package net.ryzlar;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
import net.ryzlar.fx.CameraFx;
import net.ryzlar.fx.BallFx;
import net.ryzlar.fx.DeviceFx;
import net.ryzlar.renderer.ModRendererTypes;
import net.ryzlar.renderer.StrategemBallRenderer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.ryzlar.gui.ClientCooldowns;
import net.ryzlar.gui.HudToasts;
import net.ryzlar.renderer.DeviceModelProperties;
import net.ryzlar.gui.StrategemInputHandler;
import net.ryzlar.strike.StrikeRenderer;
import net.ryzlar.gui.StrategemUI;
import net.ryzlar.items.StrategemBallItem;
import net.ryzlar.items.effects.StrategemBall;
import net.ryzlar.keymapping.KeyMappings;
import net.ryzlar.network.ClientBeamReceiver;
import net.ryzlar.network.StrategemBallThrowPayload;
import net.ryzlar.renderer.LaserRenderer;
import net.ryzlar.renderer.TrajectoryRenderer;
import net.ryzlar.strategem.Strategem;
import org.joml.Matrix4f;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

import java.security.Key;

public class LaserModClient implements ClientModInitializer {

    private static boolean wasUsingStratagem = false;
    private static int chargeWhileHolding = 0;
    private static Strategem chargingStrategem = null;

    /** Throw feedback: call-in confirmation (the server still decides; a ball on cooldown is refused there). */
    private static void onThrow(Strategem strategem) {
        if (strategem == null || ClientCooldowns.remainingTicks(strategem) > 0) return;
        DeviceFx.onThrow();
        HudToasts.push("CALL-IN CONFIRMED", strategem.displayName().getString(), strategem.color(), DeviceFx.HD_YELLOW);
    }

    @Override
    public void onInitializeClient() {

        ClientBeamReceiver.registerArmPiece();
        ClientCooldowns.register();
        // Live item-model tints/properties: Arm Piece screen + LEDs, Strategem Ball core, first-person raise
        DeviceModelProperties.register();
        // Effect pipelines (soft-depth glow/matter, heat haze) must be registered before the shaders load
        ModRendererTypes.initialize();

        KeyMappings.initialize();


        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "stratagem_hud_unfocused"),
                // status chip + menu need the Arm Piece; charge gauge and toasts always render
                (graphics, tickCounter) -> StrategemUI.render(graphics, tickCounter, ModBeams.HAS_ARM_PIECE)
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (ModBeams.HAS_ARM_PIECE) {
                StrategemUI.menuOpen = KeyMappings.STRATEGEM_MENU_KEY.isDown();
            } else {
                StrategemUI.menuOpen = false;
            }
            StrategemInputHandler.tick(client);
            DeviceFx.tick(client);
            StrategemUI.tick(client);

            if (client.player != null) {
                boolean isCurrentlyUsing = client.player.isUsingItem() &&
                        client.player.getUseItem().getItem() instanceof net.ryzlar.items.StrategemBallItem;

                // Track charge (and what is loaded) while holding
                if (isCurrentlyUsing) {
                    chargeWhileHolding = client.player.getTicksUsingItem();
                    chargingStrategem = net.ryzlar.items.StrategemBallItem.getStrategem(client.player.getUseItem());
                }

                if (wasUsingStratagem && !isCurrentlyUsing) {
                    float power = Math.min(1.0f, Math.max(0.0f, chargeWhileHolding / (float) StrategemBall.MAX_CHARGE));

                    ClientPlayNetworking.send(new StrategemBallThrowPayload(power));
                    onThrow(chargingStrategem);
                    chargeWhileHolding = 0;
                    chargingStrategem = null;
                }

                wasUsingStratagem = isCurrentlyUsing;
            } else {
                wasUsingStratagem = false;
                chargeWhileHolding = 0;
            }
        });


        // Thrown beacon: real 3D model that tumbles in flight, with a light trail and a landing beacon (BallFx)
        EntityRendererRegistry.register(ModEntities.STRATAGEM_BALL, StrategemBallRenderer::new);
        ClientTickEvents.END_CLIENT_TICK.register(BallFx::tick);
        ClientEntityEvents.ENTITY_UNLOAD.register(BallFx::onUnload);
        // Orbital laser is drawn by LaserRenderer in its own render pass, the entity itself is invisible
        EntityRendererRegistry.register(ModEntities.ORBITAL_LASER, NoopRenderer::new);
        // Strikes are drawn by StrikeRenderer in the same pass as the lasers
        EntityRendererRegistry.register(ModEntities.STRIKE, NoopRenderer::new);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            CameraFx.tick();
            StrikeRenderer.tick(client);
        });
        // Screen flash / tint above everything else on the HUD
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "camera_fx"),
                (graphics, tickCounter) -> CameraFx.renderOverlay(graphics));
        ClientTickEvents.END_CLIENT_TICK.register(LaserRenderer::tickParticles);


        WorldRenderEvents.END_MAIN.register(context -> {
            if (context.consumers() == null) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            if (mc.player.isUsingItem()) {
                ItemStack heldItem = mc.player.getUseItem();
                if (heldItem.getItem() instanceof net.ryzlar.items.StrategemBallItem ) {

                    float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
                    // Same charge the tick handler sends on release, plus the partial tick so the arc grows smoothly
                    float charge = mc.player.getTicksUsingItem(partialTick);
                    float power = Math.min(1.0f, charge / StrategemBall.MAX_CHARGE);

                    // Impact marker shows what the ball will call in: stratagem color, or grey when empty
                    Strategem strategem = StrategemBallItem.getStrategem(heldItem);
                    int markerColor = strategem != null ? strategem.color() : 0x9A9A9A;

                    TrajectoryRenderer.render(context.matrices(), context.consumers(), power, partialTick, markerColor);
                }
            }
        });

    }
}