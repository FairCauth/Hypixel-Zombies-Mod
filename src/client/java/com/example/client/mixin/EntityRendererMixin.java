package com.example.client.mixin;

import com.example.client.module.modules.HideBlockingPlayer;
import com.example.client.utils.HidePlayerHelper;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
    @Inject(
            method = "shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void zombiesmod$hideBlockingPlayer(
            Entity entity,
            Frustum frustum,
            double camX,
            double camY,
            double camZ,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if(!HideBlockingPlayer.fullHide.getValue()) return;
        if (entity instanceof Player player) {
            if (HidePlayerHelper.shouldFade(player)) {
                cir.setReturnValue(false);
            }
        }
    }
}
