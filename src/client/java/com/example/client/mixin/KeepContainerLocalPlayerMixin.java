package com.example.client.mixin;

import com.example.client.module.modules.KeepContainer;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class KeepContainerLocalPlayerMixin {

    /**
     * 现代版本关闭界面时除了发关闭包，还会立刻把 containerMenu 重置成玩家背包。
     * LB 1.8 只拦包即可；这里同时保留本地菜单，才能用原 containerId 正常重开和点击。
     */
    @Inject(method = "closeContainer", at = @At("HEAD"), cancellable = true)
    private void zombiesmod$keepContainerSession(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (KeepContainer.shouldKeepMenu(player.containerMenu)) {
            ci.cancel();
        }
    }
}
