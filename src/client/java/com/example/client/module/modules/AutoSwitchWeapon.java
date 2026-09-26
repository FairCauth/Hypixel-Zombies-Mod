package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.data.ZombiesGuns;
import com.example.client.config.AutoSwitchWeaponConfig;
import com.example.client.events.TickEvent;
import com.example.client.gui.AutoSwitchWeaponScreen;
import com.example.client.gui.ZombiesConfigScreen;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.attribute.SettingAttribute;
import com.example.client.setting.settings.BooleanSetting;
import com.example.client.setting.settings.ButtonSetting;
import com.example.client.setting.settings.HotbarSlotSetting;
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

    // -- 快捷栏模式：各槽位（激活状态 + 绑键合一） --

    @SettingInfo(name = "setting.hotbar_slot_2_key")
    public static final HotbarSlotSetting slot2 = new HotbarSlotSetting(true, 0);

    @SettingInfo(name = "setting.hotbar_slot_3_key")
    public static final HotbarSlotSetting slot3 = new HotbarSlotSetting(true, 0);

    @SettingInfo(name = "setting.hotbar_slot_4_key")
    public static final HotbarSlotSetting slot4 = new HotbarSlotSetting(false, 0);

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
        @Override
        public boolean isDisplay() {
            return switchType.is("Weapon") && delayMode.is("Cooldown");
        }
    };

    // -- 切换类型：武器识别（默认）/ 快捷栏 --

    @SettingInfo(name = "setting.switch_type")
    public static final ModeSetting switchType = new ModeSetting(
            "Weapon", Arrays.asList("Weapon", "Hotbar"),
            new SettingAttribute<>(slot2, "Hotbar"),
            new SettingAttribute<>(slot3, "Hotbar"),
            new SettingAttribute<>(slot4, "Hotbar")
    );

    @SettingInfo(name = "setting.delay_mode")
    public static final ModeSetting delayMode = new ModeSetting("Interval", Arrays.asList("Interval", "Cooldown"),
            new SettingAttribute<>(switchDelay, "Interval")
    );

    @SettingInfo(name = "setting.auto_reload_durability_1")
    public static final BooleanSetting autoReload = new BooleanSetting(false);

    public AutoSwitchWeapon() {
        registerSetting(switchType, delayMode, gunsConfig, autoReload);
    }

    private final TimeUtils timeUtils = new TimeUtils();
    private final TimeUtils holdTimer = new TimeUtils();
    private static boolean lastUseDown = false;

    // Cooldown 模式：记录每把枪上次被切到的时间戳
    private final EnumMap<ZombiesGuns, Long> lastSwitchMs = new EnumMap<>(ZombiesGuns.class);
    // 快捷栏模式：每个槽位（索引0=槽2,1=槽3,2=槽4）上次被切到的时间戳
    private final long[] lastHotbarSwitchMs = {0L, 0L, 0L};

    @EventTarget
    public void onClick(TickEvent event) {

//        if(!PlayerUtils.isInHypZombies()) return;

        if (mc.gui.screen() != null) {
            timeUtils.reset();
            return;
        }
        boolean useDown = mc.options.keyUse.isDown();

        if (!useDown) {
            lastUseDown = false;
            timeUtils.reset();
            return;
        }

        // 右键刚按下，记录按下时刻，等待足够长的持续时间
        if (!lastUseDown) {
            holdTimer.reset();
            timeUtils.reset();
            lastUseDown = true;
            return;
        }

        // 持续按住不足 200ms，不切换
        if (!holdTimer.hasTimeElapsed(200, false)) {
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

        if (switchType.is("Hotbar")) {
            switchToNextHotbarSlot();
        } else {
            switchToNextGun();
        }
    }

    // ── 武器识别模式 ──────────────────────────────────────────────────────────

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

        ItemStack nextStack = mc.player.getInventory().getItem(nextSlot);
        boolean reload = needsReload(nextStack);
        setSelectedSlot(nextSlot);
        if (reload) {
            KeyMapping.click(mc.options.keyAttack.getDefaultKey());
        }
    }

    private int findNextUsableGunSlot(int currentSlot) {
        long now = System.currentTimeMillis();

        for (int i = 1; i <= 9; i++) {
            int slot = (currentSlot + i) % 9;
            ItemStack stack = mc.player.getInventory().getItem(slot);

            if (!ZombiesGuns.isZombiesGun(stack)) continue;
            if (isReloadingGun(stack) && !needsReload(stack)) continue;

            ZombiesGuns gun = ZombiesGuns.getGunOrNull(stack);
            AutoSwitchWeaponConfig.GunSwitchSetting config = AutoSwitchWeaponConfig.get(gun);
            if (config == null || !config.isEnabled()) continue;

            if (delayMode.is("Cooldown")) {
                long last = lastSwitchMs.getOrDefault(gun, 0L);
                if (now - last < AutoSwitchWeaponConfig.getSwitchDelay(stack)) continue;
            } else {
                if (!timeUtils.hasTimeElapsed(switchDelay.getValue().longValue(), true)) return -1;
            }
            return slot;
        }
        return -1;
    }

    // ── 快捷栏模式 ────────────────────────────────────────────────────────────

    private void switchToNextHotbarSlot() {
        int currentSlot = mc.player.getInventory().getSelectedSlot();
        // 仅当当前槽位在 2/3/4（索引 1/2/3）时才触发切换
        if (currentSlot < 1 || currentSlot > 3) return;
        int nextSlot = findNextUsableHotbarSlot(currentSlot);

        if (nextSlot == -1 || nextSlot == currentSlot) return;

        // 记录切换时间
        lastHotbarSwitchMs[nextSlot - 1] = System.currentTimeMillis();

        ItemStack nextStack = mc.player.getInventory().getItem(nextSlot);
        boolean reload = needsReload(nextStack);
        setSelectedSlot(nextSlot);
        if (reload) {
            KeyMapping.click(mc.options.keyAttack.getDefaultKey());
        }
    }

    private int findNextUsableHotbarSlot(int currentSlot) {
        // 快捷栏 2、3、4 对应背包索引 1、2、3
        int[] slots = {1, 2, 3};
        boolean[] actives = {slot2.isActive(), slot3.isActive(), slot4.isActive()};
        long now = System.currentTimeMillis();
        long delay = switchDelay.getValue().longValue();

        int startIdx = 0;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == currentSlot) { startIdx = i; break; }
        }

        for (int i = 1; i <= slots.length; i++) {
            int idx = (startIdx + i) % slots.length;
            if (!actives[idx]) continue;
            if (slots[idx] == currentSlot) continue;
            if (delayMode.is("Cooldown")) {
                if (now - lastHotbarSwitchMs[idx] < delay) continue;
            } else {
                if (!timeUtils.hasTimeElapsed(delay, true)) return -1;
            }
            return slots[idx];
        }
        return -1;
    }

    // ── 公共工具 ──────────────────────────────────────────────────────────────

    /** 切到这把枪后是否需要左键换弹：剩余耐久=1。 */
    private boolean needsReload(ItemStack stack) {
        if (!autoReload.getValue() || !ZombiesGuns.isZombiesGun(stack)) return false;
        return stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() == 1;
    }

    private static boolean isReloadingGun(ItemStack stack) {
        if (!ZombiesGuns.isZombiesGun(stack)) return false;
        if (!stack.isDamageableItem()) return false;
        return stack.getDamageValue() > 0;
    }

    private static void setSelectedSlot(int slot) {
        if (slot < 0 || slot > 8) return;
        mc.player.getInventory().setSelectedSlot(slot);
    }
}
