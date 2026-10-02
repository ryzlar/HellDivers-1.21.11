package net.ryzlar.strike;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.ryzlar.fx.FxContext;
import net.ryzlar.strike.visual.MeteorVisual;
import net.ryzlar.strike.visual.NuclearVisual;
import net.ryzlar.strike.visual.OrbitalLightningVisual;
import net.ryzlar.strike.visual.VoidRiftVisual;
import net.ryzlar.strike.visual.VolcanicEruptionVisual;
import net.ryzlar.strategem.Strategems;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Finds running {@link StrikeEntity}s and drives their {@link StrikeVisual}s (render + per-tick events). */
public final class StrikeRenderer {

    private static final Map<Identifier, StrikeVisual> VISUALS = new HashMap<>();
    private static final List<StrikeEntity> STRIKES = new ArrayList<>();

    static {
        register(Strategems.VOID_RIFT.id(), new VoidRiftVisual());
        register(Strategems.METEOR_STRIKE.id(), new MeteorVisual());
        register(Strategems.ORBITAL_LIGHTNING.id(), new OrbitalLightningVisual());
        register(Strategems.VOLCANIC_ERUPTION.id(), new VolcanicEruptionVisual());
        register(Strategems.NUCLEAR_STRIKE.id(), new NuclearVisual());
    }

    private StrikeRenderer() {
    }

    /** Hook for new stratagems: the visual for a strike started with this stratagem id. */
    public static void register(Identifier strategem, StrikeVisual visual) {
        VISUALS.put(strategem, visual);
    }

    public static boolean hasStrikes() {
        STRIKES.clear();
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return false;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof StrikeEntity strike && !strike.isRemoved() && VISUALS.containsKey(strike.getStrategemId())) {
                STRIKES.add(strike);
            }
        }
        return !STRIKES.isEmpty();
    }

    public static void renderWorld(MultiBufferSource.BufferSource buffers) {
        FxContext ctx = context(buffers);
        if (ctx == null) return;
        for (StrikeEntity strike : STRIKES) {
            VISUALS.get(strike.getStrategemId()).renderDistortion(ctx, strike, strike.getAge(ctx.partialTick));
        }
        buffers.endBatch();
        for (StrikeEntity strike : STRIKES) {
            VISUALS.get(strike.getStrategemId()).renderSolid(ctx, strike, strike.getAge(ctx.partialTick));
        }
        buffers.endBatch();
        for (StrikeEntity strike : STRIKES) {
            VISUALS.get(strike.getStrategemId()).render(ctx, strike, strike.getAge(ctx.partialTick));
        }
    }

    public static void renderUi(MultiBufferSource.BufferSource buffers) {
        FxContext ctx = context(buffers);
        if (ctx == null) return;
        for (StrikeEntity strike : STRIKES) {
            VISUALS.get(strike.getStrategemId()).renderUi(ctx, strike, strike.getAge(ctx.partialTick));
        }
    }

    /** Runs each visual's tick once for every age step, so one-shot events never fire twice or get skipped. */
    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) return;
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof StrikeEntity strike)) continue;
            StrikeVisual visual = VISUALS.get(strike.getStrategemId());
            if (visual == null || strike.getStartTime() == 0L) continue;

            int age = (int) (level.getGameTime() - strike.getStartTime());
            if (strike.lastClientAge == Integer.MIN_VALUE) strike.lastClientAge = age - 1;
            for (int a = Math.max(strike.lastClientAge + 1, age - 20); a <= age; a++) {
                visual.tick(strike, level, a, level.getRandom());
            }
            strike.lastClientAge = Math.max(strike.lastClientAge, age);
        }
    }

    private static FxContext context(MultiBufferSource.BufferSource buffers) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        return new FxContext(buffers, new PoseStack(), mc.gameRenderer.getMainCamera(), mc.level,
                mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }
}
