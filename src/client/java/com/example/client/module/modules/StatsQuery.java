package com.example.client.module.modules;

import com.example.client.gui.StatsQueryScreen;
import com.example.client.gui.ZombiesConfigScreen;
import com.example.client.language.Language;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.settings.ButtonSetting;

@ModuleInfo(name = "module.stats_query", enable = false)
public class StatsQuery extends AbstractModule {


    @Override
    protected void onEnable() {
        if(mc.player == null || mc.level == null) return;
        toggle();
        mc.gui.setScreen(new StatsQueryScreen(null));
        super.onEnable();
    }
}
