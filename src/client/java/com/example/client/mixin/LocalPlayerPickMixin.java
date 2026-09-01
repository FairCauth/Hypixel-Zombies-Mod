package com.example.client.mixin;

import com.example.client.module.modules.HologramFix;
import com.example.client.module.modules.EasyRevive;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

@Mixin(LocalPlayer.class)
public class LocalPlayerPickMixin {

    @Inject(
            method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",
            at = @At("HEAD")
    )
    private static void zombiesmod$applyEasyReviveBoundingBoxes(Entity cameraEntity,
                                                                 double blockInteractionRange,
                                                                 double entityInteractionRange,
                                                                 float partialTick,
                                                                 CallbackInfoReturnable<HitResult> cir) {
        // 玩家姿势更新可能在准心选取前恢复原始碰撞箱，因此在 raycast 前重新应用。
        EasyRevive.applyExpandedBoundingBoxes();
    }

    @ModifyArg(
            method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;"
            ),
            index = 4
    )
    private static Predicate<Entity> zombiesmod$ignoreHolograms(Predicate<Entity> original) {
        if (!HologramFix.isActiveInCurrentGame()) return original;
        // 在原过滤器（EntitySelector.CAN_BE_PICKED）基础上再排除所有盔甲架（隐形全息字）。
        return original.and(e -> !(e instanceof ArmorStand));
    }
}
