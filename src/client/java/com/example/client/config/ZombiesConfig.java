package com.example.client.config;

import com.example.client.ZombiesModClient;
import com.example.client.module.AbstractModule;
import com.example.client.setting.Setting;
import com.example.client.setting.SettingManager;
import com.example.client.setting.settings.BooleanSetting;
import com.example.client.setting.settings.KeySetting;
import com.example.client.setting.settings.ModeSetting;
import com.example.client.setting.settings.NumberSetting;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ZombiesConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("zombies-mod.json");

    /** Hypixel API key（战绩查询用） */
    public static String apiKey = "";

    /** 被保护的名字列表（未自定义时显示为 Player1/Player2…） */
    public static final List<String> protectedNames = new ArrayList<>();

    /** 每个受保护名字的显示设置；key 为名单里的真实用户名。 */
    public static final Map<String, NameProtectSettings> protectedNameSettings = new LinkedHashMap<>();

    public static final class NameProtectSettings {
        /** 是否替换玩家名字本身；关闭后只保留前缀替换/颜色设置。 */
        private boolean renameName = true;
        private String customName = "";
        /** -1 代表沿用服务器/原文字颜色；其他值为 0xRRGGBB。 */
        private int nameColor = -1;
        /** -1 代表沿用原 rank 颜色；其他值只用于 '[' 和 ']'。 */
        private int bracketColor = -1;
        /** -1 代表沿用原 rank 颜色；其他值只用于 VIP/MVP 等 rank 字母。 */
        private int rankTextColor = -1;
        /** -1 代表沿用原 rank 颜色；其他值只用于 rank 中的 '+'。 */
        private int plusColor = -1;
        /** 空字符串代表保留原 Hypixel rank 前缀。 */
        private String prefix = "";

        public boolean isRenameName() {
            return renameName;
        }

        public void setRenameName(boolean renameName) {
            this.renameName = renameName;
        }

        public String getCustomName() {
            return customName;
        }

        public void setCustomName(String customName) {
            this.customName = customName == null ? "" : customName.trim();
        }

        public int getNameColor() {
            return nameColor;
        }

        public void setNameColor(int nameColor) {
            this.nameColor = nameColor;
        }

        public int getBracketColor() {
            return bracketColor;
        }

        public void setBracketColor(int bracketColor) {
            this.bracketColor = bracketColor;
        }

        public int getRankTextColor() {
            return rankTextColor;
        }

        public void setRankTextColor(int rankTextColor) {
            this.rankTextColor = rankTextColor;
        }

        public int getPlusColor() {
            return plusColor;
        }

        public void setPlusColor(int plusColor) {
            this.plusColor = plusColor;
        }

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix == null ? "" : prefix.trim();
        }
    }

    public static NameProtectSettings getNameProtectSettings(String name) {
        for (Map.Entry<String, NameProtectSettings> entry : protectedNameSettings.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        NameProtectSettings settings = new NameProtectSettings();
        protectedNameSettings.put(name, settings);
        return settings;
    }

    public static NameProtectSettings findNameProtectSettings(String name) {
        for (Map.Entry<String, NameProtectSettings> entry : protectedNameSettings.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public static void removeNameProtectSettings(String name) {
        protectedNameSettings.keySet().removeIf(key -> key.equalsIgnoreCase(name));
    }



    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }

        try {
            String json = Files.readString(CONFIG_PATH);

            if (json.isBlank()) {
                save();
                return;
            }

            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            if (root.has("guiKey")) {
                ZombiesModClient.guiKey = root.get("guiKey").getAsInt();
            }

            if (root.has("apiKey")) {
                apiKey = root.get("apiKey").getAsString();
            }

            if (root.has("protectedNames") && root.get("protectedNames").isJsonArray()) {
                protectedNames.clear();
                for (JsonElement el : root.getAsJsonArray("protectedNames")) {
                    protectedNames.add(el.getAsString());
                }
            }

            protectedNameSettings.clear();
            if (root.has("protectedNameSettings") && root.get("protectedNameSettings").isJsonObject()) {
                JsonObject settingsJson = root.getAsJsonObject("protectedNameSettings");
                for (Map.Entry<String, JsonElement> entry : settingsJson.entrySet()) {
                    if (!entry.getValue().isJsonObject()) continue;
                    JsonObject item = entry.getValue().getAsJsonObject();
                    NameProtectSettings settings = new NameProtectSettings();
                    if (item.has("renameName")) settings.setRenameName(item.get("renameName").getAsBoolean());
                    if (item.has("customName")) settings.setCustomName(item.get("customName").getAsString());
                    if (item.has("nameColor")) settings.setNameColor(item.get("nameColor").getAsInt());
                    if (item.has("bracketColor")) settings.setBracketColor(item.get("bracketColor").getAsInt());
                    if (item.has("rankTextColor")) settings.setRankTextColor(item.get("rankTextColor").getAsInt());
                    if (item.has("plusColor")) settings.setPlusColor(item.get("plusColor").getAsInt());
                    if (item.has("prefix")) settings.setPrefix(item.get("prefix").getAsString());
                    protectedNameSettings.put(entry.getKey(), settings);
                }
            }

            // 每把枪的开关/延迟配置
            AutoSwitchWeaponConfig.loadFrom(root);

            if (!root.has("modules") || !root.get("modules").isJsonObject()) {
                return;
            }

            JsonObject modulesJson = root.getAsJsonObject("modules");

            for (AbstractModule module : ZombiesModClient.moduleManager.getModuleList()) {
                String moduleKey = module.getNameKey();

                if (!modulesJson.has(moduleKey)) {
                    continue;
                }

                JsonObject moduleJson = modulesJson.getAsJsonObject(moduleKey);

                if (moduleJson.has("enabled")) {
                    boolean enabled = moduleJson.get("enabled").getAsBoolean();

                    if (module.isEnable() != enabled) {
                        module.toggle();
                    }
                }

                if (moduleJson.has("key")) {
                    module.setKey(moduleJson.get("key").getAsInt());
                }

                if (!moduleJson.has("settings") || !moduleJson.get("settings").isJsonObject()) {
                    continue;
                }

                JsonObject settingsJson = moduleJson.getAsJsonObject("settings");

                for (Setting<?> setting : SettingManager.getSettings(module)) {
                    String settingKey = setting.getNameKey();

                    if (!settingsJson.has(settingKey)) {
                        continue;
                    }

                    JsonElement value = settingsJson.get(settingKey);

                    loadSettingValue(setting, value);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void loadSettingValue(Setting setting, JsonElement value) {
        try {
            if (setting instanceof KeySetting keySetting) {
                keySetting.setValue(keySetting.getJson(value));
                return;
            }
            if (setting instanceof BooleanSetting booleanSetting) {
                booleanSetting.setValue(value.getAsBoolean());
                return;
            }

            if (setting instanceof NumberSetting numberSetting) {
                numberSetting.setValue(value.getAsDouble());
                return;
            }

            if (setting instanceof ModeSetting modeSetting) {
                modeSetting.setValue(value.getAsString());
            }
        } catch (Exception e) {
            System.err.println("[ZombiesConfig] Failed to load setting: " + setting.getNameKey());
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            JsonObject root = new JsonObject();

            root.addProperty("guiKey", ZombiesModClient.guiKey);
            root.addProperty("apiKey", apiKey);

            JsonArray namesArr = new JsonArray();
            for (String n : protectedNames) namesArr.add(n);
            root.add("protectedNames", namesArr);

            JsonObject nameProtectJson = new JsonObject();
            for (Map.Entry<String, NameProtectSettings> entry : protectedNameSettings.entrySet()) {
                NameProtectSettings settings = entry.getValue();
                JsonObject item = new JsonObject();
                item.addProperty("renameName", settings.isRenameName());
                item.addProperty("customName", settings.getCustomName());
                item.addProperty("nameColor", settings.getNameColor());
                item.addProperty("bracketColor", settings.getBracketColor());
                item.addProperty("rankTextColor", settings.getRankTextColor());
                item.addProperty("plusColor", settings.getPlusColor());
                item.addProperty("prefix", settings.getPrefix());
                nameProtectJson.add(entry.getKey(), item);
            }
            root.add("protectedNameSettings", nameProtectJson);

            JsonObject modulesJson = new JsonObject();

            if (ZombiesModClient.moduleManager != null) {
                for (AbstractModule module : ZombiesModClient.moduleManager.getModuleList()) {
                    JsonObject moduleJson = new JsonObject();

                    moduleJson.addProperty("enabled", module.isEnable());
                    moduleJson.addProperty("key", module.getKey());

                    JsonObject settingsJson = new JsonObject();

                    for (Setting<?> setting : SettingManager.getSettings(module)) {
                        String settingKey = setting.getNameKey();
                        saveSettingValue(settingsJson, settingKey, setting);
                    }

                    moduleJson.add("settings", settingsJson);

                    modulesJson.add(module.getNameKey(), moduleJson);
                }
            }

            root.add("modules", modulesJson);

            // 每把枪的开关/延迟配置
            AutoSwitchWeaponConfig.saveTo(root);

            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(root));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void saveSettingValue(JsonObject settingsJson, String key, Setting<?> setting) {
        Object value = setting.getValue();

        if (setting instanceof KeySetting keySetting) {
            settingsJson.addProperty(key, keySetting.getValue());
            return;
        }

        if (setting instanceof BooleanSetting) {
            settingsJson.addProperty(key, Boolean.TRUE.equals(value));
            return;
        }

        if (setting instanceof NumberSetting) {
            if (value instanceof Number number) {
                settingsJson.addProperty(key, number.doubleValue());
            }
            return;
        }

        if (setting instanceof ModeSetting) {
            settingsJson.addProperty(key, String.valueOf(value));
        }
    }

}
