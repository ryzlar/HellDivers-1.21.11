package net.ryzlar.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.ryzlar.strategem.Attack;
import net.ryzlar.strategem.Strategem;
import net.ryzlar.strategem.Strategems;
import net.ryzlar.strike.MeteorStrike;
import net.ryzlar.strike.NuclearStrike;
import net.ryzlar.strike.VoidRiftStrike;

/**
 * Visual check of every strike: calls each one in on its own patch of a test world and screenshots the key
 * phases from fixed camera positions (screenshots land in run/screenshots). Not a pass/fail test.
 */
public class StrikeVisualTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            context.runOnClient(mc -> {
                mc.options.renderDistance().set(12);
                mc.options.broadcastOptions(); // the server only tracks entities within the client's view distance
            });
            world.getServer().runOnServer(server -> server.getPlayerList().setViewDistance(12));
            context.waitTicks(40);
            world.getServer().runCommand("gamerule doDaylightCycle false");
            world.getServer().runCommand("gamerule doMobSpawning false");
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("weather clear");
            world.getServer().runCommand("gamemode spectator @a");

            voidRift(context, world, 0);
            volcano(context, world, 400);
            meteor(context, world, 800);
            lightning(context, world, 1200);
            nuke(context, world, 1700);
        }
    }

    // ---------------------------------------------------------------- per strike

    private void voidRift(ClientGameTestContext context, TestSingleplayerContext world, int x) {
        BlockPos ground = prepare(context, world, x, 0);
        Vec3 rift = VoidRiftStrike.riftCenter(Vec3.atBottomCenterOf(ground));
        call(world, Strategems.VOID_RIFT, ground);
        // stand in front of the rift plane, then off to the side, then behind it
        Vec3 n = VoidRiftStrike.normal(lastSeed);
        // a few mobs to get swallowed
        for (int i = 0; i < 4; i++) {
            world.getServer().runCommand(String.format("summon cow %d %d %d", ground.getX() + 8 + i * 3, ground.getY(), ground.getZ() + 6 - i * 4));
        }
        look(world, rift.add(n.scale(26)).add(0, 2, 0), rift);
        shoot(context, 40, "rift_01_distortion");
        shoot(context, 30, "rift_02_cracks");
        shoot(context, 40, "rift_03_expand");
        shoot(context, 60, "rift_04_open_front");
        look(world, rift.add(VoidRiftStrike.right(lastSeed).scale(18)).add(n.scale(14)).add(0, 4, 0), rift);
        shoot(context, 20, "rift_05_open_angle");
        look(world, rift.add(n.scale(-22)).add(0, -2, 0), rift);
        shoot(context, 20, "rift_06_open_behind");
        look(world, rift.add(n.scale(12)).add(0, 1, 0), rift);
        shoot(context, 40, "rift_07_close_suction");
        shoot(context, 160, "rift_08_unstable");
        look(world, rift.add(n.scale(30)).add(0, 5, 0), rift);
        shoot(context, 60, "rift_09_unstable_far");
        shoot(context, 100, "rift_10_collapse");
        shoot(context, 26, "rift_11_implosion");
    }

    private void volcano(ClientGameTestContext context, TestSingleplayerContext world, int x) {
        BlockPos ground = prepare(context, world, x, 0);
        Vec3 c = Vec3.atBottomCenterOf(ground);
        call(world, Strategems.VOLCANIC_ERUPTION, ground);
        look(world, c.add(30, 18, 30), c);
        shoot(context, 120, "volcano_01_cracks");
        shoot(context, 120, "volcano_02_hot_cracks");
        shoot(context, 30, "volcano_03_opening");
        look(world, c.add(4, 22, 4), c.add(0, -10, 0));
        shoot(context, 40, "volcano_04_chasm_from_above");
        look(world, c.add(45, 25, 45), c.add(0, 15, 0));
        shoot(context, 70, "volcano_05_eruption");
        shoot(context, 80, "volcano_06_plume");
        look(world, c.add(70, 30, 0), c.add(0, 20, 0));
        shoot(context, 60, "volcano_07_far");
    }

    private void meteor(ClientGameTestContext context, TestSingleplayerContext world, int x) {
        BlockPos ground = prepare(context, world, x, 0);
        Vec3 c = Vec3.atBottomCenterOf(ground);
        call(world, Strategems.METEOR_STRIKE, ground);
        look(world, c.add(-35, 12, -35), c.add(MeteorStrike.entryOffset(seedOf(world)).scale(0.3)));
        shoot(context, 40, "meteor_01_mark");
        shoot(context, 50, "meteor_02_incoming");
        shoot(context, 30, "meteor_03_close");
        look(world, c.add(-40, 15, -40), c);
        shoot(context, 12, "meteor_04_impact");
        shoot(context, 40, "meteor_05_ejecta");
        shoot(context, 140, "meteor_06_aftermath");
    }

    private void lightning(ClientGameTestContext context, TestSingleplayerContext world, int x) {
        BlockPos ground = prepare(context, world, x, 0);
        Vec3 c = Vec3.atBottomCenterOf(ground);
        call(world, Strategems.ORBITAL_LIGHTNING, ground);
        look(world, c.add(-30, 15, -30), c.add(0, 30, 0));
        shoot(context, 80, "lightning_01_vortex");
        shoot(context, 50, "lightning_02_barrage");
        shoot(context, 60, "lightning_03_heavy");
        shoot(context, 30, "lightning_04_charge");
        shoot(context, 12, "lightning_05_final");
        shoot(context, 40, "lightning_06_after");
    }

    private void nuke(ClientGameTestContext context, TestSingleplayerContext world, int x) {
        BlockPos ground = prepare(context, world, x, 0);
        Vec3 c = Vec3.atBottomCenterOf(ground);
        call(world, Strategems.NUCLEAR_STRIKE, ground);
        look(world, c.add(-70, 25, -70), c.add(0, 25, 0));
        shoot(context, 150, "nuke_01_countdown");
        shoot(context, NuclearStrike.DETONATION - 150 + 3, "nuke_02_flash");
        shoot(context, 30, "nuke_03_fireball");
        shoot(context, 80, "nuke_04_cloud");
        look(world, c.add(-120, 30, -120), c.add(0, 70, 0));
        shoot(context, 200, "nuke_05_mushroom");
    }

    // ---------------------------------------------------------------- helpers

    private int lastSeed;

    private int seedOf(TestSingleplayerContext world) {
        return lastSeed;
    }

    /** Teleports the camera near the test spot so chunks load, returns the ground block above which to strike. */
    private BlockPos prepare(ClientGameTestContext context, TestSingleplayerContext world, int x, int z) {
        world.getServer().runCommand(String.format("tp @a %d 150 %d", x, z));
        context.waitTicks(120);
        return world.getServer().computeOnServer(server -> {
            ServerLevel level = server.overworld();
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            return new BlockPos(x, y, z);
        });
    }

    private void call(TestSingleplayerContext world, Strategem strategem, BlockPos ground) {
        world.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            strategem.attack().execute(new Attack.Context(level, strategem, Vec3.atBottomCenterOf(ground), ground, Direction.UP, player));
            level.getEntitiesOfClass(net.ryzlar.strike.StrikeEntity.class, new net.minecraft.world.phys.AABB(ground).inflate(4))
                    .forEach(s -> lastSeed = s.getSeed());
        });
    }

    private void look(TestSingleplayerContext world, Vec3 from, Vec3 at) {
        Vec3 d = at.subtract(from);
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        world.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", from.x, from.y, from.z, yaw, pitch));
    }

    private void shoot(ClientGameTestContext context, int ticks, String name) {
        context.waitTicks(ticks);
        context.takeScreenshot(name);
    }
}
