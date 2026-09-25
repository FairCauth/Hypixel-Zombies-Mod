package com.example.client.mixin;
import com.example.client.utils.HideEntityState;
import com.example.client.utils.render.AlphaSubmitNodeCollector;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(HumanoidArmorLayer.class)
public class HumanoidArmorLayerMixin {
    @ModifyVariable(
            method = "submit",
            at = @At("HEAD"),
            argsOnly = true
    )
    private SubmitNodeCollector zombiesmod$fadeArmor(
            SubmitNodeCollector nodeCollector,
            PoseStack poseStack,
            SubmitNodeCollector originalNodeCollector,
            int packedLight,
            HumanoidRenderState renderState,
            float yRot,
            float xRot
    ) {
        if (renderState instanceof HideEntityState hideState && hideState.zombiesmod$isFaded()) {
            float fadeProgress = 1.0F - hideState.zombiesmod$getFadeAlpha() / 255.0F;
            float smoothProgress = fadeProgress * fadeProgress * (3.0F - 2.0F * fadeProgress);
            float armorAlphaFactor = 1.0F - 0.4F * smoothProgress;
            int armorAlpha = Math.round(hideState.zombiesmod$getFadeAlpha() * armorAlphaFactor);
            return AlphaSubmitNodeCollector.wrap(nodeCollector, armorAlpha);
        }
        return nodeCollector;
    }
}
