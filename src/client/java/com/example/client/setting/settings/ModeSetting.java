package com.example.client.setting.settings;

import com.example.client.setting.Setting;
import com.example.client.setting.attribute.SettingAttribute;
import com.google.gson.JsonElement;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
public class ModeSetting extends Setting<String> {
    @Setter
    private List<String> modes;

    public ModeSetting(String value, List<String> modes) {
        super(value);
        this.modes = modes;
    }

    @Override
    public String getJson(JsonElement jsonElement) {
        return jsonElement.getAsString();
    }
    public boolean is(String va) {
        return va.equals(value);
    }
    @Override
    public boolean canSaveConfig() {
        return true;
    }

    @SafeVarargs
    public ModeSetting(String value, List<String> modes, SettingAttribute<String>... settingAttributes) {
        super(value, settingAttributes);
        this.modes = modes;
    }
    public void setMode(String mode) {
        if (modes == null || modes.isEmpty()) {
            value = mode;
            return;
        }

        if (modes.contains(mode)) {
            value = mode;
        }
    }
    public String next() {
        int index = modes.indexOf(value);

        if (index == -1) {
            value = modes.getFirst();
            return value;
        }

        index++;

        if (index >= modes.size()) {
            index = 0;
        }

        value = modes.get(index);
        return value;
    }
}
