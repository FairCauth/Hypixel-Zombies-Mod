package com.example.client;

import lombok.Getter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
@Getter
public enum ZombiesGuns {
    Pistol(Items.WOODEN_HOE, 10, 15, 6, 6),
    Rifle(Items.STONE_HOE, 7, 10, 6, 8),
    Rainbow_Rifle(Items.GOLDEN_SHOVEL, 5, 7, 5, 6, 6.5, 7),
    Shotgun(Items.IRON_HOE, 8, 12, 4.5, 4.5),
    Rocket_Launcher(Items.STONE_SHOVEL, 10, 15, 10, 10),
    Sniper(Items.WOODEN_SHOVEL, 30, 45, 30, 40),
    Flamethrower(Items.GOLDEN_HOE, 4, 6, 2, 2),
    Blow_Dart(Items.IRON_SHOVEL, 20, 30, 10, 10),
    Zombie_Soaker(Items.DIAMOND_HOE, 5, 10, 5, 8),
    Zombie_Zapper(Items.DIAMOND_PICKAXE, 15, 20, 12, 18),
    Double_Barrel_Shotgun(Items.FLINT_AND_STEEL, 8, 12, 7, 7, 8, 8),
    Elder_Gun(Items.SHEARS, 20, 30, 15, 20),
    Gold_Digger(Items.GOLDEN_PICKAXE, 10, 15, 6, 8, 10, 12, 15, 20);

    private final Item item;
    private final int gold;
    private final int criticalGold;
    private final double damage;
    private final double[] ultimatedDamage;

    ZombiesGuns(Item item, int gold, int criticalGold, double damage, double... ultimatedDamage) {
        this.item = item;
        this.gold = gold;
        this.criticalGold = criticalGold;
        this.damage = damage;
        this.ultimatedDamage = ultimatedDamage;
    }
    public String getDisplayName() {
        return switch (this) {
            case Pistol -> "Pistol";
            case Rifle -> "Rifle";
            case Rainbow_Rifle -> "Rainbow Rifle";
            case Shotgun -> "Shotgun";
            case Rocket_Launcher -> "Rocket Launcher";
            case Sniper -> "Sniper";
            case Flamethrower -> "Flamethrower";
            case Blow_Dart -> "Blow Dart";
            case Zombie_Soaker -> "Zombie Soaker";
            case Zombie_Zapper -> "Zombie Zapper";
            case Double_Barrel_Shotgun -> "Double Barrel Shotgun";
            case Elder_Gun -> "Elder Gun";
            case Gold_Digger -> "Gold Digger";
        };
    }
    public static ZombiesGuns getGunOrNull(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return ITEM_TO_GUN.get(stack.getItem());
    }
    public static final Map<Item, ZombiesGuns> ITEM_TO_GUN =
            Arrays.stream(values()).collect(Collectors.toMap(
                    ZombiesGuns::getItem,
                    gun -> gun
            ));
    public double getDamageByUltimateLevel(int ultimateLevel) {
        if (ultimateLevel <= 0)
            return damage;

        if (ultimatedDamage == null || ultimatedDamage.length == 0)
            return damage;

        int index = Math.min(ultimateLevel - 1, ultimatedDamage.length - 1);

        return ultimatedDamage[index];
    }

    /**
     * 判断是否能多次强化
     */
    public boolean hasMultiUltimateDamage() {
        return ultimatedDamage != null && ultimatedDamage.length > 1;
    }
    public static boolean isZombiesGun(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return false;

        return ZombiesGuns.ITEM_TO_GUN.containsKey(stack.getItem());
    }

}
