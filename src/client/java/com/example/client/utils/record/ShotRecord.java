package com.example.client.utils.record;

import com.example.client.ZombiesGuns;
import net.minecraft.world.item.ItemStack;

public record ShotRecord(
        long id,
        ZombiesGuns gun,
        int ultimateLevel,
        int slot,
        ItemStack stack,
        long timeMs
) {

}