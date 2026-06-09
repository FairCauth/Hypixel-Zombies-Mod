package com.example.client.setting.attribute;


import com.example.client.setting.Setting;

/**
 * @param <T> setting type
 * @author FairCauth
 */
public record SettingAttribute<T>(Setting<?> setting, T... value) {

    public SettingAttribute {
    }

    public boolean get() {
        for (T t : value)
            if (t.equals(setting.getParent().getValue())) return true;
        return value.equals(setting.getParent().getValue());
    }
}
