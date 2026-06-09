package com.example.client.setting.settings;

import com.example.client.setting.Setting;
import com.example.client.setting.attribute.SettingAttribute;
import com.google.gson.JsonElement;

public class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(Boolean value) {
        super(value);
    }

    @SafeVarargs
    public BooleanSetting(Boolean value, SettingAttribute<Boolean>... settingAttributes) {
        super(value, settingAttributes);
    }

    @Override
    public boolean canSaveConfig() {
        return true;
    }

    @Override
    public Boolean getJson(JsonElement jsonElement) {
        return jsonElement.getAsBoolean();
    }
}
