package com.ionhex975.vulkanpostfx.client.shadow;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.mixin.LevelRendererShadowAccess;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fStack;

public final class ShadowEntityPassLite {
    private static final ProjectionMatrixBuffer SHADOW_ENTITY_PROJECTION =
            new ProjectionMatrixBuffer("vpfx_shadow_entities");

    private static SkipReason lastSkipReason;
    private static boolean firstEntitySuccessLogged;
    private static final int MAX_LOG_ERRORS_PER_FRAME = 3;

    private ShadowEntityPassLite() {
    }

    /**
     * Submits eligible entity render states as shadow casters into the VPFX
     * {@code shadow_depth} target.
     *
     * <p>Entity shadow-space coordinates are computed relative to
     * {@link ShadowFrameState#getShadowOrigin()} — not the player camera position.
     * The shadow view/projection matrices come from the shadow state and must
     * never use the player's view direction, yaw, or pitch.
     *
     * @param minecraft    the Minecraft client instance
     * @param levelRenderer the level renderer (for accessor extraction)
     * @param shadowState  the current frame shadow state
     * @param shadowTarget the VPFX shadow depth render target
     * @return the number of entities successfully submitted to the shadow pass
     */
    public static int execute(
            Minecraft minecraft,
            LevelRenderer levelRenderer,
            ShadowFrameState shadowState,
            RenderTarget shadowTarget
    ) {
        RenderSystem.assertOnRenderThread();

        // ---- Pre-checks ----

        if (minecraft == null || minecraft.level == null || levelRenderer == null
                || shadowState == null || shadowTarget == null) {
            logSkip(SkipReason.NULL_INPUT);
            return 0;
        }

        if (!shadowState.isValid() || !shadowState.isShadowPassEnabled()
                || !shadowState.isShadowTargetReady()) {
            logSkip(SkipReason.SHADOW_STATE_NOT_READY);
            return 0;
        }

        if (!shadowState.hasRenderableShadowLight()) {
            logSkip(SkipReason.NO_RENDERABLE_LIGHT);
            return 0;
        }

        if (!shadowState.isShadowEntities()) {
            logSkip(SkipReason.ENTITIES_DISABLED);
            return 0;
        }

        if (shadowTarget.getDepthTextureView() == null) {
            logSkip(SkipReason.NO_DEPTH_TEXTURE);
            return 0;
        }

        // ---- Extract LevelRenderState ----

        LevelRendererShadowAccess access = (LevelRendererShadowAccess) levelRenderer;
        LevelRenderState levelRenderState = access.vulkanpostfx$getLevelRenderState();
        if (levelRenderState == null
                || levelRenderState.entityRenderStates == null
                || levelRenderState.entityRenderStates.isEmpty()) {
            logSkip(SkipReason.NO_ENTITY_RENDER_STATES);
            return 0;
        }

        EntityRenderDispatcher entityRenderDispatcher = access.vulkanpostfx$getEntityRenderDispatcher();
        FeatureRenderDispatcher featureRenderDispatcher = access.vulkanpostfx$getFeatureRenderDispatcher();
        if (entityRenderDispatcher == null || featureRenderDispatcher == null) {
            logSkip(SkipReason.NO_DISPATCHER);
            return 0;
        }

        // ---- Shadow-space reference data ----

        Vec3 shadowOrigin = shadowState.getShadowOrigin();
        float entityShadowDistance = shadowState.getEntityShadowDistance();
        boolean shadowPlayer = shadowState.isShadowPlayer();

        // ---- Render state setup ----

        RenderSystem.backupProjectionMatrix();

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.identity();
        modelViewStack.mul(shadowState.getShadowViewMatrix());

        GpuBufferSlice projectionSlice =
                SHADOW_ENTITY_PROJECTION.getBuffer(shadowState.getShadowProjectionMatrix());
        RenderSystem.setProjectionMatrix(projectionSlice, ProjectionType.ORTHOGRAPHIC);

        int submittedCount = 0;
        int errorCount = 0;

        try {
            RenderSystem.outputColorTextureOverride = shadowTarget.getColorTextureView();
            RenderSystem.outputDepthTextureOverride = shadowTarget.getDepthTextureView();

            ShadowEntitySubmitCollector collector = new ShadowEntitySubmitCollector();

            // CameraRenderState is passed only as a vanilla submit API compatibility
            // object. Shadow-space coordinates (x, y, z) are shadowOrigin-relative and
            // do NOT read from cameraRenderState.pos, .xRot, or .yRot.
            CameraRenderState cameraRenderState = levelRenderState.cameraRenderState;

            double maxDistance = Math.max(1.0F, entityShadowDistance);

            for (EntityRenderState entityState : levelRenderState.entityRenderStates) {
                if (entityState == null) {
                    continue;
                }

                if (entityState.isInvisible) {
                    continue;
                }

                // Player-model entities: skip if shadowPlayer is disabled.
                if (!shadowPlayer && entityState instanceof AvatarRenderState) {
                    continue;
                }

                float w = entityState.boundingBoxWidth;
                float h = entityState.boundingBoxHeight;
                if (w <= 0.0F || h <= 0.0F) {
                    continue;
                }

                double dx = entityState.x - shadowOrigin.x;
                double dz = entityState.z - shadowOrigin.z;
                double padding = Math.max(2.0, (double) w);
                double limit = maxDistance + padding;
                if (dx * dx + dz * dz > limit * limit) {
                    continue;
                }

                double relX = entityState.x - shadowOrigin.x;
                double relY = entityState.y - shadowOrigin.y;
                double relZ = entityState.z - shadowOrigin.z;

                try {
                    entityRenderDispatcher.submit(
                            entityState,
                            cameraRenderState,
                            relX,
                            relY,
                            relZ,
                            new PoseStack(),
                            collector
                    );
                    submittedCount++;
                } catch (Throwable t) {
                    if (errorCount < MAX_LOG_ERRORS_PER_FRAME) {
                        VulkanPostFX.LOGGER.warn(
                                "[{}] Shadow entity submit failed for entityType={} at ({}, {}, {}): {}",
                                VulkanPostFX.MOD_ID,
                                entityState.entityType,
                                entityState.x, entityState.y, entityState.z,
                                t.toString()
                        );
                    }
                    errorCount++;
                    // Continue processing other entities.
                }
            }

            if (submittedCount > 0) {
                try (FeatureRenderDispatcher.PreparedFrame preparedFrame =
                             featureRenderDispatcher.prepareFrame(collector.storage())) {
                    preparedFrame.executeSolid();

                    // PlayerModel uses RenderTypes.entityTranslucent(...) for the
                    // skin/body mesh in Minecraft 26.2, so the player body lands in
                    // the translucent model phase even though it is a valid shadow
                    // caster. Our ShadowEntitySubmitCollector filters name tags,
                    // vanilla blob shadows, outlines, gizmos, particles, flame, and
                    // leash submits, so executing the translucent phase here is still
                    // restricted to shadow-eligible geometry.
                    preparedFrame.executeTranslucent();
                }
            }

            // ---- Success logging (rate-limited) ----

            lastSkipReason = null;

            if (!firstEntitySuccessLogged && submittedCount > 0) {
                firstEntitySuccessLogged = true;
                VulkanPostFX.LOGGER.info(
                        "[{}] Shadow entity pass submitted {} entities successfully",
                        VulkanPostFX.MOD_ID,
                        submittedCount
                );
            }

            if (errorCount > 0) {
                VulkanPostFX.LOGGER.warn(
                        "[{}] Shadow entity pass: {} entities submitted, {} errors during submit",
                        VulkanPostFX.MOD_ID,
                        submittedCount,
                        errorCount
                );
            }

            return submittedCount;
        } catch (Throwable t) {
            VulkanPostFX.LOGGER.error(
                    "[{}] Shadow entity pass dispatch failed",
                    VulkanPostFX.MOD_ID,
                    t
            );
            return 0;
        } finally {
            RenderSystem.outputColorTextureOverride = null;
            RenderSystem.outputDepthTextureOverride = null;
            modelViewStack.popMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private static void logSkip(SkipReason reason) {
        if (lastSkipReason != reason) {
            lastSkipReason = reason;
            VulkanPostFX.LOGGER.info(
                    "[{}] Shadow entity pass skipped: {}",
                    VulkanPostFX.MOD_ID,
                    reason
            );
        }
    }

    private enum SkipReason {
        NULL_INPUT,
        SHADOW_STATE_NOT_READY,
        NO_RENDERABLE_LIGHT,
        ENTITIES_DISABLED,
        NO_DEPTH_TEXTURE,
        NO_ENTITY_RENDER_STATES,
        NO_DISPATCHER
    }
}
