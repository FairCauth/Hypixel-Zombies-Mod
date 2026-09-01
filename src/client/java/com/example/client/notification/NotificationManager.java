package com.example.client.notification;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class NotificationManager {
    public static final long DEFAULT_DURATION_MS = 4_000L;
    public static final long DEFAULT_ALERT_DURATION_MS = 3_500L;

    private static final int MAX_VISIBLE = 5;
    private static final int MAX_QUEUED_ALERTS = 8;
    private static final AtomicLong IDS = new AtomicLong();
    private static final Object LOCK = new Object();
    private static final ArrayList<NotificationMessage> NOTIFICATIONS = new ArrayList<>();
    private static final ArrayDeque<NotificationMessage> ALERTS = new ArrayDeque<>();
    private static NotificationMessage activeAlert;

    private NotificationManager() {
    }

    public static void info(String title, String message) {
        show(NotificationType.INFO, title, message, DEFAULT_DURATION_MS);
    }

    public static void success(String title, String message) {
        show(NotificationType.SUCCESS, title, message, DEFAULT_DURATION_MS);
    }

    public static void warning(String title, String message) {
        show(NotificationType.WARNING, title, message, DEFAULT_DURATION_MS);
    }

    public static void error(String title, String message) {
        show(NotificationType.ERROR, title, message, DEFAULT_DURATION_MS);
    }

    public static void show(NotificationType type, String title, String message, long durationMs) {
        NotificationMessage notification = create(type, title, message, durationMs, false);
        synchronized (LOCK) {
            NOTIFICATIONS.addFirst(notification);
            while (NOTIFICATIONS.size() > MAX_VISIBLE) {
                NOTIFICATIONS.removeLast();
            }
        }
    }

    public static void alert(String title, String message) {
        alert(NotificationType.WARNING, title, message, DEFAULT_ALERT_DURATION_MS);
    }

    public static void alert(NotificationType type, String title, String message, long durationMs) {
        NotificationMessage notification = create(type, title, message, durationMs, true);
        synchronized (LOCK) {
            while (ALERTS.size() >= MAX_QUEUED_ALERTS) {
                ALERTS.removeFirst();
            }
            ALERTS.addLast(notification);
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            NOTIFICATIONS.clear();
            ALERTS.clear();
            activeAlert = null;
        }
    }

    static List<NotificationMessage> notifications(long now) {
        synchronized (LOCK) {
            NOTIFICATIONS.removeIf(notification -> notification.expired(now));
            return List.copyOf(NOTIFICATIONS);
        }
    }

    static NotificationMessage activeAlert(long now) {
        synchronized (LOCK) {
            if (activeAlert != null && activeAlert.expired(now)) {
                activeAlert = null;
            }
            if (activeAlert == null) {
                activeAlert = ALERTS.pollFirst();
                if (activeAlert != null) activeAlert.activate(now);
            }
            return activeAlert;
        }
    }

    private static NotificationMessage create(NotificationType type, String title, String message,
                                              long durationMs, boolean queued) {
        return new NotificationMessage(
                IDS.incrementAndGet(),
                normalize(title, "Notification"),
                normalize(message, ""),
                type == null ? NotificationType.INFO : type,
                Math.clamp(durationMs, 1_000L, 30_000L),
                queued
        );
    }

    private static String normalize(String value, String fallback) {
        if (value == null) return fallback;
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.isEmpty() ? fallback : normalized;
    }
}
