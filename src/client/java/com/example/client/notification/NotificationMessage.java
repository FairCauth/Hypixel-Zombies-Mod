package com.example.client.notification;

final class NotificationMessage {
    private final long id;
    private final String title;
    private final String message;
    private final NotificationType type;
    private final long durationMs;
    private long startedAt;

    NotificationMessage(long id, String title, String message, NotificationType type,
                        long durationMs, boolean queued) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.type = type;
        this.durationMs = durationMs;
        this.startedAt = queued ? -1L : System.currentTimeMillis();
    }

    long id() {
        return id;
    }

    String title() {
        return title;
    }

    String message() {
        return message;
    }

    NotificationType type() {
        return type;
    }

    long durationMs() {
        return durationMs;
    }

    long startedAt() {
        return startedAt;
    }

    void activate(long now) {
        if (startedAt < 0L) startedAt = now;
    }

    boolean expired(long now) {
        return startedAt >= 0L && now - startedAt >= durationMs;
    }
}
