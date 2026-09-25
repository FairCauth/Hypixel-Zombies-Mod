package com.example.client.mixin;

import com.example.client.utils.render.AlphaSubmitNodeCollector;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFeatureRenderer.class)
public class ItemFeatureRendererAlphaMixin {
    private int zombiesmod$itemAlphaMarker;

    @Inject(method = "prepareMainSubmit", at = @At("HEAD"))
    private void zombiesmod$captureItemAlpha(ItemFeatureRenderer.Submit submit, CallbackInfo ci) {
        int[] tintLayers = submit.tintLayers();
        int marker = tintLayers.length == 0 ? 0 : tintLayers[tintLayers.length - 1];
        zombiesmod$itemAlphaMarker = AlphaSubmitNodeCollector.isItemAlphaMarker(marker) ? marker : 0;
    }

    @Inject(method = "prepareMainSubmit", at = @At("RETURN"))
    private void zombiesmod$clearItemAlpha(ItemFeatureRenderer.Submit submit, CallbackInfo ci) {
        zombiesmod$itemAlphaMarker = 0;
    }

    @ModifyArg(
            method = "prepareMainSubmit",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/QuadInstance;setColor(I)V"),
            index = 0
    )
    private int zombiesmod$applyItemAlpha(int color) {
        if (zombiesmod$itemAlphaMarker != 0 && (color >>> 24) == 255) {
            return AlphaSubmitNodeCollector.applyItemAlphaMarker(color, zombiesmod$itemAlphaMarker);
        }
        return color;
    }
}