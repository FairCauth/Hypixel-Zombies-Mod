package com.example.client.mixin;

import com.example.client.module.modules.SidebarModification;
import com.example.client.utils.render.SidebarLatencyRenderer;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class SidebarLatencyMixin {
    @ModifyArg(
            method = "displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V",
                    ordinal = 1
            ),
            index = 3
    )
    private int zombiesmod$includeLatencyBackground(int bottom) {
        return SidebarModification.isActive() ? bottom + Minecraft.getInstance().font.lineHeight : bottom;
    }

    // The custom Zombies sidebar renders the same footer before cancelling this method.
    @Inject(
            method = "displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)V",
            at = @At("TAIL")
    )
    private void zombiesmod$renderLatency(
            GuiGraphicsExtractor graphics, Objective objective, CallbackInfo ci,
            @Local(name = "left") int left,
            @Local(name = "bottom") int bottom,
            @Local(name = "width") int width
    ) {
        SidebarLatencyRenderer.render(graphics, Minecraft.getInstance().font, left, bottom, width);
    }
}
