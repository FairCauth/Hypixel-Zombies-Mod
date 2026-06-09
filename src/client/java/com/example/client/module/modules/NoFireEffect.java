package com.example.client.module.modules;

import com.example.client.language.Language;
import com.example.client.language.Text;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.settings.NumberSetting;

@ModuleInfo(name = {
        @Text(label = "No Fire Effect", language = Language.English),
        @Text(label = "无火焰特效", language = Language.Chinese)
}, enable = true)
public class NoFireEffect extends AbstractModule {
    @SettingInfo(name = {
            @Text(label = "Fire Alpha", language = Language.English),
            @Text(label = "火焰透明度", language = Language.Chinese)
    })
    public static final NumberSetting fireAlpha = new NumberSetting(0.25, 0.0, 1.0, "#.00");

    public NoFireEffect() {
        registerSetting(fireAlpha);
    }
}
