package net.ryzlar.fx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.ryzlar.LaserMod;

/**
 * Snapshots of the finished world image (depth + color), taken at the start of the effects pass, so effect shaders
 * can read the scene they are drawn into:
 * <ul>
 *     <li>{@link #DEPTH}: soft depth. Smoke, fire, energy and shockwaves fade out where they meet terrain instead of
 *     being sliced by block faces (see {@code shaders/core/fx_soft.fsh}).</li>
 *     <li>{@link #COLOR}: heat haze and spatial lensing, which bend the scene behind them
 *     (see {@code shaders/core/fx_distort.fsh}).</li>
 * </ul>
 * Only copied on frames where the effects pass runs (something is actually on screen).
 */
public final class SceneTextures {

    public static final Identifier DEPTH = Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "fx/scene_depth");
    public static final Identifier COLOR = Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "fx/scene_color");

    private static final Snapshot DEPTH_SNAPSHOT = new Snapshot("lasermod scene depth", TextureFormat.DEPTH32, FilterMode.NEAREST);
    private static final Snapshot COLOR_SNAPSHOT = new Snapshot("lasermod scene color", TextureFormat.RGBA8, FilterMode.LINEAR);
    private static boolean registered;

    private SceneTextures() {
    }

    /** Copies the target's depth and color. Must run on the render thread, outside of any open render pass. */
    public static void capture(RenderTarget target) {
        if (!registered) {
            TextureManager textures = Minecraft.getInstance().getTextureManager();
            textures.register(DEPTH, DEPTH_SNAPSHOT);
            textures.register(COLOR, COLOR_SNAPSHOT);
            registered = true;
        }
        GpuTexture depth = target.getDepthTexture();
        GpuTexture color = target.getColorTexture();
        if (depth != null) DEPTH_SNAPSHOT.copyFrom(depth);
        if (color != null) COLOR_SNAPSHOT.copyFrom(color);
    }

    /** A texture whose content is replaced by a GPU-side copy of another texture of the same format. */
    private static final class Snapshot extends AbstractTexture {
        private final String label;
        private final TextureFormat format;
        private final FilterMode filter;

        Snapshot(String label, TextureFormat format, FilterMode filter) {
            this.label = label;
            this.format = format;
            this.filter = filter;
        }

        void copyFrom(GpuTexture source) {
            int width = source.getWidth(0);
            int height = source.getHeight(0);
            if (texture == null || texture.isClosed() || texture.getWidth(0) != width || texture.getHeight(0) != height) {
                close();
                GpuDevice device = RenderSystem.getDevice();
                texture = device.createTexture(() -> label, GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                        format, width, height, 1, 1);
                textureView = device.createTextureView(texture);
                sampler = RenderSystem.getSamplerCache().getClampToEdge(filter);
            }
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(source, texture, 0, 0, 0, 0, 0, width, height);
        }
    }
}
