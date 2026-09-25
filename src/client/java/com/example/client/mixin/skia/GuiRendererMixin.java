package com.example.client.mixin.skia;

import com.example.client.skia.Skia;
import com.example.client.skia.fbo.GameFramebuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public class GuiRendererMixin {
    @Inject(
            method = "executeDrawRange",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderPass;close()V",
                    ordinal = 0,
                    shift = At.Shift.BEFORE
            )
    )
    private void modid$captureMainGuiFramebuffer(CallbackInfo ci) {
        if (!GameFramebuffer.isRenderingCustomGui()) {
            Skia.captureMainGuiFramebuffer();
        }
    }

    /** Draw the mod HUD before vanilla starts its GUI passes. */
    @Inject(
            method = "render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
            at = @At("HEAD")
    )
    private void modid$renderSkiaBelowGui(GpuBufferSlice fogBuffer, CallbackInfo ci) {
        if (!GameFramebuffer.isRenderingCustomGui()) {
            Skia.tick();
        }
    }
}
