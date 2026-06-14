package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.events.TickEvent;
import com.example.client.language.Language;
import com.example.client.language.Text;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.settings.BooleanSetting;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

@ModuleInfo(name = {
        @Text(label = "Notification", language = Language.English),
        @Text(label = "回合提示", language = Language.Chinese)
}, enable = true)
public class Notification extends AbstractModule {

    @SettingInfo(name = {
            @Text(label = "Round Recorder", language = Language.English),
            @Text(label = "回合计时", language = Language.Chinese)
    })
    public static final BooleanSetting roundRecorder = new BooleanSetting(true);
    @SettingInfo(name = {
            @Text(label = "AA Round Suggest", language = Language.English),
            @Text(label = "AA 回合建议", language = Language.Chinese)
    })
    public static final BooleanSetting roundSuggest = new BooleanSetting(true);

    public Notification() {
        registerSetting(roundRecorder, roundSuggest);
    }
}
