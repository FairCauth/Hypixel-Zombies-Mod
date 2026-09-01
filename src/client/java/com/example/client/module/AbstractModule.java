package com.example.client.module;

import com.darkmagician6.eventapi.EventManager;
import com.example.client.language.Language;
import com.example.client.language.Text;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.notification.NotificationManager;
import com.example.client.setting.Setting;
import com.example.client.setting.SettingManager;
import com.example.client.utils.ChatUtils;
import com.example.client.utils.IMinecraft;
import com.example.client.utils.render.ToastUtils;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.ChatFormatting;

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
            if(mc.player != null)
            {

//                NotificationManager.info("Module", "这是一条普通通知");

                NotificationManager.info("Module", ChatFormatting.AQUA + getName() +ChatFormatting.GRAY+ " was " + ChatFormatting.GREEN +"Enabled");
                ChatUtils.print(ChatFormatting.AQUA + getName() +ChatFormatting.GRAY+ " was " + ChatFormatting.GREEN +"Enabled");
//                ToastUtils.show("Module", ChatFormatting.AQUA + getName() +ChatFormatting.GRAY+ " was " + ChatFormatting.GREEN +"Enabled");
            }
            onEnable();
        }else {
            EventManager.unregister(this);
            if(mc.player != null)
            {
//                NotificationManager.warning("警告", "队友生命值过低");
                NotificationManager.info("Module", ChatFormatting.AQUA + getName() +ChatFormatting.GRAY+ " was " + ChatFormatting.RED +"Disabled");
                ChatUtils.print(ChatFormatting.AQUA + getName() +ChatFormatting.GRAY+ " was " + ChatFormatting.RED +"Disabled");
//                ToastUtils.show("Module", ChatFormatting.AQUA + getName() +ChatFormatting.GRAY+ " was " + ChatFormatting.RED +"Disabled");
            }

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
