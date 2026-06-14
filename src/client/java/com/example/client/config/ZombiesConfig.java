package com.example.client.config;

import com.example.client.ZombiesModClient;
import com.example.client.module.AbstractModule;
import com.example.client.setting.Setting;
import com.example.client.setting.SettingManager;
import com.example.client.setting.settings.BooleanSetting;
import com.example.client.setting.settings.ModeSetting;
import com.example.client.setting.settings.NumberSetting;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ZombiesConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("zombies-mod.json");



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

            if (root.has("modEnabled")) {
                ZombiesModClient.guiKey = root.get("guiKey").getAsInt();
            }

            if (root.has("guiKey")) {
                ZombiesModClient.guiKey = root.get("guiKey").getAsInt();
            }

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

            JsonObject modulesJson = new JsonObject();

            if (ZombiesModClient.moduleManager != null) {
                for (AbstractModule module : ZombiesModClient.moduleManager.getModuleList()) {
                    JsonObject moduleJson = new JsonObject();

                    moduleJson.addProperty("enabled", module.isEnable());

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

            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(root));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void saveSettingValue(JsonObject settingsJson, String key, Setting<?> setting) {
        Object value = setting.getValue();

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