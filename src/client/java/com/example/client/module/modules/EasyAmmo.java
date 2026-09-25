package com.example.client.module.modules;

import com.example.client.ZombiesModClient;
import com.example.client.language.Language;
import com.example.client.language.Text;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.settings.BooleanSetting;
import com.example.client.setting.settings.NumberSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(name = {
        @Text(label = "Easy Ammo", language = Language.English),
        @Text(label = "更容易补弹", language = Language.Chinese)
}, enable = false)
public class EasyAmmo extends AbstractModule {
    private static final Vec3 AMMO_STAND_POSITION = new Vec3(-5.0D, 72.0D, 27.0D);
    private static final double AMMO_STAND_SEARCH_RADIUS_SQR = 3.0D * 3.0D;

    @SettingInfo(name = {
            @Text(label = "Range", language = Language.English),
            @Text(label = "距离", language = Language.Chinese)
    })
    public static final NumberSetting range = new NumberSetting(5.0D, 3.0D, 6.0D, "#.0");

    @SettingInfo(name = {
            @Text(label = "Show AABB", language = Language.English),
            @Text(label = "显示点击范围", language = Language.Chinese)
    })
    public static final BooleanSetting showAABB = new BooleanSetting(true);

    public EasyAmmo() {
        registerSetting(range, showAABB);
    }

    public static boolean isActive() {
        if (ZombiesModClient.moduleManager == null) return false;
        AbstractModule module = ZombiesModClient.moduleManager.getModule("Easy Ammo");
        return module != null && module.isEnable();
    }

    public static boolean shouldShowAABB(Entity entity) {
        return isActive()
                && showAABB.getValue()
                && mc.level != null
                && entity == findAmmoStand();
    }

    /**
     * 当前准星先命中玩家时，沿同一视线重新检测只包含盔甲架的目标。
     * ProjectileUtil 同时处理方块遮挡，因此不会隔墙选择盔甲架。
     */
    public static EntityHitResult findArmorStandBehindPlayer(HitResult blockedHit) {
        if (!isActive()
                || mc.player == null
                || mc.level == null
                || !(blockedHit instanceof EntityHitResult playerHit)
                || !(playerHit.getEntity() instanceof Player)
                || playerHit.getEntity() == mc.player) {
            return null;
        }

        ArmorStand ammoStand = findAmmoStand();
        if (ammoStand == null) return null;

        HitResult result = ProjectileUtil.getHitResultOnViewVector(
                mc.player,
                entity -> entity == ammoStand,
                range.getValue().doubleValue()
        );
        if (!(result instanceof EntityHitResult armorStandHit)
                || !(armorStandHit.getEntity() instanceof ArmorStand)) {
            return null;
        }

        Vec3 eye = mc.player.getEyePosition();
        double playerDistance = eye.distanceToSqr(playerHit.getLocation());
        double armorStandDistance = eye.distanceToSqr(armorStandHit.getLocation());

        // 只处理被玩家挡住的盔甲架，避免劫持位于玩家前方的其他盔甲架交互。
        return armorStandDistance + 1.0E-4D >= playerDistance ? armorStandHit : null;
    }

    private static ArmorStand findAmmoStand() {
        ArmorStand closest = null;
        double closestDistance = AMMO_STAND_SEARCH_RADIUS_SQR;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand armorStand) || !armorStand.isAlive()) continue;

            double distance = armorStand.position().distanceToSqr(AMMO_STAND_POSITION);
            if (distance < closestDistance) {
                closest = armorStand;
                closestDistance = distance;
            }
        }
        return closest;
    }
}
