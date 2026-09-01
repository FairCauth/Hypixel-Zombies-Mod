package com.example.client.utils;

public interface EasyReviveBoundingBoxState {
    boolean zombiesmod$showEasyReviveBoundingBox();

    void zombiesmod$setEasyReviveBoundingBox(boolean show,
                                              float minX, float minY, float minZ,
                                              float maxX, float maxY, float maxZ);

    float zombiesmod$getEasyReviveMinX();

    float zombiesmod$getEasyReviveMinY();

    float zombiesmod$getEasyReviveMinZ();

    float zombiesmod$getEasyReviveMaxX();

    float zombiesmod$getEasyReviveMaxY();

    float zombiesmod$getEasyReviveMaxZ();
}
