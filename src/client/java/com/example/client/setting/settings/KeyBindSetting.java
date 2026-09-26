package com.example.client.setting.settings;

import com.example.client.setting.Setting;
import com.example.client.setting.attribute.SettingAttribute;
import com.google.gson.JsonElement;

public class KeyBindSetting extends Setting<Integer> {
    public KeyBindSetting(int defaultKey) {
        super(defaultKey);
    }

    @SafeVarargs
    public KeyBindSetting(int defaultKey, SettingAttribute<Integer>... settingAttributes) {
        super(defaultKey, settingAttributes);
    }

    @Override
    public boolean canSaveConfig() {
        return true;
    }

    @Override
    public Integer getJson(JsonElement jsonElement) {
        return jsonElement.getAsInt();
    }
}
