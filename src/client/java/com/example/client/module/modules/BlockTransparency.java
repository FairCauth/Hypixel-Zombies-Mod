package com.example.client.module.modules;

import com.example.client.ZombiesModClient;
import com.example.client.language.Language;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

@ModuleInfo(name = "module.block_transparency", enable = false)
public class BlockTransparency extends AbstractModule {

    @Override
    protected void onEnable() {
        rebuildChunks();
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        rebuildChunks();
        super.onDisable();
    }

    private void rebuildChunks() {
        if (mc.levelRenderer != null) {
            mc.levelRenderer.resetLevelRenderData();
        }
    }
    public static final int ALPHA = 128;
    public static boolean isActive() {
        AbstractModule m = ZombiesModClient.moduleManager.getModule("Slab See-Through");
        return m != null && m.isEnable();
    }

    public static int alphaMultiplier() {
        return (ALPHA << 24) | 0x00FFFFFF;
    }

    public static boolean isTarget(BlockState state) {
        if (state == null) return false;
        Block b = state.getBlock();
        return b instanceof SlabBlock || b instanceof StairBlock;
    }
}
