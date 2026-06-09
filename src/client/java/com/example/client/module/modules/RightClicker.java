package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.ZombiesGuns;
import com.example.client.events.TickEvent;
import com.example.client.language.Language;
import com.example.client.language.Text;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.settings.BooleanSetting;
import com.example.client.setting.settings.NumberSetting;
import com.example.client.utils.TimeUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

@ModuleInfo(name = {
        @Text(label = "Right Clicker", language = Language.English),
        @Text(label = "右键连点器", language = Language.Chinese)
}, enable = true)
public class RightClicker extends AbstractModule {

    @SettingInfo(name = {
            @Text(label = "Max CPS", language = Language.English),
            @Text(label = "最大CPS", language = Language.Chinese)
    })
    private final NumberSetting maxCPS = new NumberSetting(12, 1.0, 20.0, "#");
    @SettingInfo(name = {
            @Text(label = "Min CPS", language = Language.English),
            @Text(label = "最小CPS", language = Language.Chinese)
    })
    private final NumberSetting minCPS = new NumberSetting(11, 1.0, 20.0, "#");
    @SettingInfo(name = {
            @Text(label = "Only Guns", language = Language.English),
            @Text(label = "只对枪生效", language = Language.Chinese)
    })
    private final BooleanSetting onlyGuns = new BooleanSetting(true);
    public TimeUtils rightClickTimer = new TimeUtils();

    public RightClicker() {
        registerSetting(maxCPS, minCPS, onlyGuns);
    }

    @EventTarget
    public void onClick(TickEvent event) {

        if (!mc.options.keyUse.isDown()) return;
        if (shouldSkipInteraction()) {
            return;
        }
        ItemStack current = mc.player.getMainHandItem();

        if(onlyGuns.getValue() && !ZombiesGuns.isZombiesGun(current)) {
            return;
        }
        long delay = TimeUtils.randomClickDelay(minCPS.getValue().intValue(), maxCPS.getValue().intValue());
        if (rightClickTimer.hasTimeElapsed(delay, true)) {
            KeyMapping.click(mc.options.keyUse.getDefaultKey());
        }
    }
    private boolean shouldSkipInteraction() {
        if (mc.player == null || mc.level == null) {
            return true;
        }

        if (mc.screen != null) {
            return true;
        }

        if (mc.hitResult == null) {
            return false;
        }

        switch (mc.hitResult.getType()) {
            case BLOCK -> {
                BlockHitResult blockHit = (BlockHitResult) mc.hitResult;
                BlockPos pos = blockHit.getBlockPos();
                BlockState state = mc.level.getBlockState(pos);
                Block block = state.getBlock();

                return isInteractableBlock(block, state);
            }

            case ENTITY -> {
                EntityHitResult entityHit = (EntityHitResult) mc.hitResult;
                Entity entity = entityHit.getEntity();

                return isInteractableEntity(entity);
            }

            default -> {
                return false;
            }
        }
    }
    private boolean isInteractableBlock(Block block, BlockState state) {
        return block instanceof ButtonBlock
                || block instanceof LeverBlock
                || block instanceof DoorBlock
                || block instanceof TrapDoorBlock
                || block instanceof FenceGateBlock
                || block instanceof ChestBlock
                || block instanceof EnderChestBlock
                || block instanceof BarrelBlock
                || block instanceof ShulkerBoxBlock
                || block instanceof CraftingTableBlock
                || block instanceof FurnaceBlock
                || block instanceof AnvilBlock
                || block instanceof EnchantingTableBlock
                || block instanceof BrewingStandBlock
                || block instanceof HopperBlock
                || block instanceof DispenserBlock
                || block instanceof DropperBlock
                || block instanceof BedBlock
                || state.hasBlockEntity();
    }
    private boolean isInteractableEntity(Entity entity) {
        return entity instanceof AbstractVillager
                || entity instanceof ArmorStand
                || entity instanceof ItemFrame
                || entity instanceof Minecart
                || entity instanceof Boat
                || entity instanceof AbstractHorse;
    }
}
