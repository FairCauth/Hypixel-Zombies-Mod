package com.example.client.language;

import net.minecraft.network.chat.Component;

public final class GuiText {
    private GuiText() {}

    public static Component text(String key) {
        return Component.translatable("zombies-mod." + key);
    }

    public static Component text(String key, Object... args) {
        return Component.translatable("zombies-mod." + key, args);
    }

    public static String textString(String key) {
        return text(key).getString();
    }

    public static String textString(String key, Object... args) {
        return text(key, args).getString();
    }

}