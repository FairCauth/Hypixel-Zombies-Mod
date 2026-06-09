package com.example.client.setting.settings;

import com.example.client.setting.Setting;
import com.google.gson.JsonElement;

public class TextSetting extends Setting<String> {
    public TextSetting(String value) {
        super(value);
    }
    @Override
    public boolean canSaveConfig() {
        return true;
    }
    @Override
    public String getJson(JsonElement jsonElement) {
        return jsonElement.getAsString();
    }
}
