package com.example.client.mixin;


import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import org.spongepowered.asm.mixin.Mixin;


@Mixin(EquipmentLayerRenderer.class)
public class EquipmentLayerRendererMixin {
//
//    @ModifyVariable(
//            method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
//            at = @At("HEAD"),
//            argsOnly = true,
//            ordinal = 1
//    )
//    private int zombiesmod$fadeArmorColor2(
//            int color,
//            EquipmentClientInfo.LayerType layerType,
//            ResourceKey<EquipmentAsset> equipmentAssetId,
//            Model<?> model,
//            Object state,
//            ItemStack itemStack,
//            PoseStack poseStack,
//            SubmitNodeCollector submitNodeCollector,
//            int lightCoords,
//            Identifier playerTextureOverride,
//            int outlineColor,
//            int order
//    ) {
//        return applyArmorAlphaIfNeeded(color, state);
//    }
//
//    private static int applyArmorAlphaIfNeeded(int color, Object state) {
//        if (!(state instanceof AvatarRenderState avatarState)) {
//            return color;
//        }
//
//        if (!HidePlayerHelper.shouldFade(avatarState.id)) {
//            return color;
//        }
//
//        return HidePlayerHelper.alphaWhite(HideBlockingPlayer.fadePlayerAlpha.getValue().intValue());
//    }
//
//    private static int withAlpha(int color, int alpha) {
//        int a = Math.max(0, Math.min(255, alpha));
//        return (a << 24) | (color & 0x00FFFFFF);
//    }
}
