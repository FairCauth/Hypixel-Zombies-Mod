package com.example.client.mixin;
import com.example.client.utils.HidePlayerHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public class HumanoidArmorLayerMixin {
    @Inject(
            method = "submit",
            at = @At("HEAD"),
            cancellable = true
    )
    private void zombiesmod$hideArmorWhenPlayerFaded(
            PoseStack poseStack,
            SubmitNodeCollector nodeCollector,
            int packedLight,
            HumanoidRenderState renderState,
            float yRot,
            float xRot,
            CallbackInfo ci
    ) {
        if (!(renderState instanceof AvatarRenderState avatarState)) {
            return;
        }

        if (HidePlayerHelper.shouldFade(avatarState.id)) {
            ci.cancel();
        }
    }
}
