package com.ionhex975.vulkanpostfx.client.shadow;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;

import java.util.List;

/**
 * Filtered SubmitNodeCollector for the VPFX entity shadow pass.
 *
 * Wraps a {@link SubmitNodeStorage} and only delegates submit calls that produce
 * actual geometry useful for shadow map rendering. Vanilla shadow blobs,
 * name tags, text, flame, leash, outlines, gizmos, and particles are silently
 * dropped so they never enter the shadow_depth target.
 *
 * {@code submitModel} clears {@code outlineColor} and {@code crumblingOverlay}
 * before forwarding so that glowing outlines and block-destruction overlays
 * do not pollute shadow depth.
 */
public final class ShadowEntitySubmitCollector implements SubmitNodeCollector {

    private final SubmitNodeStorage storage;

    public ShadowEntitySubmitCollector() {
        this.storage = new SubmitNodeStorage();
    }

    /**
     * Returns the inner {@link SubmitNodeStorage} populated with shadow-eligible
     * entity submits, ready for {@code FeatureRenderDispatcher.prepareFrame(...)}.
     */
    public SubmitNodeStorage storage() {
        return this.storage;
    }

    // ---- SubmitNodeCollector ----

    @Override
    public SubmitNodeCollection order(int order) {
        return this.storage.order(order);
    }

    // ---- Allowed submits (forwarded to storage) ----

    @Override
    public <S> void submitModel(net.minecraft.client.model.Model<? super S> model, S data, PoseStack poseStack,
                                RenderType renderType, int packedLight, int packedOverlay, int tintedColor,
                                TextureAtlasSprite sprite, int outlineColor,
                                ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        // Preserve the original tintedColor. Player skins and many entity models
        // pass -1 here; replacing it with 0 makes the model fully transparent
        // and prevents it from contributing useful shadow depth. Only clear the
        // outline and crumbling overlay channels.
        this.storage.submitModel(model, data, poseStack, renderType, packedLight, packedOverlay,
                tintedColor, sprite, 0, null);
    }

    @Override
    public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext,
                           int packedLight, int overlay, int outlineColor, int[] tints,
                           List<BakedQuad> quads, net.minecraft.client.renderer.item.ItemStackRenderState.FoilType foilType) {
        this.storage.submitItem(poseStack, displayContext, packedLight, overlay, 0, tints, quads, foilType);
    }

    @Override
    public void submitBlockModel(PoseStack poseStack, RenderType renderType,
                                 List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> parts,
                                 int[] tints, int packedLight, int overlay, int outlineColor) {
        this.storage.submitBlockModel(poseStack, renderType, parts, tints, packedLight, overlay, 0);
    }

    @Override
    public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState renderState, int outlineColor) {
        this.storage.submitMovingBlock(poseStack, renderState, 0);
    }

    // ---- Filtered submits (no-op — must not enter shadow map) ----

    @Override
    public void submitShadow(PoseStack poseStack, float radius,
                             List<EntityRenderState.ShadowPiece> shadowPieces) {
        // Vanilla circular shadow blob — not a VPFX shadow caster.
    }

    @Override
    public void submitNameTag(PoseStack poseStack, net.minecraft.world.phys.Vec3 pos,
                              int packedLight, Component component, boolean seeThrough,
                              int color, CameraRenderState cameraState) {
    }

    @Override
    public void submitText(PoseStack poseStack, float x, float y,
                           FormattedCharSequence text, boolean seeThrough,
                           net.minecraft.client.gui.Font.DisplayMode displayMode,
                           int packedLight, int color, int backgroundColor, int opacity) {
    }

    @Override
    public void submitFlame(PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation) {
    }

    @Override
    public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {
    }

    @Override
    public void submitShapeOutline(PoseStack poseStack, VoxelShape shape,
                                   RenderType renderType, int color, float lineWidth,
                                   boolean disableDepthTest) {
    }

    @Override
    public void submitBreakingBlockModel(PoseStack poseStack,
                                         List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> parts,
                                         int color) {
    }

    @Override
    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType,
                                     SubmitNodeCollector.CustomGeometryRenderer renderer) {
    }

    @Override
    public void submitQuadParticleGroup(QuadParticleRenderState particleGroup) {
    }

    @Override
    public void submitGizmoPrimitives(
            net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives.Group group,
            CameraRenderState cameraState, boolean depthTest) {
    }
}
