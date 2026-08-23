package com.example.client.module.modules;

import com.example.client.language.Language;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.settings.NumberSetting;

@ModuleInfo(name = "module.no_fire_effect", enable = true)
public class NoFireEffect extends AbstractModule {
    @SettingInfo(name = "setting.fire_alpha")
    public static final NumberSetting fireAlpha = new NumberSetting(0.25, 0.0, 1.0, "#.00");

    public NoFireEffect() {
        registerSetting(fireAlpha);
    }
}
