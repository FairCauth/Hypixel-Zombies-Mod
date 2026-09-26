package com.example.client.setting.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 快捷栏槽位设置：同时持有"是否激活"状态和"开关绑键"（GLFW key code）。
 * 激活状态默认为 true，绑键默认 0（未绑定）。
 * JSON 格式：{"active": true, "key": 0}
 */
public class HotbarSlotSetting extends KeyBindSetting {

    private boolean active;

    public HotbarSlotSetting(boolean defaultActive, int defaultKey) {
        super(defaultKey);
        this.active = defaultActive;
    }

    public boolean isActive() { return active; }

    public void setActive(boolean active) { this.active = active; }

    public void toggleActive() { this.active = !this.active; }

    @Override
    public boolean canSaveConfig() { return true; }

    /** 序列化为 {"active":…,"key":…} 的 JSON 字符串，由 ZombiesConfig 存取。 */
    @Override
    public String getConfigValue() {
        JsonObject obj = new JsonObject();
        obj.addProperty("active", active);
        obj.addProperty("key", getValue());
        return obj.toString();
    }

    /** 从 JSON 读回时，只处理 key 整数；active 字段由 loadJson 另行处理。 */
    @Override
    public Integer getJson(JsonElement jsonElement) {
        if (jsonElement.isJsonObject()) {
            JsonObject obj = jsonElement.getAsJsonObject();
            if (obj.has("active")) {
                active = obj.get("active").getAsBoolean();
            }
            if (obj.has("key")) {
                return obj.get("key").getAsInt();
            }
            return getValue();
        }
        // 旧格式兼容：纯整数
        return jsonElement.getAsInt();
    }
}
