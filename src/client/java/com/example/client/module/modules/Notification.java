package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.events.TickEvent;
import com.example.client.language.Language;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.settings.BooleanSetting;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

@ModuleInfo(name = "module.notification", enable = true)
public class Notification extends AbstractModule {

    @SettingInfo(name = "setting.round_recorder")
    public static final BooleanSetting roundRecorder = new BooleanSetting(true);
    @SettingInfo(name = "setting.aa_round_suggest")
    public static final BooleanSetting roundSuggest = new BooleanSetting(true);
    @SettingInfo(name = "setting.aa_round_chat")
    public static final BooleanSetting aaRoundChat = new BooleanSetting(false);
    @SettingInfo(name = "setting.game_stat_chat")
    public static final BooleanSetting gameStatChat = new BooleanSetting(false);

    public Notification() {
        registerSetting(roundRecorder, roundSuggest, aaRoundChat, gameStatChat);
    }
}
