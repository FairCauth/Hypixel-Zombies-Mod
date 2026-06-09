package com.example.client.module.modules;

import com.example.client.language.Language;
import com.example.client.language.Text;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.settings.BooleanSetting;
import com.example.client.setting.settings.NumberSetting;

@ModuleInfo(name = {
        @Text(label = "Hide Blocking Player", language = Language.English),
        @Text(label = "隐藏阻挡玩家", language = Language.Chinese)
}, enable = true)
public class HideBlockingPlayer extends AbstractModule {
    @SettingInfo(name = {
            @Text(label = "Fade Overlap Expand", language = Language.English)
    })
    public static final NumberSetting fadeOverlapExpand = new NumberSetting(0.15d, 0, 1d,"#.0");
//    @SettingInfo(name = {
//            @Text(label = "Fade Player Alpha", language = Language.English)
//    })
//    public static final NumberSetting fadePlayerAlpha = new NumberSetting(100, 0, 255,"#");


    public HideBlockingPlayer() {
        registerSetting(fadeOverlapExpand);

    }
}
