package com.example.client.module.modules;

import com.example.client.ZombiesGuns;
import com.example.client.ZombiesModClient;
import com.example.client.language.Language;
import com.example.client.language.Text;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.utils.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

@ModuleInfo(name = {
        @Text(label = "No Gun Fire", language = Language.English),
        @Text(label = "屏蔽开枪火焰", language = Language.Chinese)
}, enable = true)
public class NoGunFire extends AbstractModule {
    @Override
    protected void onEnable() {
        super.onEnable();
    }


    public static boolean shouldCancel(ClientboundLevelParticlesPacket packet) {
        if (mc.player == null || mc.level == null) {
            return false;
        }

        NoGunFire module = ((NoGunFire) ZombiesModClient.moduleManager.getModule("No Gun Fire"));

        if (module == null || !module.isEnable()) {
            return false;
        }

        if (!PlayerUtils.isInHypZombies()) {
            return false;
        }

        if (!ZombiesGuns.isZombiesGun(mc.player.getMainHandItem())) {
            return false;
        }

        ParticleOptions options = packet.getParticle();

        if (!isGunFireParticle(options)) {
            return false;
        }

        /*
         * 只屏蔽玩家附近的火焰粒子，避免把地图里的火焰也屏蔽掉。
         */
        double distanceSq = mc.player.distanceToSqr(
                packet.getX(),
                packet.getY(),
                packet.getZ()
        );

        return distanceSq <= 26.0;
    }

    private static boolean isGunFireParticle(ParticleOptions options) {
        if (options == null) {
            return false;
        }

        ParticleType<?> type = options.getType();

        return type == ParticleTypes.FLAME
                || type == ParticleTypes.SMALL_FLAME
                || type == ParticleTypes.LAVA
                || type == ParticleTypes.SMOKE
                || type == ParticleTypes.LARGE_SMOKE
                || type == ParticleTypes.CAMPFIRE_COSY_SMOKE
                || type == ParticleTypes.CAMPFIRE_SIGNAL_SMOKE
                || type == ParticleTypes.FIREWORK
                || type == ParticleTypes.POOF;
    }
}
