package com.example.client.mixin;

import com.example.client.utils.HideEntityState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EquipmentLayerRenderer.class)
public class EquipmentLayerRendererMixin {
	private boolean zombiesmod$fadeArmor;

	@Inject(
		method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
		at = @At("HEAD")
	)
	private void zombiesmod$captureArmorFade(
		    EquipmentClientInfo.LayerType layerType,
		    ResourceKey<?> equipmentAssetId,
		    Model<?> model,
		Object state,
		    ItemStack itemStack,
		    PoseStack poseStack,
		    SubmitNodeCollector nodeCollector,
		int lightCoords,
		Identifier playerTextureOverride,
		int outlineColor,
		int order,
		CallbackInfo ci
	) {
	    zombiesmod$fadeArmor = state instanceof HideEntityState hideState && hideState.zombiesmod$isFaded();
	}

	@Inject(
		method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
		at = @At("RETURN")
	)
	    private void zombiesmod$clearArmorFade(
		    EquipmentClientInfo.LayerType layerType,
		    ResourceKey<?> equipmentAssetId,
		    Model<?> model,
		    Object state,
		    ItemStack itemStack,
		    PoseStack poseStack,
		    SubmitNodeCollector nodeCollector,
		    int lightCoords,
		    Identifier playerTextureOverride,
		    int outlineColor,
		    int order,
		    CallbackInfo ci
	    ) {
	    zombiesmod$fadeArmor = false;
	}

	@Redirect(
		method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCull(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"
		)
	)
	private RenderType zombiesmod$useTranslucentArmor(Identifier texture) {
	    return zombiesmod$fadeArmor
		    ? RenderTypes.armorTranslucent(texture)
		    : RenderTypes.armorCutoutNoCull(texture);
	}
}
