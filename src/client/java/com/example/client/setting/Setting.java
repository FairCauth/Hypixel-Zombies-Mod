package com.example.client.setting;

import com.google.gson.JsonObject;

import com.example.client.language.Language;
import com.example.client.language.GuiText;
import com.example.client.setting.attribute.SettingAttribute;
import com.google.gson.JsonElement;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class Setting<T> {
    @Getter
    @Setter
    private String textKey;

    protected T value;
    @Setter
    @Getter
    public int level = 0;

    @Setter
    @Getter
    private Setting<?> parent = null;
    private boolean display = true;
    @Getter
    private final List<Setting<?>> treeSetting = new ArrayList<>();

    public Setting(T value) {
        this.value = value;
    }

    @SafeVarargs
    public Setting(T value, SettingAttribute<T>... settingAttributes) {
        this.value = value;
        Arrays.stream(settingAttributes).forEach(it -> {
            it.setting().setParent(this);
            it.setting().setDisplay(it.get());
            SettingManager.attributeMap.put(it.setting(), it);
            this.treeSetting.add(it.setting());


        });
    }

    public String getName(Language language) {
        return GuiText.textString(textKey);
    }
    public String getName() {
        return GuiText.textString(textKey);
    }
    public String getNameKey() {
        return textKey;
    }

    public boolean isDisplay() {
        return display;
    }

    public void setDisplay(boolean display) {
        this.display = display;

    }

    public void setValue(T value) {
        if (onValueChanging()) {
            this.value = value;
            onValueChangedFirst();
        }
    }

    public boolean canSaveConfig() {
        return false;
    }

    public T getValue() {
        return value;
    }

    public String getConfigValue() {
        return value.toString();
    }

    protected boolean onValueChanging() {
        return true;
    }

    private void onValueChangedFirst() {
        SettingManager.updateDisplay(SettingManager.getSettings());
        onValueChanged();
    }
    public int getAncestorCount() {
        int cnt = 0;
        Setting<?> p = this.parent;
        while (p != null) {
            cnt++;
            p = p.parent;
        }
        return cnt;
    }
    protected void onValueChanged() {

    }

    public T getJson(JsonElement jsonElement) {
        return null;
    }


}
