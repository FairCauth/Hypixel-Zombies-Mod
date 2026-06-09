package com.example.client;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;

public class ZombiesGuns {
    public static final Map<Item, String> ZOMBIES_GUN_ITEM_NAMES = Map.ofEntries(
            Map.entry(Items.WOODEN_HOE, "Pistol"),
            Map.entry(Items.STONE_HOE, "Rifle"),
            Map.entry(Items.IRON_HOE, "Shotgun"),
            Map.entry(Items.STONE_SHOVEL, "Rocket Launcher"),
            Map.entry(Items.WOODEN_SHOVEL, "Sniper"),
            Map.entry(Items.GOLDEN_HOE, "Flamethrower"),
            Map.entry(Items.IRON_SHOVEL, "Blow Dart"),
            Map.entry(Items.DIAMOND_HOE, "Zombie Soaker"),
            Map.entry(Items.DIAMOND_PICKAXE, "Zombie Zapper"),
            Map.entry(Items.DIAMOND_AXE, "The Puncher"),
            Map.entry(Items.SHEARS, "Elder Gun"),
            Map.entry(Items.GOLDEN_PICKAXE, "Gold Digger")
    );
    public static boolean isZombiesGun(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return false;

        return ZombiesGuns.ZOMBIES_GUN_ITEM_NAMES.containsKey(stack.getItem());
    }

}
