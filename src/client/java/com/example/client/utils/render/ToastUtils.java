package com.example.client.utils.render;

import com.example.client.utils.IMinecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

public class ToastUtils implements IMinecraft {
    public static final long DEFAULT_DURATION_MS = 5000L;

    public static void show(String title, String message) {
        if (message == null) return;
        show(title, Component.literal(message), DEFAULT_DURATION_MS);
    }

    public static void show(String title, Component message) {
        show(title, message, DEFAULT_DURATION_MS);
    }

    public static void show(String title, String message, long durationMs) {
        if (message == null) return;
        show(title, Component.literal(message), durationMs);
    }

    public static void show(String title, Component message, long durationMs) {
        if (mc.player == null || message == null) return;

        SystemToast.add(
                mc.getToastManager(),
                new SystemToast.SystemToastId(durationMs),
                Component.literal(title),
                message
        );
    }
}
