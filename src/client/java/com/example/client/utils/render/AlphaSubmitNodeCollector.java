package com.example.client.utils.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;

import java.util.List;
import java.util.ArrayList;

public final class AlphaSubmitNodeCollector implements SubmitNodeCollector {
    private static final int ITEM_ALPHA_MARKER_PREFIX = 0x5AF00000;
    private final SubmitNodeCollector delegate;
    private final float alpha;

    private AlphaSubmitNodeCollector(SubmitNodeCollector delegate, float alpha) {
        this.delegate = delegate;
        this.alpha = Math.max(0.0F, Math.min(1.0F, alpha));
    }

    public static SubmitNodeCollector wrap(SubmitNodeCollector delegate, int alpha) {
        return alpha >= 255 ? delegate : new AlphaSubmitNodeCollector(delegate, alpha / 255.0F);
    }

    @Override public OrderedSubmitNodeCollector order(int order) {
        return new AlphaOrderedSubmitNodeCollector(delegate.order(order));
    }
    @Override public void submitShadow(PoseStack p, float r, List<EntityRenderState.ShadowPiece> pieces) { delegate.submitShadow(p, r, pieces); }
    @Override public void submitNameTag(PoseStack p, Vec3 o, int l, Component t, boolean d, int b, CameraRenderState c) { delegate.submitNameTag(p, o, l, t, d, b, c); }
    @Override public void submitText(PoseStack p, float x, float y, FormattedCharSequence t, boolean d, Font.DisplayMode m, int c, int b, int l, int o) { delegate.submitText(p, x, y, t, d, m, c, b, l, o); }
    @Override public void submitFlame(PoseStack p, EntityRenderState s, Quaternionf r) { delegate.submitFlame(p, s, r); }
    @Override public void submitLeash(PoseStack p, EntityRenderState.LeashState s) { delegate.submitLeash(p, s); }

    @Override
    public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType,
                                int light, int overlay, int color, TextureAtlasSprite sprite, int outlineColor,
                                ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        delegate.submitModel(model, state, poseStack, renderType, light, overlay, multiplyAlpha(color), sprite, outlineColor, crumblingOverlay);
    }

    @Override public void submitMovingBlock(PoseStack p, MovingBlockRenderState s, int l) { delegate.submitMovingBlock(p, s, l); }
    @Override public void submitBlockModel(PoseStack p, RenderType r, List<BlockStateModelPart> parts, int[] t, int l, int o, int c) { delegate.submitBlockModel(p, r, parts, t, l, o, c); }
    @Override public void submitBreakingBlockModel(PoseStack p, List<BlockStateModelPart> parts, int s) { delegate.submitBreakingBlockModel(p, parts, s); }
    @Override public void submitShapeOutline(PoseStack p, VoxelShape s, RenderType r, int c, float w, boolean a) { delegate.submitShapeOutline(p, s, r, c, w, a); }

    @Override
    public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int light, int overlay,
                           int outlineColor, int[] tints,
                           List<net.minecraft.client.resources.model.geometry.BakedQuad> quads,
                           ItemStackRenderState.FoilType foilType) {
        delegate.submitItem(poseStack, displayContext, light, overlay, outlineColor, alphaMarkerTints(tints, quads),
            translucentQuads(quads), foilType);
    }

    @Override public void submitCustomGeometry(PoseStack p, RenderType r, SubmitNodeCollector.CustomGeometryRenderer g) { delegate.submitCustomGeometry(p, r, g); }
    @Override public void submitQuadParticleGroup(QuadParticleRenderState s) { delegate.submitQuadParticleGroup(s); }
    @Override public void submitGizmoPrimitives(DrawableGizmoPrimitives.Group g, CameraRenderState c, boolean a) { delegate.submitGizmoPrimitives(g, c, a); }

    private final class AlphaOrderedSubmitNodeCollector implements OrderedSubmitNodeCollector {
        private final OrderedSubmitNodeCollector delegate;

        private AlphaOrderedSubmitNodeCollector(OrderedSubmitNodeCollector delegate) {
            this.delegate = delegate;
        }

        @Override public void submitShadow(PoseStack p, float r, List<EntityRenderState.ShadowPiece> pieces) { delegate.submitShadow(p, r, pieces); }
        @Override public void submitNameTag(PoseStack p, Vec3 o, int l, Component t, boolean d, int b, CameraRenderState c) { delegate.submitNameTag(p, o, l, t, d, b, c); }
        @Override public void submitText(PoseStack p, float x, float y, FormattedCharSequence t, boolean d, Font.DisplayMode m, int c, int b, int l, int o) { delegate.submitText(p, x, y, t, d, m, c, b, l, o); }
        @Override public void submitFlame(PoseStack p, EntityRenderState s, Quaternionf r) { delegate.submitFlame(p, s, r); }
        @Override public void submitLeash(PoseStack p, EntityRenderState.LeashState s) { delegate.submitLeash(p, s); }

        @Override
        public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType,
                                    int light, int overlay, int color, TextureAtlasSprite sprite, int outlineColor,
                                    ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
            delegate.submitModel(model, state, poseStack, renderType, light, overlay,
                    multiplyAlpha(color), sprite, outlineColor, crumblingOverlay);
        }

        @Override public void submitMovingBlock(PoseStack p, MovingBlockRenderState s, int l) { delegate.submitMovingBlock(p, s, l); }
        @Override public void submitBlockModel(PoseStack p, RenderType r, List<BlockStateModelPart> parts, int[] t, int l, int o, int c) { delegate.submitBlockModel(p, r, parts, t, l, o, c); }
        @Override public void submitBreakingBlockModel(PoseStack p, List<BlockStateModelPart> parts, int s) { delegate.submitBreakingBlockModel(p, parts, s); }
        @Override public void submitShapeOutline(PoseStack p, VoxelShape s, RenderType r, int c, float w, boolean a) { delegate.submitShapeOutline(p, s, r, c, w, a); }
        @Override public void submitItem(PoseStack p, ItemDisplayContext d, int l, int o, int c, int[] t, List<BakedQuad> q, ItemStackRenderState.FoilType f) { delegate.submitItem(p, d, l, o, c, alphaMarkerTints(t, q), translucentQuads(q), f); }
        @Override public void submitCustomGeometry(PoseStack p, RenderType r, SubmitNodeCollector.CustomGeometryRenderer g) { delegate.submitCustomGeometry(p, r, g); }
        @Override public void submitQuadParticleGroup(QuadParticleRenderState s) { delegate.submitQuadParticleGroup(s); }
        @Override public void submitGizmoPrimitives(DrawableGizmoPrimitives.Group g, CameraRenderState c, boolean a) { delegate.submitGizmoPrimitives(g, c, a); }
    }

    private int multiplyAlpha(int color) {
        return (color & 0x00FFFFFF) | Math.round((color >>> 24) * alpha) << 24;
    }

    private int[] alphaMarkerTints(int[] tints, List<BakedQuad> quads) {
        int[] result = tints == null ? new int[0] : tints.clone();
        int markerIndex = result.length;
        for (BakedQuad quad : quads) {
            markerIndex = Math.max(markerIndex, quad.materialInfo().tintIndex() + 1);
        }
        int[] markedResult = java.util.Arrays.copyOf(result, markerIndex + 1);
        java.util.Arrays.fill(markedResult, result.length, markerIndex, -1);
        markedResult[markerIndex] = ITEM_ALPHA_MARKER_PREFIX | Math.round(alpha * 255.0F);
        return markedResult;
    }

    private List<BakedQuad> translucentQuads(List<BakedQuad> quads) {
        List<BakedQuad> result = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            BakedQuad.MaterialInfo material = quad.materialInfo();
            BakedQuad.MaterialInfo translucentMaterial = new BakedQuad.MaterialInfo(
                    material.sprite(),
                    material.layer(),
                    RenderTypes.itemTranslucent(material.sprite().atlasLocation()),
                    material.tintIndex(),
                    material.shade(),
                    material.lightEmission()
            );
            result.add(new BakedQuad(
                    quad.position0(), quad.position1(), quad.position2(), quad.position3(),
                    quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(),
                    quad.direction(), translucentMaterial
            ));
        }
        return result;
    }

    public static boolean isItemAlphaMarker(int color) {
        return (color & 0xFFFFFF00) == ITEM_ALPHA_MARKER_PREFIX;
    }

    public static int applyItemAlphaMarker(int color, int marker) {
        int alpha = marker & 0xFF;
        return (color & 0x00FFFFFF) | Math.round((color >>> 24) * (alpha / 255.0F)) << 24;
    }
}
