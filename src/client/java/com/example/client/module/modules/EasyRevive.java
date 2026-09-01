package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.events.MouseInputEvent;
import com.example.client.events.TickEvent;
import com.example.client.ZombiesModClient;
import com.example.client.language.Language;
import com.example.client.language.Text;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.attribute.SettingAttribute;
import com.example.client.setting.settings.BooleanSetting;
import com.example.client.setting.settings.ModeSetting;
import com.example.client.setting.settings.NumberSetting;
import com.example.client.utils.PlayerUtils;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;

@ModuleInfo(name = {
        @Text(label = "Easy Revive", language = Language.English),
        @Text(label = "更容易救人", language = Language.Chinese)
}, enable = false)
public class EasyRevive extends AbstractModule {

    @SettingInfo(name = {
            @Text(label = "Range", language = Language.English),
            @Text(label = "距离", language = Language.Chinese)
    })
    public static final NumberSetting range = new NumberSetting(4.5d, 1, 5, "#.0");
    @SettingInfo(name = {
            @Text(label = "Resize Aura", language = Language.English),
            @Text(label = "救人光环", language = Language.Chinese)
    })
    public static final BooleanSetting aura = new BooleanSetting(false,
            new SettingAttribute<>(range, true)
    );

    @SettingInfo(name = {
            @Text(label = "Mode", language = Language.English),
            @Text(label = "模式", language = Language.Chinese)
    })
    public static final ModeSetting mode = new ModeSetting("Packet", Arrays.asList("AABB", "Packet"),
            new SettingAttribute<>(aura, "Packet")

    );

    @SettingInfo(name = {
            @Text(label = "Show BoundingBox", language = Language.English),
            @Text(label = "显示碰撞箱", language = Language.Chinese)
    })
    public static final BooleanSetting showBB = new BooleanSetting(true);

    public EasyRevive() {
        registerSetting(mode, showBB);
    }

    @Override
    protected void onEnable() {
        super.onEnable();



    }

    @Override
    protected void onDisable() {
        super.onDisable();


    }

    public static boolean isDown(Player player) {
        return player.isSleeping() || player.getPose() == net.minecraft.world.entity.Pose.SLEEPING;
    }

    public static AABB getExpandedBoundingBox(Player player) {
        double halfSize = 0.9D;

        return new AABB(
                player.getX() - halfSize,
                player.getY(),
                player.getZ() - halfSize,
                player.getX() + halfSize,
                player.getY() + 0.6D,
                player.getZ() + halfSize
        );
    }

    public static void applyExpandedBoundingBoxes() {
        if (mc.player == null
                || mc.level == null
                || ZombiesModClient.moduleManager == null
                || !mode.is("AABB")
                || !PlayerUtils.isInHypZombies()) {
            return;
        }

        AbstractModule module = ZombiesModClient.moduleManager.getModule("Easy Revive");
        if (module == null || !module.isEnable()) {
            return;
        }

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Player player
                    && player != mc.player
                    && isDown(player)) {
                player.setBoundingBox(getExpandedBoundingBox(player));
            }
        }
    }

    @EventTarget
    public void onMouse(MouseInputEvent event) {
        if (!mode.is("Packet")
                || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT
                || event.getAction() != GLFW.GLFW_PRESS
                || mc.player == null
                || mc.level == null
                || mc.screen != null
                || mc.getConnection() == null
                || !PlayerUtils.isInHypZombies()) {
            return;
        }


        Player player = raycastDownedPlayer(range.getValue().doubleValue());
        if (player == null) return;

        Vec3 eye = mc.player.getEyePosition();
        Vec3 relativeHit = player.getBoundingBox().clip(eye, player.position())
                .map(hit -> hit.subtract(player.getX(), player.getY(), player.getZ()))
                .orElse(Vec3.ZERO);

        mc.getConnection().send(new ServerboundInteractPacket(
                player.getId(),
                InteractionHand.MAIN_HAND,
                relativeHit,
                false
        ));
    }

    private Player raycastDownedPlayer(double distance) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 end = eye.add(mc.player.getViewVector(1.0F).scale(distance));

        double closestDistanceSq = distance * distance;
        if (mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS) {
            closestDistanceSq = Math.min(
                    closestDistanceSq,
                    eye.distanceToSqr(mc.hitResult.getLocation()) + 1.0E-6D
            );
        }

        Player closest = null;
        for (Player candidate : mc.level.players()) {
            if (candidate == mc.player || !isDown(candidate)) continue;

            var hit = getExpandedBoundingBox(candidate).clip(eye, end);
            if (hit.isEmpty()) continue;

            double distanceSq = eye.distanceToSqr(hit.get());
            if (distanceSq < closestDistanceSq) {
                closestDistanceSq = distanceSq;
                closest = candidate;
            }
        }
        return closest;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        applyExpandedBoundingBoxes();
    }

}
