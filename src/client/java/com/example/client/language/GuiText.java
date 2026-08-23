package com.example.client.language;

import net.minecraft.network.chat.Component;

public final class GuiText {
    private GuiText() {}

    public static Component text(String key) {
        String translationKey = key.contains(".") ? "zombies-mod." + key : "zombies-mod.gui." + key;
        return Component.translatable(translationKey);
    }

    public static Component text(String key, Object... args) {
        String translationKey = key.contains(".") ? "zombies-mod." + key : "zombies-mod.gui." + key;
        return Component.translatable(translationKey, args);
    }

    public static String textString(String key) {
        return text(key).getString();
    }

    public static String textString(String key, Object... args) {
        return text(key, args).getString();
    }

    public static String mode(String value) {
        return switch (value) {
            case "Interval" -> textString("interval");
            case "Cooldown" -> textString("cooldown");
            case "Simulate" -> textString("simulate");
            case "Key" -> textString("key_mode");
            default -> value;
        };
    }
}