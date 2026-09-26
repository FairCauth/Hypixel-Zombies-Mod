package com.example.client.chams;

import com.example.client.language.Language;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.settings.BooleanSetting;

@ModuleInfo(name = "module.zombie_chams", enable = false)
public class ZombieChams extends AbstractModule {

    @SettingInfo(name = "setting.only_in_zombies")
    public static final BooleanSetting onlyGame = new BooleanSetting(true);

    @SettingInfo(name = "setting.invisible_entities")
    public static final BooleanSetting invisibleEntities = new BooleanSetting(true);

    public ZombieChams() {
        registerSetting(onlyGame, invisibleEntities);
    }
}
