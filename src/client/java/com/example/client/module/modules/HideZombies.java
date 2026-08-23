package com.example.client.module.modules;

import com.example.client.language.Language;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.settings.BooleanSetting;
import com.example.client.setting.settings.NumberSetting;

@ModuleInfo(name = "module.hide_zombies", enable = true)
public class HideZombies extends AbstractModule {
    @SettingInfo(name = "setting.hide_overlap_expand")
    public static final NumberSetting fadeOverlapExpand = new NumberSetting(0.15d, 0, 1d, "#.0");

    @SettingInfo(name = "setting.full_hide")
    public static final BooleanSetting fullHide = new BooleanSetting(false);

    public HideZombies() {
        registerSetting(fadeOverlapExpand, fullHide);
    }
}
