package net.ryzlar.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ryzlar.entities.StrategemBallEntity;
import net.ryzlar.items.StrategemBallItem;
import net.ryzlar.sound.ModSounds;
import net.ryzlar.strategem.Strategem;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * World-space effects of the thrown beacon:
 * <ul>
 *     <li>a thin light trail in its stratagem color that follows its real flight path (faint grey when empty);</li>
 *     <li>the landing beacon: when a programmed ball activates, a beam of light shoots up from the impact point
 *     with a ground ping - the classic "stratagem incoming" signal - before the attack takes over.</li>
 * </ul>
 */
public final class BallFx {

    private static final int TRAIL_LENGTH = 14;
    private static final double BEACON_SECONDS = 1.8;

    private record Beacon(Vec3 pos, int color, double born) {
    }

    private static final Map<Integer, Deque<Vec3>> TRAILS = new HashMap<>();
    private static final Map<Integer, Integer> TRAIL_COLORS = new HashMap<>();
    private static final List<Beacon> BEACONS = new ArrayList<>();

    private BallFx() {
    }

    /** Records flight positions once per tick. */
    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) {
            TRAILS.clear();
            BEACONS.clear();
            return;
        }
        if (mc.isPaused()) return;
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof StrategemBallEntity ball)) continue;
            Deque<Vec3> trail = TRAILS.computeIfAbsent(ball.getId(), id -> new ArrayDeque<>());
            trail.addFirst(ball.position().add(0, 0.12, 0));
            while (trail.size() > TRAIL_LENGTH) trail.removeLast();
            Strategem strategem = StrategemBallItem.getStrategem(ball.getItem());
            TRAIL_COLORS.put(ball.getId(), strategem != null ? strategem.color() : 0x8A9096);
        }
        TRAILS.keySet().removeIf(id -> !(level.getEntity(id) instanceof StrategemBallEntity));
        TRAIL_COLORS.keySet().retainAll(TRAILS.keySet());
        BEACONS.removeIf(b -> DeviceFx.now() - b.born() > BEACON_SECONDS);
    }

    /** A ball left the world: if it was discarded (it landed) and programmed, its stratagem is activating. */
    public static void onUnload(Entity entity, ClientLevel level) {
        if (!(entity instanceof StrategemBallEntity ball)) return;
        TRAILS.remove(ball.getId());
        if (ball.getRemovalReason() != Entity.RemovalReason.DISCARDED) return;
        Strategem strategem = StrategemBallItem.getStrategem(ball.getItem());
        if (strategem == null) return;
        Vec3 pos = ball.position();
        BEACONS.add(new Beacon(pos, strategem.color(), DeviceFx.now()));
        level.playLocalSound(pos.x, pos.y, pos.z, ModSounds.BEACON_LAND, SoundSource.PLAYERS, 1.4f, 1.0f, false);
    }

    public static boolean hasWork() {
        return !TRAILS.isEmpty() || !BEACONS.isEmpty();
    }

    public static void render(FxContext ctx) {
        renderTrails(ctx);
        renderBeacons(ctx);
    }

    private static void renderTrails(FxContext ctx) {
        for (Map.Entry<Integer, Deque<Vec3>> entry : TRAILS.entrySet()) {
            Deque<Vec3> trail = entry.getValue();
            if (trail.size() < 3) continue;
            Entity ball = ctx.level.getEntity(entry.getKey());
            if (ball == null) continue;
            int color = TRAIL_COLORS.getOrDefault(entry.getKey(), 0x8A9096);
            boolean programmed = color != 0x8A9096;

            List<Vector3f> points = new ArrayList<>();
            // start exactly at the ball's interpolated position so the trail stays attached
            points.add(ctx.rel(ball.getPosition(ctx.partialTick).add(0, 0.12, 0)));
            Iterator<Vec3> it = trail.iterator();
            it.next();
            while (it.hasNext()) points.add(ctx.rel(it.next()));
            int n = points.size();
            float[] widths = new float[n], alphas = new float[n];
            for (int i = 0; i < n; i++) {
                float t = i / (float) (n - 1);
                widths[i] = Mth.lerp(t, programmed ? 0.09f : 0.05f, 0.01f);
                alphas[i] = (1.0f - t) * (programmed ? 0.75f : 0.3f);
            }
            FxDraw.ribbon(ctx, true, points, widths, alphas, color);
            if (programmed) {
                FxDraw.hardRibbon(ctx, true, points.subList(0, Math.min(5, n)), 0.012f, 0xFFFFFF, 0.8f);
            }
        }
    }

    private static void renderBeacons(FxContext ctx) {
        double now = DeviceFx.now();
        for (Beacon beacon : BEACONS) {
            float t = (float) ((now - beacon.born()) / BEACON_SECONDS);
            float fade = 1.0f - t * t;
            Vector3f base = ctx.rel(beacon.pos());
            // beam shooting up into the sky, then thinning out
            float height = 60.0f * FxDraw.easeOut(Math.min(1.0f, t * 3.0f));
            Vector3f top = new Vector3f(base).add(0, height, 0);
            FxDraw.beam(ctx, true, base, top, 0.5f * fade, beacon.color(), 0.7f * fade, 0.0f);
            FxDraw.hardRibbon(ctx, true, List.of(new Vector3f(base), top), 0.04f * fade + 0.01f, 0xFFFFFF, fade);
            // ground ping
            float ping = (t * 2.2f) % 1.0f;
            FxDraw.groundRing(ctx, true, new Vector3f(base).add(0, 0.05f, 0), 0.4f + ping * 3.5f, 0.12f, beacon.color(), (1.0f - ping) * fade);
            FxDraw.sprite(ctx, true, new Vector3f(base).add(0, 0.25f, 0), 0.9f, beacon.color(), 0.9f * fade, 0.0f);
        }
    }
}
