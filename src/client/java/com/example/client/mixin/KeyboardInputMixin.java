package com.example.client.mixin;

import com.example.client.module.modules.InvMove;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {
    /** Synchronize physical keys immediately before vanilla builds the movement input. */
    @Inject(method = "tick", at = @At("HEAD"))
    private void zombiesmod$allowContainerMovement(CallbackInfo ci) {
        InvMove.updateMovementKeys();
    }
}
