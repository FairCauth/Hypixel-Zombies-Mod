package com.example.client.mixin.render;

import com.example.client.utils.HideEntityState;
import com.example.client.utils.HidePlayerHelper;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void zombiesmod$useFadeRenderType(LivingEntityRenderState state, boolean bodyVisible,
                                                boolean forceTransparent, boolean glowing,
                                                CallbackInfoReturnable<RenderType> cir) {
        if (state instanceof HideEntityState hide && hide.zombiesmod$isFaded()) {
            Identifier texture = ((LivingEntityRenderer) (Object) this).getTextureLocation(state);
            cir.setReturnValue(RenderTypes.entityTranslucent(texture));
        }
    }

    @Inject(method = "getModelTint", at = @At("HEAD"), cancellable = true)
    private void zombiesmod$fadeEntityModel(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
        if (state instanceof HideEntityState hide && hide.zombiesmod$isFaded()) {
            cir.setReturnValue((hide.zombiesmod$getFadeAlpha() << 24) | 0x00FFFFFF);
        }
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL")
    )
    private void zombiesmod$makeOverlappingPlayerTranslucent(
            LivingEntity entity,
            LivingEntityRenderState state,
            float partialTicks,
            CallbackInfo ci
    ) {

        boolean faded = HidePlayerHelper.shouldFade(entity);
        if (state instanceof HideEntityState hideState) {
            hideState.zombiesmod$setFaded(faded);
            hideState.zombiesmod$setFadeAlpha(faded ? HidePlayerHelper.fadeAlpha(entity) : 255);
        }
        if (!faded) {
            return;
        }
        //
        state.isInvisible = false;
        state.isInvisibleToPlayer = false;
    }
//
//    @Inject(
//            method = "getModelTint(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)I",
//            at = @At("HEAD"),
//            cancellable = true
//    )
//    private void zombiesmod$changeOverlappingPlayerAlpha(
//            LivingEntityRenderState state,
//            CallbackInfoReturnable<Integer> cir
//    ) {
//        if (!(state instanceof AvatarRenderState avatarState)) {
//            return;
//        }
//
//        if (!HidePlayerHelper.shouldFade(avatarState.id)) {
//            return;
//        }
//
//        cir.setReturnValue(HidePlayerHelper.alphaWhite(HideBlockingPlayer.fadePlayerAlpha.getValue().intValue()));
//    }
}
