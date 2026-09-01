package com.example.client.notification;

public enum NotificationType {
    INFO("INFO", 0xFF59D9FF),
    SUCCESS("SUCCESS", 0xFF64FF91),
    WARNING("WARNING", 0xFFFFC857),
    ERROR("ERROR", 0xFFFF5A67);

    private final String label;
    private final int color;

    NotificationType(String label, int color) {
        this.label = label;
        this.color = color;
    }

    public String label() {
        return label;
    }

    public int color() {
        return color;
    }
}
