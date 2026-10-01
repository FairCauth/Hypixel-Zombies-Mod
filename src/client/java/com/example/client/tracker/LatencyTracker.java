package com.example.client.tracker;

import com.example.client.module.modules.SidebarModification;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;

import java.util.concurrent.TimeUnit;

public final class LatencyTracker {
    private static final long PROBE_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static final long PROBE_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5);
    private static final long PROBE_TAG = 0x5A4D000000000000L;
    private static final long PROBE_TAG_MASK = 0xFFFF000000000000L;
    private static final long PROBE_NONCE_MASK = 0x0000FFFFFFFFFFFFL;

    private static ClientPacketListener connection;
    private static long lastProbeNanos;
    private static long sentAtNanos;
    private static long probeToken;
    private static boolean pending;
    private static int latencyMs = -1;

    private LatencyTracker() {
    }

    public static synchronized void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener current = minecraft.getConnection();
        if (current == null || minecraft.player == null || !SidebarModification.isActive()) {
            reset();
            return;
        }

        if (current != connection) {
            reset();
            connection = current;
        }

        long now = System.nanoTime();
        if (pending) {
            if (now - sentAtNanos < PROBE_TIMEOUT_NANOS) return;
            pending = false;
            latencyMs = -1;
        }
        if (lastProbeNanos != 0 && now - lastProbeNanos < PROBE_INTERVAL_NANOS) return;

        probeToken = PROBE_TAG | (now & PROBE_NONCE_MASK);
        sentAtNanos = now;
        lastProbeNanos = now;
        pending = true;
        current.send(new ServerboundPingRequestPacket(probeToken));
    }

    public static synchronized boolean onPong(ClientPacketListener source, long token) {
        if ((token & PROBE_TAG_MASK) != PROBE_TAG) return false;

        if (pending && source == connection && token == probeToken) {
            latencyMs = (int) Math.min(Integer.MAX_VALUE,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sentAtNanos));
            pending = false;
        }
        return true;
    }

    public static synchronized int getLatencyMs() {
        return latencyMs;
    }

    private static void reset() {
        connection = null;
        lastProbeNanos = 0;
        pending = false;
        latencyMs = -1;
    }
}
