package com.example.client.mixin;

import com.example.client.utils.HideEntityState;
import com.example.client.utils.render.AlphaSubmitNodeCollector;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.level.block.SkullBlock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CustomHeadLayer.class)
public class CustomHeadLayerMixin {
    @Shadow @Final
    private PlayerSkinRenderCache playerSkinRenderCache;

    @Inject(method = "resolveSkullRenderType", at = @At("RETURN"), cancellable = true)
    private void zombiesmod$translucentPlayerHead(LivingEntityRenderState state, SkullBlock.Type type,
                                                    CallbackInfoReturnable<RenderType> cir) {
        if (state instanceof HideEntityState hide && hide.zombiesmod$isFaded() && type == SkullBlock.Types.PLAYER) {
            cir.setReturnValue(state.wornHeadProfile == null
                    ? PlayerSkinRenderCache.DEFAULT_PLAYER_SKIN_RENDER_TYPE
                    : playerSkinRenderCache.getOrDefault(state.wornHeadProfile).renderType());
        }
    }

    @ModifyVariable(
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;FF)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private SubmitNodeCollector zombiesmod$fadeHead(
            SubmitNodeCollector nodeCollector,
            PoseStack poseStack,
            SubmitNodeCollector originalNodeCollector,
            int packedLight,
            LivingEntityRenderState renderState,
            float yRot,
            float xRot
    ) {
        if (renderState instanceof HideEntityState hide && hide.zombiesmod$isFaded()) {
            return AlphaSubmitNodeCollector.wrap(nodeCollector, hide.zombiesmod$getFadeAlpha());
        }
        return nodeCollector;
    }
}
