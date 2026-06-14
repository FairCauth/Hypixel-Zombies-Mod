package com.example.client.utils;

import com.example.client.ZombiesGuns;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ZombiesUtils implements IMinecraft {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");
    public static ZombiesMap getMap() {
        if(mc.level == null ||  mc.player == null) return ZombiesMap.NULL;
        BlockPos blockPos = new BlockPos(0, 72, 12);
        BlockState state =  mc.level.getBlockState(blockPos);
        Block block = state.getBlock();

        if (state.isAir())
            return ZombiesMap.THE_LAB;
        if (state.is(BlockTags.WOOL_CARPETS))
            return ZombiesMap.ALIEN_ARCADIUM;
        if (block == Blocks.STONE_BRICKS)
            return ZombiesMap.BAD_BLOOD;
        if (state.is(BlockTags.WOOL))
            return ZombiesMap.DEAD_END;
        if (isTerracotta(block))
            return ZombiesMap.PRISON;
        return ZombiesMap.NULL;
    }
    private static boolean isTerracotta(Block block) {
        return block == Blocks.TERRACOTTA
                || block == Blocks.WHITE_TERRACOTTA
                || block == Blocks.ORANGE_TERRACOTTA
                || block == Blocks.MAGENTA_TERRACOTTA
                || block == Blocks.LIGHT_BLUE_TERRACOTTA
                || block == Blocks.YELLOW_TERRACOTTA
                || block == Blocks.LIME_TERRACOTTA
                || block == Blocks.PINK_TERRACOTTA
                || block == Blocks.GRAY_TERRACOTTA
                || block == Blocks.LIGHT_GRAY_TERRACOTTA
                || block == Blocks.CYAN_TERRACOTTA
                || block == Blocks.PURPLE_TERRACOTTA
                || block == Blocks.BLUE_TERRACOTTA
                || block == Blocks.BROWN_TERRACOTTA
                || block == Blocks.GREEN_TERRACOTTA
                || block == Blocks.RED_TERRACOTTA
                || block == Blocks.BLACK_TERRACOTTA;
    }

    public static double getDamageByGold(String chatMessage, ItemStack stack, boolean doubleGold) {
        ZombiesGuns currentGun = ZombiesGuns.getGunOrNull(stack);
        if (currentGun == null)
            return -1;
        int gold = getGoldFromChat(chatMessage);

        if (gold <= 0) {
            return -1;
        }

        if (doubleGold) {
            gold /= 2;
        }

        boolean critical = isCritical(chatMessage);

        int targetGold = critical ? currentGun.getCriticalGold() : currentGun.getGold();

        if (targetGold != gold) {
            return -1;
        }

        int ultimateLevel = getUltimateLevel(stack, currentGun);

        return currentGun.getDamageByUltimateLevel(ultimateLevel);
    }

    public static int getUltimateLevel(ItemStack stack, ZombiesGuns gun) {
        if (stack == null || stack.isEmpty() || gun == null) {
            return 0;
        }
        //没附魔 没有强化
        if (!stack.hasFoil()) {
            return 0;
        }
        //有附魔，但是这把枪只有一种强化伤害，直接算 Ultimate I。
        if (!gun.hasMultiUltimateDamage()) {
            return 1;
        }

        String name = cleanChat(stack.getHoverName().getString()).toUpperCase();

        int level = getRomanLevel(name);

        if (level > 0) {
            return level;
        }

        //有附魔，但没识别到等级，默认算 I。
        return 1;
    }

    private static int getRomanLevel(String text) {
        if (text == null || text.isEmpty())
            return 0;
        if (containsRoman(text, "V"))
            return 5;
        if (containsRoman(text, "IV"))
            return 4;
        if (containsRoman(text, "III"))
            return 3;
        if (containsRoman(text, "II"))
            return 2;
        if (containsRoman(text, "I"))
            return 1;
        return 0;
    }

    private static boolean containsRoman(String text, String roman) {
        return text.matches(".*\\b" + roman + "\\b.*");
    }

    public static List<ZombiesGuns> getPossibleGunsByGold(int gold, boolean critical) {
        List<ZombiesGuns> result = new ArrayList<>();

        for (ZombiesGuns gun : ZombiesGuns.values()) {
            int targetGold = critical ? gun.getCriticalGold() : gun.getGold();

            if (targetGold == gold) {
                result.add(gun);
            }
        }

        return result;
    }

    public static int getGoldFromChat(String chatMessage) {
        if (chatMessage == null || chatMessage.isEmpty()) {
            return -1;
        }

        String clean = cleanChat(chatMessage);

        Matcher matcher = NUMBER_PATTERN.matcher(clean);

        if (!matcher.find()) {
            return -1;
        }

        try {
            return Integer.parseInt(matcher.group());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static boolean isCritical(String chatMessage) {
        if (chatMessage == null || chatMessage.isEmpty()) {
            return false;
        }

        String clean = cleanChat(chatMessage);

        int open = clean.indexOf('(');
        int close = clean.indexOf(')', open + 1);

        return open != -1 && close != -1 && close > open;
    }

    public static String cleanChat(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replaceAll("§.", "")
                .replace(",", "")
                .trim();
    }
}