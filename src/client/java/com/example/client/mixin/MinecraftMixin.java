package com.example.client.mixin;

import com.example.client.ZombiesModClient;
import com.example.client.module.AbstractModule;
import com.example.client.module.modules.TeammatesGlow;
import com.example.client.utils.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(
            method = "shouldEntityAppearGlowing(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void zombiesmod$shouldEntityAppearGlowing(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        AbstractModule module = ZombiesModClient.moduleManager.getModule("Teammates Glow");
        if (module == null || !module.isEnable()) {
            return;
        }
        if (TeammatesGlow.onlyGame.getValue() && !PlayerUtils.isInHypZombies())
            return;
        if (entity instanceof Player) {
            cir.setReturnValue(true);
        }
    }
}
