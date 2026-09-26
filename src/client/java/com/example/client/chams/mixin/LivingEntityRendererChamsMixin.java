package com.example.client.chams.mixin;

import com.example.client.chams.ChamsRenderType;
import com.example.client.chams.ChamsState;
import com.example.client.chams.FixedOrderSubmitNodeCollector;
import com.example.client.chams.ZombieChams;
import com.example.client.ZombiesModClient;
import com.example.client.module.AbstractModule;
import com.example.client.utils.PlayerUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererChamsMixin {

    @Unique
    private boolean zombiesmod$submittingDepthSeed;

    // 抽取阶段拿得到实体，判断是否 chams 目标，写进 render state
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void zombiesmod$flagChams(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
        if (state instanceof ChamsState cs) {
            boolean chamsTarget = zombiesmod$isChamsTarget(entity);
            cs.zombiesmod$setChams(chamsTarget);
            if (chamsTarget && entity.isInvisible()) {
                state.isInvisible = false;
                state.isInvisibleToPlayer = false;
            }
        }
    }

    // 先提交 LESS_THAN 深度种子，再让外层 submit 用原版深度通道重建正确的可见表面。
    @Inject(method = "submit", at = @At("HEAD"))
    private void zombiesmod$chamsSubmitStart(LivingEntityRenderState state, PoseStack poseStack,
                                             SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        boolean chamsTarget = state instanceof ChamsState cs && cs.zombiesmod$isChams();

        if (chamsTarget && !zombiesmod$submittingDepthSeed) {
            zombiesmod$submittingDepthSeed = true;
            ChamsRenderType.active = true;
            try {
                SubmitNodeCollector depthSeedCollector =
                        new FixedOrderSubmitNodeCollector(collector.order(-1));
                ((LivingEntityRenderer) (Object) this).submit(state, poseStack, depthSeedCollector, camera);
            } finally {
                ChamsRenderType.active = false;
                zombiesmod$submittingDepthSeed = false;
            }
        } else {
            ChamsRenderType.active = chamsTarget && zombiesmod$submittingDepthSeed;
        }

    }

    @Inject(method = "submit", at = @At("RETURN"))
    private void zombiesmod$chamsSubmitEnd(LivingEntityRenderState state, PoseStack poseStack,
                                           SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        ChamsRenderType.active = false;
    }

    // 本体兜底：仅在深度种子提交中替换，外层提交必须继续使用原版 RenderType。
    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void zombiesmod$chamsType(LivingEntityRenderState state, boolean isBodyVisible, boolean forceTransparent,
                                       boolean appearGlowing, CallbackInfoReturnable<RenderType> cir) {
        if (ChamsRenderType.active
                && state instanceof ChamsState cs
                && cs.zombiesmod$isChams()) {
            Identifier texture = ((LivingEntityRenderer) (Object) this).getTextureLocation(state);
            cir.setReturnValue(ChamsRenderType.noDepth(texture));
        }
    }

    @Unique
    private static boolean zombiesmod$isChamsTarget(LivingEntity entity) {
        if (ZombiesModClient.moduleManager == null) return false;
        AbstractModule m = ZombiesModClient.moduleManager.getModule("module.zombie_chams");
        if (m == null || !m.isEnable()) return false;
        if (ZombieChams.onlyGame.getValue() && !PlayerUtils.isInHypZombies()) return false;
        if (entity.isInvisible() && !ZombieChams.invisibleEntities.getValue()) return false;
        return entity instanceof Enemy || entity instanceof Wolf || entity instanceof IronGolem;
    }

}
