package com.example.client.mixin;

import com.darkmagician6.eventapi.EventManager;
import com.example.client.events.KeyInputEvent;
import com.example.client.utils.IMinecraft;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin implements IMinecraft {

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void zombiesmod$keyPress(long handle, @KeyEvent.Action int action, KeyEvent event, CallbackInfo ci) {
        if (handle != mc.getWindow().handle()) {
            return;
        }

        int key = event.key();
        int scanCode = event.scancode();
        int modifiers = event.modifiers();

        EventManager.call(new KeyInputEvent(
                key,
                scanCode,
                action,
                modifiers
        ));
    }
}
