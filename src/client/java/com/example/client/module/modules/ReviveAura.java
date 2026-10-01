package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.events.TickEvent;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.settings.NumberSetting;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(name = "module.revive_aura", enable = false)
public class ReviveAura extends AbstractModule {

    @SettingInfo(name = "setting.distance")
    public static final NumberSetting range = new NumberSetting(4.5, 1.0, 10.0, "#.#");

    @SettingInfo(name = "setting.interval")
    public static final NumberSetting interval = new NumberSetting(200, 50, 1000, "#");

    private long lastReviveMs = 0L;

    public ReviveAura() {
        registerSetting(range, interval);
    }

    @Override
    protected void onEnable() {
        lastReviveMs = 0L;
    }

    @Override
    protected void onDisable() {
        lastReviveMs = 0L;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null || mc.gui.screen() != null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastReviveMs < interval.getValue().longValue()) {
            return;
        }

        if (mc.getConnection() == null) {
            return;
        }

        double rangeLimit = range.getValue().doubleValue();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Player player)) {
                continue;
            }
            if (player == mc.player) {
                continue;
            }
            if (!isDownCandidate(player)) {
                continue;
            }
            if (mc.player.distanceTo(player) > rangeLimit) {
                continue;
            }

            Vec3 eye = mc.player.getEyePosition();
            Vec3 rel = player.getBoundingBox().clip(eye, player.position())
                    .orElse(Vec3.ZERO)
                    .subtract(player.getX(), player.getY(), player.getZ());

            mc.getConnection().send(new ServerboundInteractPacket(player.getId(), InteractionHand.MAIN_HAND, rel, false));
            lastReviveMs = now;
            break;
        }
    }

    private static boolean isDownCandidate(Player player) {
        return player.isSleeping() || player.getPose() == Pose.SLEEPING;
    }
}
