package com.aetherianartificer.townstead.skillcheck;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.gametest.framework.MultipleTestTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;

import java.util.Collection;

/**
 * Runs the game tests in the open world. A production Forge registers neither the tests nor the
 * test command, so this class does both by hand. Written by tools/skills/port_forge.py.
 */
final class ForgeTestRun {
    private static final Class<?>[] TESTS = {ArmorerHeavyweightTests.class, ArmorerTests.class, ButcherTests.class, CartographerTests.class, ClericTests.class, CookTests.class, FarmerTests.class, FishermanTests.class, FletcherTests.class, HunterTests.class, LateCareerTests.class, MasonTests.class, RespecTests.class, ScribeTests.class, ShepherdTests.class, SkillLoadTests.class, TailorTests.class, ToolsmithTests.class, WeaponsmithTests.class, WitchTests.class};
    private static volatile Collection<GameTestInfo> infos;
    private static volatile MultipleTestTracker tracker;
    private static volatile boolean reported;

    private ForgeTestRun() {}

    static void start(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            try {
                for (Class<?> tests : TESTS) GameTestRegistry.register(tests);
                ServerLevel level = server.overworld();
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                GameTestRunner.clearMarkers(level);
                // On the flat ground, where a game-test server lays its tests.
                BlockPos at = new BlockPos(player.getBlockX() + 4, -60, player.getBlockZ() + 4);
                infos = GameTestRunner.runTestBatches(
                        GameTestRunner.groupTestsIntoBatches(GameTestRegistry.getAllTestFunctions()),
                        at, Rotation.NONE, level, GameTestTicker.SINGLETON, 8);
                tracker = new MultipleTestTracker(infos);
                System.out.println("[scene] game tests started: " + infos.size());
            } catch (Throwable failure) {
                failure.printStackTrace();
                System.out.println("[scene] game tests could not start: " + failure);
                reported = true;
            }
        });
    }

    /** Once each server tick, as a game-test server does it. */
    static void tick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END && tracker != null && !reported) {
            GameTestTicker.SINGLETON.tick();
        }
    }

    /** Reports once when the last test is over. */
    static boolean done(Minecraft mc) {
        if (reported) return true;
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) return true;
        MultipleTestTracker running = tracker;
        if (running == null || !running.isDone()) return false;
        reported = true;
        int failed = 0;
        for (GameTestInfo info : infos) {
            if (!info.hasFailed()) continue;
            failed++;
            System.out.println("[scene] test failed: " + info.getTestName() + ": "
                    + (info.getError() == null ? "" : info.getError().getMessage()));
        }
        System.out.println("[scene] GAME TESTS COMPLETE: " + infos.size() + " run, " + failed + " failed");
        return true;
    }
}
