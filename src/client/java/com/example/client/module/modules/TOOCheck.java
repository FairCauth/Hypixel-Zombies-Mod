package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.data.ZombiesWaves;
import com.example.client.events.TickEvent;
import com.example.client.language.Language;
import com.example.client.language.Text;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.notification.NotificationManager;
import com.example.client.tracker.ServerTracker;
import com.example.client.utils.PlayerUtils;
import com.example.client.utils.ZombiesMap;
import com.example.client.utils.ZombiesUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@ModuleInfo(name = {
        @Text(label = "TOO Check", language = Language.English),
        @Text(label = "长者检测", language = Language.Chinese)
}, enable = true)
public class TOOCheck extends AbstractModule {
    private static final double WINDOW_HALF_SIZE = 1.5D;
    private static final int SCAN_INTERVAL_TICKS = 4;
    private static final Window ULT_WINDOW = new Window("ULT", 28, 72, 32);
    private static final Window ALT_WINDOW = new Window("ALT", 18, 72, 44);

    private static final Window[] WINDOWS = {
            new Window("P1", 6, 72, 32),
            new Window("P2", -22, 72, 16),
            new Window("P5", 22, 72, 14),
            new Window("P3", -22, 72, 10),
            new Window("P4", -10, 72, -6),
            ULT_WINDOW,
            new Window("CL", -28, 72, 28),
            ALT_WINDOW,
            new Window("CR", -12, 72, 40),
            new Window("BR", 22, 72, -14),
            new Window("BL", 34, 72, -2)
    };

    private final Set<UUID> notifiedOldOnes = new HashSet<>();
    private int trackedRound = Integer.MIN_VALUE;
    private long trackedRoundTime = Long.MIN_VALUE;
    private long lastScanTick = Long.MIN_VALUE;

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.level == null || mc.player == null || !PlayerUtils.isInHypZombies()) {
            resetState();
            return;
        }

        int round = ServerTracker.currentRound;
        long roundTime = ServerTracker.roundTime;
        if (round != trackedRound || roundTime != trackedRoundTime) {
            notifiedOldOnes.clear();
            trackedRound = round;
            trackedRoundTime = roundTime;
        }

        ZombiesMap map = ZombiesUtils.getMap();
        int[] waves = ZombiesWaves.getWaves(map, round);
        if (map != ZombiesMap.ALIEN_ARCADIUM || waves == null || waves.length == 0) return;

        double elapsed = Math.max(0D, (System.currentTimeMillis() - roundTime) / 1000D);
        int waveIndex = ZombiesWaves.currentWaveIndex(waves, elapsed);
        if (waveIndex < 0 || !containsOldOne(ZombiesWaves.aaWaveBoss(map, round, waveIndex + 1))) return;

        long gameTick = mc.level.getGameTime();
        if (lastScanTick != Long.MIN_VALUE && gameTick - lastScanTick < SCAN_INTERVAL_TICKS) return;
        lastScanTick = gameTick;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!ZombiesUtils.isTheOldOne(entity) || notifiedOldOnes.contains(entity.getUUID())) continue;

            Window window = findWindow(entity);
            notifiedOldOnes.add(entity.getUUID());
            notifyOldOne(round, window);
        }
    }

    @Override
    protected void onDisable() {
        resetState();
    }

    private static boolean containsOldOne(ZombiesWaves.WaveBoss boss) {
        return boss == ZombiesWaves.WaveBoss.OLD_ONE || boss == ZombiesWaves.WaveBoss.BOTH;
    }

    private static void notifyOldOne(int round, Window window) {
        String location = window == null ? "MID" : window.name();
        String message = location + " 发现 TOO";
        if (isDangerousSpawn(round, window)) {
            NotificationManager.warning("THE OLD ONE", message);
        } else {
            NotificationManager.info("THE OLD ONE", message);
        }
    }

    private static boolean isDangerousSpawn(int round, Window window) {
        boolean x0Round = round == 70 || round == 80 || round == 90 || round == 100;
        if (!x0Round || window == null || mc.player == null
                || (window != ALT_WINDOW && window != ULT_WINDOW)) {
            return false;
        }

        double playerX = mc.player.getX();
        double playerZ = mc.player.getZ();
        double distanceToAlt = ALT_WINDOW.horizontalDistanceSquared(playerX, playerZ);
        double distanceToUlt = ULT_WINDOW.horizontalDistanceSquared(playerX, playerZ);
        Window nearestWindow = distanceToAlt <= distanceToUlt ? ALT_WINDOW : ULT_WINDOW;
        return window == nearestWindow;
    }

    private static Window findWindow(Entity entity) {
        AABB entityBox = entity.getBoundingBox();
        for (Window window : WINDOWS) {
            if (window.area().intersects(entityBox)) return window;
        }
        return null;
    }

    private void resetState() {
        notifiedOldOnes.clear();
        trackedRound = Integer.MIN_VALUE;
        trackedRoundTime = Long.MIN_VALUE;
        lastScanTick = Long.MIN_VALUE;
    }

    private record Window(String name, int x, int y, int z) {
        private double horizontalDistanceSquared(double targetX, double targetZ) {
            double deltaX = targetX - x;
            double deltaZ = targetZ - z;
            return deltaX * deltaX + deltaZ * deltaZ;
        }

        private AABB area() {
            return new AABB(
                    x - WINDOW_HALF_SIZE, y - WINDOW_HALF_SIZE, z - WINDOW_HALF_SIZE,
                    x + WINDOW_HALF_SIZE, y + WINDOW_HALF_SIZE, z + WINDOW_HALF_SIZE
            );
        }
    }
}
