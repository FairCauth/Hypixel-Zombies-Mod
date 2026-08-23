package com.example.client.module;

import com.darkmagician6.eventapi.EventManager;
import com.example.client.language.GuiText;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.Setting;
import com.example.client.setting.SettingManager;
import com.example.client.utils.ChatUtils;
import com.example.client.utils.IMinecraft;
import com.example.client.utils.render.ToastUtils;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public class AbstractModule extends SettingManager implements IMinecraft {
    @Getter
    private final String textKey;

    @Getter
    @Setter
    private int key;
    @Getter
    private boolean enable;
    public AbstractModule() {
        ModuleInfo moduleInfo = this.getClass().getAnnotation(ModuleInfo.class);
        if (moduleInfo == null)
            throw new RuntimeException(String.format("未检测到模块信息 %s", getClass().getName()));
        this.textKey = moduleInfo.name();
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
                ChatUtils.print(Component.literal(getName()).withStyle(ChatFormatting.AQUA)
                    .append(GuiText.text("chat.enabled").copy().withStyle(ChatFormatting.GRAY)));
//                ToastUtils.show("Module", ChatFormatting.AQUA + getName() +ChatFormatting.GRAY+ " was " + ChatFormatting.GREEN +"Enabled");
            }
            onEnable();
        }else {
            EventManager.unregister(this);
            if(mc.player != null)
            {
                ChatUtils.print(Component.literal(getName()).withStyle(ChatFormatting.AQUA)
                    .append(GuiText.text("chat.disabled").copy().withStyle(ChatFormatting.GRAY)));
//                ToastUtils.show("Module", ChatFormatting.AQUA + getName() +ChatFormatting.GRAY+ " was " + ChatFormatting.RED +"Disabled");
            }

            onDisable();
        }

    }
    public String getNameKey() {
        return textKey;
    }

    public String getName() {
        return GuiText.textString(textKey);
    }

    protected void onEnable() {}

    protected void onDisable() {}

    public void cleanup() {

    }
}
