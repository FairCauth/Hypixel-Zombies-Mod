package com.example.client.mixin.render;

import com.example.client.ZombiesModClient;
import com.example.client.module.AbstractModule;
import com.example.client.module.modules.BadHeadshot;
import com.example.client.module.modules.EasyAmmo;
import com.example.client.module.modules.EasyRevive;
import com.example.client.module.modules.ZombieChams;
import com.example.client.utils.BadHeadshotOutlineState;
import com.example.client.utils.ChamsState;
import com.example.client.utils.EasyReviveBoundingBoxState;
import com.example.client.utils.HideEntityState;
import com.example.client.utils.PlayerUtils;
import com.example.client.utils.render.ChamsRenderType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererChamsMixin {

    // 抽取阶段拿得到实体，判断是否 chams 目标，写进 render state
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void zombiesmod$flagChams(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
        if (state instanceof ChamsState cs) {
            cs.zombiesmod$setChams(zombiesmod$isChamsTarget(entity));
        }
        if (state instanceof BadHeadshotOutlineState badHeadshotState) {
            badHeadshotState.zombiesmod$setBadHeadshotBoxColor(BadHeadshot.boxColor(entity));
        }
        if (state instanceof EasyReviveBoundingBoxState boxState) {
            boolean showReviveBox = zombiesmod$shouldShowEasyReviveBox(entity);
            boolean show = showReviveBox || EasyAmmo.shouldShowAABB(entity);
            AABB box = showReviveBox && entity instanceof Player player
                    ? EasyRevive.getExpandedBoundingBox(player)
                    : entity.getBoundingBox();
            boxState.zombiesmod$setEasyReviveBoundingBox(
                    show,
                    (float) (box.minX - entity.getX()),
                    (float) (box.minY - entity.getY()),
                    (float) (box.minZ - entity.getZ()),
                    (float) (box.maxX - entity.getX()),
                    (float) (box.maxY - entity.getY()),
                    (float) (box.maxZ - entity.getZ())
            );
        }
    }

    // 进入该实体 submit 时开启 chams 标记：期间所有走 RenderTypes 的层（本体/盔甲/头颅…）都会被换成无深度版
    @Inject(method = "submit", at = @At("HEAD"))
    private void zombiesmod$chamsSubmitStart(LivingEntityRenderState state, PoseStack poseStack,
                                             SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        ChamsRenderType.active = state instanceof ChamsState cs
                && cs.zombiesmod$isChams()
                && (!(state instanceof HideEntityState hideState) || !hideState.zombiesmod$isFaded());

        int badHeadshotColor = state instanceof BadHeadshotOutlineState badHeadshotState
                ? badHeadshotState.zombiesmod$getBadHeadshotBoxColor()
                : 0;
        if (badHeadshotColor != 0) {
            float width = Math.max(0.1F, state.boundingBoxWidth);
            float height = Math.max(0.1F, state.boundingBoxHeight);
            double halfWidth = width / 2.0D;
            collector.submitCustomGeometry(
                    poseStack,
                    RenderTypes.lines(),
                    (pose, consumer) -> zombiesmod$drawBox(
                            pose.pose(),
                            consumer,
                            (float) -halfWidth,
                            0.0F,
                            (float) -halfWidth,
                            (float) halfWidth,
                            height,
                            (float) halfWidth,
                            badHeadshotColor
                    )
            );
        }

        if (state instanceof EasyReviveBoundingBoxState boxState
                && boxState.zombiesmod$showEasyReviveBoundingBox()) {
            collector.submitCustomGeometry(
                    poseStack,
                    RenderTypes.lines(),
                    (pose, consumer) -> zombiesmod$drawBox(
                            pose.pose(),
                            consumer,
                            boxState.zombiesmod$getEasyReviveMinX(),
                            boxState.zombiesmod$getEasyReviveMinY(),
                            boxState.zombiesmod$getEasyReviveMinZ(),
                            boxState.zombiesmod$getEasyReviveMaxX(),
                            boxState.zombiesmod$getEasyReviveMaxY(),
                            boxState.zombiesmod$getEasyReviveMaxZ(),
                            0xFF55FF55
                    )
            );
        }
    }

    @Inject(method = "submit", at = @At("RETURN"))
    private void zombiesmod$chamsSubmitEnd(LivingEntityRenderState state, PoseStack poseStack,
                                            SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        ChamsRenderType.active = false;
    }

    // 本体兜底：即使本体模型用了非 RenderTypes 的类型，也直接换成无深度
    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void zombiesmod$chamsType(LivingEntityRenderState state, boolean isBodyVisible, boolean forceTransparent,
                                       boolean appearGlowing, CallbackInfoReturnable<RenderType> cir) {
        if (state instanceof ChamsState cs
                && cs.zombiesmod$isChams()
                && (!(state instanceof HideEntityState hideState) || !hideState.zombiesmod$isFaded())) {
            Identifier texture = ((LivingEntityRenderer) (Object) this).getTextureLocation(state);
            cir.setReturnValue(ChamsRenderType.noDepth(texture));
        }
    }

    @Unique
    private static boolean zombiesmod$isChamsTarget(LivingEntity entity) {
        if (ZombiesModClient.moduleManager == null) return false;
        AbstractModule m = ZombiesModClient.moduleManager.getModule("Zombie Chams");
        if (m == null || !m.isEnable()) return false;
        if (ZombieChams.onlyGame.getValue() && !PlayerUtils.isInHypZombies()) return false;
        return entity instanceof Enemy || entity instanceof Wolf || entity instanceof IronGolem;
    }

    @Unique
    private static boolean zombiesmod$shouldShowEasyReviveBox(LivingEntity entity) {
        if (!(entity instanceof Player player) || !EasyRevive.isDown(player)) return false;
        if (ZombiesModClient.moduleManager == null || !EasyRevive.showBB.getValue()) return false;
        AbstractModule module = ZombiesModClient.moduleManager.getModule("Easy Revive");
        return module != null && module.isEnable();
    }

    @Unique
    private static void zombiesmod$drawBox(Matrix4f pose, VertexConsumer consumer,
                                           float minX, float minY, float minZ,
                                           float maxX, float maxY, float maxZ,
                                           int color) {
        zombiesmod$line(pose, consumer, minX, minY, minZ, maxX, minY, minZ, color);
        zombiesmod$line(pose, consumer, minX, minY, maxZ, maxX, minY, maxZ, color);
        zombiesmod$line(pose, consumer, minX, maxY, minZ, maxX, maxY, minZ, color);
        zombiesmod$line(pose, consumer, minX, maxY, maxZ, maxX, maxY, maxZ, color);

        zombiesmod$line(pose, consumer, minX, minY, minZ, minX, minY, maxZ, color);
        zombiesmod$line(pose, consumer, maxX, minY, minZ, maxX, minY, maxZ, color);
        zombiesmod$line(pose, consumer, minX, maxY, minZ, minX, maxY, maxZ, color);
        zombiesmod$line(pose, consumer, maxX, maxY, minZ, maxX, maxY, maxZ, color);

        zombiesmod$line(pose, consumer, minX, minY, minZ, minX, maxY, minZ, color);
        zombiesmod$line(pose, consumer, maxX, minY, minZ, maxX, maxY, minZ, color);
        zombiesmod$line(pose, consumer, minX, minY, maxZ, minX, maxY, maxZ, color);
        zombiesmod$line(pose, consumer, maxX, minY, maxZ, maxX, maxY, maxZ, color);
    }

    @Unique
    private static void zombiesmod$line(Matrix4f pose, VertexConsumer consumer,
                                        float x1, float y1, float z1,
                                        float x2, float y2, float z2,
                                        int color) {
        float nx = x2 - x1;
        float ny = y2 - y1;
        float nz = z2 - z1;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 0.0F) {
            nx /= len;
            ny /= len;
            nz /= len;
        }
        consumer.addVertex(pose, x1, y1, z1).setColor(color).setNormal(nx, ny, nz).setLineWidth(1.0F);
        consumer.addVertex(pose, x2, y2, z2).setColor(color).setNormal(nx, ny, nz).setLineWidth(1.0F);
    }
}
