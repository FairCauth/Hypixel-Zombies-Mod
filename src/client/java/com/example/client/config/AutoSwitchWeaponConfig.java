package com.example.client.config;
import com.example.client.ZombiesGuns;
import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.Map;

public class AutoSwitchWeaponConfig {

    public static final EnumMap<ZombiesGuns, GunSwitchSetting> GUN_SETTINGS = new EnumMap<>(ZombiesGuns.class);

    static {
        for (ZombiesGuns gun : ZombiesGuns.values()) {
            GUN_SETTINGS.put(gun, new GunSwitchSetting(true, 120));
        }
    }

    public static GunSwitchSetting get(ZombiesGuns gun) {
        return GUN_SETTINGS.computeIfAbsent(gun, g -> new GunSwitchSetting(true, 120));
    }

    public static boolean shouldSwitch(ItemStack stack) {
        ZombiesGuns gun = ZombiesGuns.getGunOrNull(stack);
        if (gun == null) return false;
        return get(gun).isEnabled();
    }

    public static int getSwitchDelay(ItemStack stack) {
        ZombiesGuns gun = ZombiesGuns.getGunOrNull(stack);
        if (gun == null) return 120;
        return get(gun).getDelayMs();
    }

    public static void saveTo(JsonObject root) {
        JsonObject autoSwitchJson = new JsonObject();

        for (Map.Entry<ZombiesGuns, GunSwitchSetting> entry : GUN_SETTINGS.entrySet()) {
            JsonObject gunJson = new JsonObject();

            gunJson.addProperty("enabled", entry.getValue().isEnabled());
            gunJson.addProperty("delayMs", entry.getValue().getDelayMs());

            autoSwitchJson.add(entry.getKey().name(), gunJson);
        }

        root.add("autoSwitchWeapon", autoSwitchJson);
    }

    public static void loadFrom(JsonObject root) {
        if (root == null || !root.has("autoSwitchWeapon") || !root.get("autoSwitchWeapon").isJsonObject()) {
            return;
        }

        JsonObject autoSwitchJson = root.getAsJsonObject("autoSwitchWeapon");

        for (ZombiesGuns gun : ZombiesGuns.values()) {
            if (!autoSwitchJson.has(gun.name()) || !autoSwitchJson.get(gun.name()).isJsonObject()) {
                continue;
            }

            JsonObject gunJson = autoSwitchJson.getAsJsonObject(gun.name());
            GunSwitchSetting setting = get(gun);

            if (gunJson.has("enabled")) {
                setting.setEnabled(gunJson.get("enabled").getAsBoolean());
            }

            if (gunJson.has("delayMs")) {
                setting.setDelayMs(gunJson.get("delayMs").getAsInt());
            }
        }
    }

    @Getter
    @Setter
    public static class GunSwitchSetting {
        private boolean enabled;
        private int delayMs;

        public GunSwitchSetting(boolean enabled, int delayMs) {
            this.enabled = enabled;
            this.delayMs = delayMs;
        }
    }
}
