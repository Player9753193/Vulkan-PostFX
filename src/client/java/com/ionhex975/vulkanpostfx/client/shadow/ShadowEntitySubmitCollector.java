package com.ionhex975.vulkanpostfx.client.shadow;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * Filtered {@link SubmitNodeCollector} for the VPFX entity shadow pass.
 *
 * <p>Minecraft 26.3 snapshot moved more entity rendering into submit-node based
 * extraction. Extending {@link SubmitNodeStorage} keeps this collector compatible
 * with vanilla/Fabric interface additions while still allowing VPFX to filter the
 * submits that should not enter the shadow-depth target.</p>
 *
 * <p>Only actual geometry that can contribute to a shadow map is forwarded.
 * Vanilla shadow blobs, name tags, text, flame, leash, outlines, gizmos,
 * particles, custom geometry, and breaking overlays are dropped.</p>
 */
public final class ShadowEntitySubmitCollector extends SubmitNodeStorage {

    /**
     * Returns this collector as the storage object expected by
     * {@code FeatureRenderDispatcher.prepareFrame(...)}.
     */
    public SubmitNodeStorage storage() {
        return this;
    }

    // ---- Allowed submits (forwarded, but outline / crumbling overlays removed) ----

    @Override
    public <S> void submitModel(
            Model<? super S> model,
            S state,
            PoseStack poseStack,
            RenderType renderType,
            int lightCoords,
            int overlayCoords,
            int tintedColor,
            @Nullable UvMapping uvMapping,
            int outlineColor,
            ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
    ) {
        // Preserve the original tintedColor. Player skins and many entity models
        // pass -1 here; replacing it with 0 can make the model fully transparent.
        // Only clear outline and crumbling overlay for the shadow-depth pass.
        super.submitModel(
                model,
                state,
                poseStack,
                renderType,
                lightCoords,
                overlayCoords,
                tintedColor,
                uvMapping,
                0,
                null
        );
    }

    @Override
    public void submitItem(
            PoseStack poseStack,
            ItemDisplayContext displayContext,
            int lightCoords,
            int overlayCoords,
            int outlineColor,
            int[] tintLayers,
            List<BakedQuad> quads,
            ItemStackRenderState.FoilType foilType
    ) {
        super.submitItem(poseStack, displayContext, lightCoords, overlayCoords, 0, tintLayers, quads, foilType);
    }

    @Override
    public void submitBlockModel(
            PoseStack poseStack,
            RenderType renderType,
            List<BlockStateModelPart> parts,
            int[] tintLayers,
            int lightCoords,
            int overlayCoords,
            int outlineColor
    ) {
        super.submitBlockModel(poseStack, renderType, parts, tintLayers, lightCoords, overlayCoords, 0);
    }

    @Override
    public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState, int outlineColor) {
        super.submitMovingBlock(poseStack, movingBlockRenderState, 0);
    }

    // ---- Filtered submits (no-op — must not enter shadow map) ----

    @Override
    public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
        // Vanilla circular shadow blob — not a VPFX shadow caster.
    }

    @Override
    public void submitNameTag(
            PoseStack poseStack,
            @Nullable Vec3 nameTagAttachment,
            int offset,
            Component name,
            boolean seeThrough,
            int lightCoords,
            CameraRenderState camera
    ) {
        // Name tags must never write into shadow depth.
    }

    @Override
    public void submitText(
            PoseStack poseStack,
            float x,
            float y,
            FormattedCharSequence string,
            boolean dropShadow,
            Font.DisplayMode displayMode,
            int lightCoords,
            int color,
            int backgroundColor,
            int outlineColor
    ) {
        // Text and labels must never write into shadow depth.
    }

    @Override
    public void submitFlame(PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation) {
        // Dropped for now. Fire is a light/emissive visual, not solid shadow geometry.
    }

    @Override
    public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {
        // Leashes are thin auxiliary geometry; exclude from the shadow map.
    }

    @Override
    public void submitShapeOutline(PoseStack poseStack, VoxelShape shape, RenderType renderType, int color, float width, boolean afterTerrain) {
        // Debug/selection outlines must not enter shadow depth.
    }

    @Override
    public void submitBreakingBlockModel(PoseStack poseStack, List<BlockStateModelPart> parts, int progress) {
        // Block breaking overlay must not enter shadow depth.
    }

    @Override
    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, SubmitNodeCollector.CustomGeometryRenderer renderer) {
        // Unknown custom geometry is intentionally dropped from the shadow-depth pass.
    }

    @Override
    public void submitQuadParticleGroup(QuadParticleRenderState particles) {
        // Particles are screen/world effects, not stable shadow casters.
    }

    @Override
    public void submitGizmoPrimitives(
            net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives.Group group,
            CameraRenderState camera,
            boolean onTop
    ) {
        // Gizmos/debug primitives must not enter shadow depth.
    }
}
