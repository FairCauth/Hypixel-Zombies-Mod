package com.example.client.setting.settings;

import com.example.client.setting.Setting;
import com.example.client.utils.InputBindingUtils;
import com.google.gson.JsonElement;
import org.lwjgl.glfw.GLFW;

/** A keyboard or extra mouse button binding; zero disables the action. */
public final class KeySetting extends Setting<Integer> {
    public KeySetting(int binding) {
        super(validate(binding));
    }

    @Override
    public void setValue(Integer binding) {
        super.setValue(validate(binding));
    }

    private static int validate(Integer binding) {
        if (binding == null || !(binding == InputBindingUtils.NONE
                || InputBindingUtils.isMouseBinding(binding)
                || binding >= GLFW.GLFW_KEY_SPACE && binding <= GLFW.GLFW_KEY_LAST)) {
            throw new IllegalArgumentException("Invalid input binding: " + binding);
        }
        return binding;
    }

    @Override
    public boolean canSaveConfig() { return true; }

    @Override
    public Integer getJson(JsonElement json) { return validate(json.getAsInt()); }
}
