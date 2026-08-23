package com.example.client.module.modules;

import com.example.client.ZombiesModClient;
import com.example.client.language.Language;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.settings.BooleanSetting;

/**
 * 全息字穿透（类似 ZHF / Zombies Holograms-bug Fix）。
 *
 * Hypixel 僵尸末日里那些漂浮文字（门价、机器提示、INSTA/MAX 道具字样等）都是隐形盔甲架
 * （ArmorStand）。准星射线在判定“你瞄到了谁”时会先撞上离你更近的盔甲架，导致：
 *   - 右键被当成“与盔甲架交互”而不是“使用物品（开枪）”，枪打不出去 / 卡手；
 *   - 命中判定被空气挡住。
 *
 * 本模块开启后，让准星实体射线无视所有盔甲架（见 GameRendererPickMixin），
 * 这样 hitResult 不会是盔甲架，右键正常落到 useItem（开枪）/ 后面的方块上。
 */
@ModuleInfo(name = "module.hologram_fix", enable = false)
public class HologramFix extends AbstractModule {

    @SettingInfo(name = "setting.only_in_zombies")
    public static final BooleanSetting onlyGame = new BooleanSetting(true);

    public HologramFix() {
        registerSetting(onlyGame);
    }

    /** 供 mixin 静态查询：模块是否启用。moduleManager 早期可能为 null，需判空。 */
    public static boolean isActive() {
        if (ZombiesModClient.moduleManager == null) return false;
        AbstractModule m = ZombiesModClient.moduleManager.getModule("Hologram Fix");
        return m != null && m.isEnable();
    }
}
