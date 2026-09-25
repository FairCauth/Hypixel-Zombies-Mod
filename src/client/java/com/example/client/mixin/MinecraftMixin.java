package com.example.client.mixin;

import com.example.client.ZombiesModClient;
import com.example.client.module.AbstractModule;
import com.example.client.module.modules.EasyAmmo;
import com.example.client.module.modules.HologramFix;
import com.example.client.module.modules.KeepContainer;
import com.example.client.module.modules.TeammatesGlow;
import com.example.client.utils.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow public MultiPlayerGameMode gameMode;
    @Shadow public ClientLevel level;
    @Shadow public LocalPlayer player;
    @Shadow public HitResult hitResult;
    @Shadow public GameRenderer gameRenderer;
    @Shadow private int rightClickDelay;

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen zombiesmod$handleKeptContainerScreen(Screen screen) {
        return KeepContainer.transformScreen(screen);
    }

    @Inject(
            method = "shouldEntityAppearGlowing(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void zombiesmod$shouldEntityAppearGlowing(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (ZombiesModClient.moduleManager == null) return;

        // 队友发光（玩家）
        AbstractModule glow = ZombiesModClient.moduleManager.getModule("Teammates Glow");
        if (glow != null && glow.isEnable()
                && entity instanceof Player
                && !(TeammatesGlow.onlyGame.getValue() && !PlayerUtils.isInHypZombies())) {
            cir.setReturnValue(true);
            return;
        }

    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void zombiesmod$startUseItem(CallbackInfo ci) {
        if (gameMode == null || level == null || player == null || gameRenderer == null) {
            return;
        }

        EntityHitResult ammoTarget = EasyAmmo.findArmorStandBehindPlayer(hitResult);
        if (ammoTarget != null) {
            ci.cancel();
            zombiesmod$interactWithAmmoStand(ammoTarget);
            return;
        }

        if (!HologramFix.isActiveInCurrentGame()) {
            return;
        }

        ci.cancel();

        if (gameMode.isDestroying()) {
            return;
        }

        rightClickDelay = 4;

        if (player.isHandsBusy()) {
            return;
        }

        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (!stack.isItemEnabled(level.enabledFeatures())) {
                return;
            }

            if (hitResult != null) {
                if (hitResult.getType() == HitResult.Type.ENTITY) {
                    EntityHitResult entityHit = (EntityHitResult) hitResult;
                    Entity entity = entityHit.getEntity();
                    if (!level.getWorldBorder().isWithinBounds(entity.blockPosition())) {
                        return;
                    }

                    InteractionResult result = gameMode.interact(player, entity, entityHit, hand);
                    if (result instanceof InteractionResult.Success success) {
                        zombiesmod$swingIfNeeded(hand, success);
                        return;
                    }
                } else if (hitResult.getType() == HitResult.Type.BLOCK && !HologramFix.ignoreBlockReactions.getValue()) {
                    BlockHitResult blockHit = (BlockHitResult) hitResult;
                    if (!level.getBlockState(blockHit.getBlockPos()).isAir()) {
                        int count = stack.getCount();
                        InteractionResult result = gameMode.useItemOn(player, hand, blockHit);
                        if (result instanceof InteractionResult.Success success) {
                            zombiesmod$swingIfNeeded(hand, success);
                            if (!stack.isEmpty() && (stack.getCount() != count || player.hasInfiniteMaterials())) {
                                gameRenderer.itemInHandRenderer.itemUsed(hand);
                            }
                            return;
                        }
                        if (result instanceof InteractionResult.Fail) {
                            return;
                        }
                    }
                }
            }

            if (!stack.isEmpty()) {
                InteractionResult result = gameMode.useItem(player, hand);
                if (result instanceof InteractionResult.Success success) {
                    zombiesmod$swingIfNeeded(hand, success);
                    gameRenderer.itemInHandRenderer.itemUsed(hand);
                    return;
                }
            }
        }
    }

    private void zombiesmod$interactWithAmmoStand(EntityHitResult armorStandHit) {
        if (gameMode.isDestroying() || player.isHandsBusy()) return;

        rightClickDelay = 4;
        Entity armorStand = armorStandHit.getEntity();
        if (!level.getWorldBorder().isWithinBounds(armorStand.blockPosition())) return;

        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (!stack.isItemEnabled(level.enabledFeatures())) return;

            InteractionResult result = gameMode.interact(player, armorStand, armorStandHit, hand);
            if (result instanceof InteractionResult.Success success) {
                if (success.swingSource() == InteractionResult.SwingSource.CLIENT) {
                    player.swing(hand);
                }
                return;
            }
        }
    }

    private void zombiesmod$swingIfNeeded(InteractionHand hand, InteractionResult.Success success) {
        if (!HologramFix.disableRightClickSwinging.getValue()
                && success.swingSource() == InteractionResult.SwingSource.CLIENT) {
            player.swing(hand);
        }
    }
}
