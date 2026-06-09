package com.example.client.module;

import com.darkmagician6.eventapi.EventManager;
import com.example.client.language.Language;
import com.example.client.language.Text;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.Setting;
import com.example.client.setting.SettingManager;
import com.example.client.utils.IMinecraft;
import lombok.Getter;
import lombok.Setter;

public class AbstractModule extends SettingManager implements IMinecraft {
    @Getter
    private final Text[] texts;

    @Getter
    @Setter
    private int key;
    @Getter
    private boolean enable;
    public AbstractModule() {
        ModuleInfo moduleInfo = this.getClass().getAnnotation(ModuleInfo.class);
        if (moduleInfo == null)
            throw new RuntimeException(String.format("未检测到模块信息 %s", getClass().getName()));
        this.texts = moduleInfo.name();
        this.key = moduleInfo.key();
        this.enable = moduleInfo.enable();
        setEnable(enable);
    }


    protected void registerSetting(Setting<?>... settings) {
        try {
            registerSetting(this, settings);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }
    public String getDescription() {
        return "None";
    }
    public String getTag() {
        return null;
    }
    public void toggle() {
        setEnable(!isEnable());
    }
    public void setEnable(boolean enable) {
        this.enable = enable;
        if(enable) {
            EventManager.register(this);
            onEnable();
        }else {
            EventManager.unregister(this);
            onDisable();
        }

    }
    public String getNameKey() {
        return Language.getLabel(getTexts(), Language.getDefaultLanguage());
    }

    public String getName() {
        return Language.getLabel(getTexts(), Language.getLanguage());
    }

    protected void onEnable() {}

    protected void onDisable() {}

    public void cleanup() {

    }
}
