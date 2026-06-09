package com.example.client.setting.settings;


import com.example.client.setting.Setting;

public abstract class ButtonSetting extends Setting<String> {
    public ButtonSetting() {
        super("");
    }
    public abstract void onClickedButton();
}
