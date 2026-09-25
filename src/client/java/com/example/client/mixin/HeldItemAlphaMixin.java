package com.example.client.mixin;

import com.example.client.utils.HideEntityState;
import com.example.client.utils.render.AlphaSubmitNodeCollector;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemInHandLayer.class)
public class HeldItemAlphaMixin {
    @ModifyVariable(
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;FF)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private SubmitNodeCollector zombiesmod$fadeHeldItem(
            SubmitNodeCollector nodeCollector,
            PoseStack poseStack,
            SubmitNodeCollector originalNodeCollector,
            int packedLight,
            ArmedEntityRenderState renderState,
            float yRot,
            float xRot
    ) {
        HideEntityState hide = renderState instanceof HideEntityState state ? state : null;
        if (hide != null && hide.zombiesmod$isFaded()) {
            int alpha = hide.zombiesmod$getFadeAlpha();
            return AlphaSubmitNodeCollector.wrap(nodeCollector, alpha);
        }
        return nodeCollector;
    }
}
