package com.example.client.mixin;

import com.example.client.ZombiesModClient;
import com.example.client.module.AbstractModule;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

@Mixin(Avatar.class)
public class EyeHeightMixin {
    @ModifyReturnValue(method = "getDefaultDimensions", at = @At("RETURN"))
    private EntityDimensions zombiesmod$setSneakEyeHeight(EntityDimensions original, Pose pose) {
        AbstractModule eyeHeight = ZombiesModClient.moduleManager == null
                ? null
                : ZombiesModClient.moduleManager.getModule("module.eye_height");
        if (pose == Pose.CROUCHING && eyeHeight != null && eyeHeight.isEnable()) {
            return original.withEyeHeight(1.54f);
        }
        return original;
    }
}