package com.example.client.mixin;

import com.example.client.gui.ZombiesConfigScreen;
import com.example.client.language.GuiText;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void addZombiesButton(CallbackInfo ci) {
        this.addRenderableWidget(
                Button.builder(
                        GuiText.text("zombies_mod"),
                        button -> {
                            if(ZombiesConfigScreen.instance == null)
                                ZombiesConfigScreen.instance = new ZombiesConfigScreen((Screen) this);
                            ZombiesConfigScreen.instance.setParent((Screen) this);
                            Minecraft.getInstance().gui.setScreen(ZombiesConfigScreen.instance);
                        }
                ).bounds(
                        5, 5,
                        80, 20
                ).build()
        );
    }
}
