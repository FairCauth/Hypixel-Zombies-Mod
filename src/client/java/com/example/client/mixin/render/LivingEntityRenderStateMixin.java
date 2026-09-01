package com.example.client.mixin.render;

import com.example.client.utils.ChamsState;
import com.example.client.utils.BadHeadshotOutlineState;
import com.example.client.utils.EasyReviveBoundingBoxState;
import com.example.client.utils.HideEntityState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public class LivingEntityRenderStateMixin implements ChamsState, HideEntityState, BadHeadshotOutlineState,
        EasyReviveBoundingBoxState {

    @Unique
    private boolean zombiesmod$chams;

    @Unique
    private boolean zombiesmod$faded;

    @Unique
    private int zombiesmod$badHeadshotBoxColor;

    @Unique
    private boolean zombiesmod$showEasyReviveBoundingBox;

    @Unique
    private float zombiesmod$easyReviveMinX;
    @Unique
    private float zombiesmod$easyReviveMinY;
    @Unique
    private float zombiesmod$easyReviveMinZ;
    @Unique
    private float zombiesmod$easyReviveMaxX;
    @Unique
    private float zombiesmod$easyReviveMaxY;
    @Unique
    private float zombiesmod$easyReviveMaxZ;

    @Override
    public boolean zombiesmod$isChams() {
        return this.zombiesmod$chams;
    }

    @Override
    public void zombiesmod$setChams(boolean chams) {
        this.zombiesmod$chams = chams;
    }

    @Override
    public boolean zombiesmod$isFaded() {
        return this.zombiesmod$faded;
    }

    @Override
    public void zombiesmod$setFaded(boolean faded) {
        this.zombiesmod$faded = faded;
    }

    @Override
    public int zombiesmod$getBadHeadshotBoxColor() {
        return this.zombiesmod$badHeadshotBoxColor;
    }

    @Override
    public void zombiesmod$setBadHeadshotBoxColor(int color) {
        this.zombiesmod$badHeadshotBoxColor = color;
    }

    @Override
    public boolean zombiesmod$showEasyReviveBoundingBox() {
        return this.zombiesmod$showEasyReviveBoundingBox;
    }

    @Override
    public void zombiesmod$setEasyReviveBoundingBox(boolean show,
                                                     float minX, float minY, float minZ,
                                                     float maxX, float maxY, float maxZ) {
        this.zombiesmod$showEasyReviveBoundingBox = show;
        this.zombiesmod$easyReviveMinX = minX;
        this.zombiesmod$easyReviveMinY = minY;
        this.zombiesmod$easyReviveMinZ = minZ;
        this.zombiesmod$easyReviveMaxX = maxX;
        this.zombiesmod$easyReviveMaxY = maxY;
        this.zombiesmod$easyReviveMaxZ = maxZ;
    }

    @Override
    public float zombiesmod$getEasyReviveMinX() {
        return this.zombiesmod$easyReviveMinX;
    }

    @Override
    public float zombiesmod$getEasyReviveMinY() {
        return this.zombiesmod$easyReviveMinY;
    }

    @Override
    public float zombiesmod$getEasyReviveMinZ() {
        return this.zombiesmod$easyReviveMinZ;
    }

    @Override
    public float zombiesmod$getEasyReviveMaxX() {
        return this.zombiesmod$easyReviveMaxX;
    }

    @Override
    public float zombiesmod$getEasyReviveMaxY() {
        return this.zombiesmod$easyReviveMaxY;
    }

    @Override
    public float zombiesmod$getEasyReviveMaxZ() {
        return this.zombiesmod$easyReviveMaxZ;
    }
}
