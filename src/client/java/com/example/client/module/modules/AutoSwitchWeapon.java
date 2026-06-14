package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.ZombiesGuns;
import com.example.client.config.AutoSwitchWeaponConfig;
import com.example.client.events.TickEvent;
import com.example.client.gui.AutoSwitchWeaponScreen;
import com.example.client.gui.ZombiesConfigScreen;
import com.example.client.language.Language;
import com.example.client.language.Text;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.settings.ButtonSetting;
import com.example.client.setting.settings.ModeSetting;
import com.example.client.setting.settings.NumberSetting;
import com.example.client.utils.TimeUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

@ModuleInfo(name = {
        @Text(label = "Auto Switch Weapon", language = Language.English),
        @Text(label = "自动切换武器", language = Language.Chinese)
}, enable = true)
public class AutoSwitchWeapon extends AbstractModule {
    @SettingInfo(name = {
            @Text(label = "Delay Mode", language = Language.English)
    })
    public static final ModeSetting delayMode = new ModeSetting("All", Arrays.asList("All", "Manual"));

    @SettingInfo(name = {
            @Text(label = "Switch Delay", language = Language.English)
    })
    public static final NumberSetting switchDelay = new NumberSetting(200, 10, 1000, "#");


    @SettingInfo(name = {
            @Text(label = "Guns Config", language = Language.English),
            @Text(label = "枪械配置", language = Language.English)
    })
    public static final ButtonSetting gunsConfig = new ButtonSetting() {
        @Override
        public void onClickedButton() {
            if (AutoSwitchWeaponScreen.instance == null) {
                AutoSwitchWeaponScreen.instance = new AutoSwitchWeaponScreen(ZombiesConfigScreen.instance);
            }
            mc.setScreen(AutoSwitchWeaponScreen.instance);
        }
    };


    public AutoSwitchWeapon() {
        registerSetting(delayMode, switchDelay, gunsConfig);
    }

    private TimeUtils timeUtils = new TimeUtils();
    private static boolean lastUseDown = false;

    @EventTarget
    public void onClick(TickEvent event) {
        if (mc.screen != null) {
            timeUtils.reset();
            return;
        }
        boolean useDown = mc.options.keyUse.isDown();

        if (!useDown) {
            timeUtils.reset();
            return;
        }

        // 右键刚按下，先等几 tick，让当前枪先开火
        if (!lastUseDown) {
            timeUtils.reset();
            lastUseDown = true;
            return;
        }


        switchToNextGun();

    }

    private void switchToNextGun() {
        ItemStack current = mc.player.getMainHandItem();

        if (!ZombiesGuns.isZombiesGun(current)) {
            return;
        }

        int currentSlot = mc.player.getInventory().getSelectedSlot();

        int nextSlot = findNextUsableGunSlot(currentSlot);

        if (nextSlot == -1 || nextSlot == currentSlot) {
            return;
        }

        setSelectedSlot(nextSlot);
    }

    private int findNextUsableGunSlot(int currentSlot) {
        for (int i = 1; i <= 9; i++) {
            int slot = (currentSlot + i) % 9;
            ItemStack stack = mc.player.getInventory().getItem(slot);

            if (!ZombiesGuns.isZombiesGun(stack)) {
                continue;
            }

            if (isReloadingGun(stack)) {
                continue;
            }
            ZombiesGuns gun = ZombiesGuns.getGunOrNull(stack);
            AutoSwitchWeaponConfig.GunSwitchSetting config = AutoSwitchWeaponConfig.get(gun);
            if (config == null) continue;
            if (!config.isEnabled()) continue;

            if (delayMode.is("All")) {
                if (!timeUtils.hasTimeElapsed(switchDelay.getValue().longValue(), true))
                    continue;
            } else {


            }

            return slot;
        }

        return -1;
    }


    private static boolean isReloadingGun(ItemStack stack) {
        if (!ZombiesGuns.isZombiesGun(stack)) return false;
        if (!stack.isDamageableItem()) {
            return false;
        }

        //耐久不是满的,就是在换弹
        return stack.getDamageValue() > 0;
    }


    private static void setSelectedSlot(int slot) {
        if (slot < 0 || slot > 8)
            return;
        mc.player.getInventory().setSelectedSlot(slot);
    }


}
