package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.data.ZombiesGuns;
import com.example.client.config.AutoSwitchWeaponConfig;
import com.example.client.events.TickEvent;
import com.example.client.gui.AutoSwitchWeaponScreen;
import com.example.client.gui.ZombiesConfigScreen;
import com.example.client.language.Language;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.attribute.SettingAttribute;
import com.example.client.setting.settings.BooleanSetting;
import com.example.client.setting.settings.ButtonSetting;
import com.example.client.setting.settings.ModeSetting;
import com.example.client.setting.settings.NumberSetting;
import com.example.client.utils.TimeUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

import java.util.Arrays;
import java.util.EnumMap;

@ModuleInfo(name = "module.auto_switch_weapon", enable = true)
public class AutoSwitchWeapon extends AbstractModule {


    @SettingInfo(name = "setting.switch_delay")
    public static final NumberSetting switchDelay = new NumberSetting(200, 10, 1000, "#");


    @SettingInfo(name = "setting.guns_config")
    public static final ButtonSetting gunsConfig = new ButtonSetting() {
        @Override
        public void onClickedButton() {
            if (AutoSwitchWeaponScreen.instance == null) {
                AutoSwitchWeaponScreen.instance = new AutoSwitchWeaponScreen(ZombiesConfigScreen.instance);
            }
            mc.gui.setScreen(AutoSwitchWeaponScreen.instance);
        }
    };

    @SettingInfo(name = "setting.delay_mode")
    // Interval全局线性间隔 Cooldown = 每把枪独立冷
    public static final ModeSetting delayMode = new ModeSetting("Interval", Arrays.asList("Interval", "Cooldown"),
            new SettingAttribute<>(switchDelay, "Interval"),
            new SettingAttribute<>(gunsConfig, "Cooldown")
    );
    @SettingInfo(name = "setting.auto_reload_durability_1")
    public static final BooleanSetting autoReload = new BooleanSetting(false);

    public AutoSwitchWeapon() {
        registerSetting(delayMode, autoReload);
    }

    private TimeUtils timeUtils = new TimeUtils();
    private static boolean lastUseDown = false;

    // Manual 每把枪独立冷却,记录每把枪上次被切到的时间戳
    private final EnumMap<ZombiesGuns, Long> lastSwitchMs = new EnumMap<>(ZombiesGuns.class);

    @EventTarget
    public void onClick(TickEvent event) {

//        if(!PlayerUtils.isInHypZombies()) return;

        if (mc.gui.screen() != null) {
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

        if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof ArmorStand) {
            timeUtils.reset();
            return;
        }
        if (mc.hitResult instanceof BlockHitResult bhr && mc.level != null
                && mc.level.getBlockState(bhr.getBlockPos()).getBlock() instanceof ChestBlock) {
            timeUtils.reset();
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

        // 记录这把枪被切到的时间，开始它自己的冷却
        ItemStack nextStack = mc.player.getInventory().getItem(nextSlot);
        ZombiesGuns nextGun = ZombiesGuns.getGunOrNull(nextStack);
        if (nextGun != null) {
            lastSwitchMs.put(nextGun, System.currentTimeMillis());
        }

        // 切到"需要换弹"的枪（耐久=1，或耐久满但弹夹只剩 1 发）→ 同一流程里立刻左键换弹
        boolean reload = needsReload(nextStack);

        setSelectedSlot(nextSlot);

        if (reload) {
            KeyMapping.click(mc.options.keyAttack.getDefaultKey()); // 左键 = 换弹
        }
    }

    private int findNextUsableGunSlot(int currentSlot) {
        long now = System.currentTimeMillis();

        for (int i = 1; i <= 9; i++) {
            int slot = (currentSlot + i) % 9;
            ItemStack stack = mc.player.getInventory().getItem(slot);

            if (!ZombiesGuns.isZombiesGun(stack)) {
                continue;
            }

            if (isReloadingGun(stack)) {
                if (!needsReload(stack)) {
                    continue;
                }
            }
            ZombiesGuns gun = ZombiesGuns.getGunOrNull(stack);
            AutoSwitchWeaponConfig.GunSwitchSetting config = AutoSwitchWeaponConfig.get(gun);
            if (config == null) continue;
            if (!config.isEnabled()) continue;

            if (delayMode.is("Cooldown")) {
                long last = lastSwitchMs.getOrDefault(gun, 0L);
                if (now - last < AutoSwitchWeaponConfig.getSwitchDelay(stack)) {
                    continue;
                }
            } else {
                //线性间隔模式
                if (!timeUtils.hasTimeElapsed(switchDelay.getValue().longValue(), true)) {
                    return -1;
                }
            }
            return slot;
        }

        return -1;
    }


    /** 切到这把枪后是否需要左键换弹：剩余耐久=1（耐久条剩 1），或耐久满但弹夹只剩 1 发。 */
    private boolean needsReload(ItemStack stack) {
        if (!autoReload.getValue() || !ZombiesGuns.isZombiesGun(stack)) return false;
        if (stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() == 1)
            return true; // 剩余耐久（maxDamage - damageValue）= 1，即耐久条只剩 1

//        if (stack.getDamageValue() == 0 && stack.getCount() == 1)
//            return true;     // 耐久满 + 只剩 1 发

        return false;
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
