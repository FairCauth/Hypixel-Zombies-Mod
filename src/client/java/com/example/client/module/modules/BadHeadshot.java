package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.ZombiesModClient;
import com.example.client.events.EntityLoadEvent;
import com.example.client.events.TickEvent;
import com.example.client.language.Language;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.settings.BooleanSetting;
import com.example.client.setting.settings.ColorSetting;
import com.example.client.setting.settings.NumberSetting;
import com.example.client.tracker.ServerTracker;
import com.example.client.utils.PlayerUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ModuleInfo(name = "module.bad_headshot", enable = false)
public class BadHeadshot extends AbstractModule {

    @SettingInfo(name = "setting.only_in_zombies")
    public static final BooleanSetting onlyGame = new BooleanSetting(true);

    @SettingInfo(name = "setting.distance")
    public static final NumberSetting distance = new NumberSetting(70, 10, 100, "#");

    @SettingInfo(name = "setting.target_color")
    public static final ColorSetting targetColor = new ColorSetting(new Color(255, 85, 85, 255));

    @SettingInfo(name = "setting.line_color")
    public static final ColorSetting lineColor = new ColorSetting(new Color(255, 255, 85, 255));

    private static final List<LivingEntity> trackedMobs = new ArrayList<>();
    private static final Set<Integer> lineMobIds = new HashSet<>();

    private static ClientLevel lastLevel;
    private static int lastRound = -1;

    public BadHeadshot() {
        registerSetting(onlyGame, distance, targetColor, lineColor);
    }

    @Override
    protected void onEnable() {
        clear();
    }

    @Override
    protected void onDisable() {
        clear();
    }

    @EventTarget
    public void onEntityLoad(EntityLoadEvent event) {
        if (!isAllowed()) {
            return;
        }

        if (event.getEntity() instanceof LivingEntity living && isTrackedMob(living)) {
            if (!trackedMobs.contains(living)) {
                trackedMobs.add(living);
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.level == null || mc.player == null || !isAllowed()) {
            clear();
            return;
        }

        if (lastLevel != mc.level || lastRound != ServerTracker.currentRound) {
            clear();
            lastLevel = mc.level;
            lastRound = ServerTracker.currentRound;
        }

        cleanupDeadMobs();
        updateLineMobs();
    }

    public static boolean isBadHeadshotEntity(Entity entity) {
        return entity instanceof LivingEntity living
                && isAllowed()
                && currentTarget() == living;
    }

    public static int boxColor(Entity entity) {
        return isBadHeadshotEntity(entity) ? targetColor.getValue().getRGB() : 0;
    }

    public static boolean isLineEntity(Entity entity) {
        return entity instanceof LivingEntity living
                && isAllowed()
                && lineMobIds.contains(living.getId());
    }

    private static void updateLineMobs() {
        lineMobIds.clear();

        LivingEntity target = currentTarget();
        if (target == null || mc.player == null || mc.level == null) {
            return;
        }

        List<LivingEntity> hits = raycastTrackedMobs(distance.getValue().doubleValue());
        if (!hits.contains(target)) {
            return;
        }

        for (LivingEntity hit : hits) {
            if (hit != target) {
                lineMobIds.add(hit.getId());
            }
        }
    }

    private static List<LivingEntity> raycastTrackedMobs(double maxDistance) {
        List<LivingEntity> hits = new ArrayList<>();
        LocalPlayer player = mc.player;
        if (player == null) {
            return hits;
        }

        Vec3 start = new Vec3(player.getX(), player.getEyeY(), player.getZ());
        Vec3 look = getLookVector(player.getXRot(), player.getYRot()).normalize();
        Vec3 end = start.add(look.scale(maxDistance));
        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(maxDistance)).inflate(1.0D);

        for (LivingEntity mob : trackedMobs) {
            if (!isAliveTrackedMob(mob) || !searchBox.intersects(mob.getBoundingBox())) {
                continue;
            }

            AABB box = mob.getBoundingBox().inflate(0.4D, 0.1D, 0.4D);
            if (box.clip(start, end).isPresent() && player.hasLineOfSight(mob)) {
                hits.add(mob);
            }
        }

        return hits;
    }

    private static Vec3 getLookVector(float xRot, float yRot) {
        double pitchRad = Math.toRadians(xRot);
        double yawRad = Math.toRadians(yRot);

        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);

        return new Vec3(x, y, z);
    }

    private static LivingEntity currentTarget() {
        cleanupDeadMobs();
        if (trackedMobs.isEmpty()) {
            return null;
        }
        return trackedMobs.get(trackedMobs.size() - 1);
    }

    private static void cleanupDeadMobs() {
        trackedMobs.removeIf(mob -> !isAliveTrackedMob(mob));
        lineMobIds.removeIf(id -> trackedMobs.stream().noneMatch(mob -> mob.getId() == id));
    }

    private static boolean isAliveTrackedMob(LivingEntity mob) {
        return mob != null
                && !mob.isRemoved()
                && mob.isAlive()
                && mc.level != null
                && mob.level() == mc.level;
    }

    private static boolean isTrackedMob(LivingEntity entity) {
        if (entity == mc.player || entity instanceof Player || entity instanceof ArmorStand) {
            return false;
        }

        if (!entity.isAlive() || entity.isInvisible()) {
            return false;
        }

        return entity instanceof Enemy || entity instanceof Wolf || entity instanceof IronGolem;
    }

    private static boolean isAllowed() {
        if (ZombiesModClient.moduleManager == null) {
            return false;
        }

        AbstractModule module = ZombiesModClient.moduleManager.getModule("Bad Headshot");
        if (module == null || !module.isEnable()) {
            return false;
        }

        return !onlyGame.getValue() || PlayerUtils.isInHypZombies();
    }

    private static void clear() {
        trackedMobs.clear();
        lineMobIds.clear();
    }
}
