package com.ionhex975.vulkanpostfx.client.shadow;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.mixin.LevelRendererViewAreaAccessor;
import com.ionhex975.vulkanpostfx.client.mixin.LevelRendererShadowAccess;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public final class ShadowTerrainPassLite {
    private static final ProjectionMatrixBuffer SHADOW_PROJECTION =
            new ProjectionMatrixBuffer("vpfx_shadow");
    private static final ChunkSectionLayer[] SHADOW_CASTER_LAYERS = new ChunkSectionLayer[]{
            ChunkSectionLayer.SOLID,
            ChunkSectionLayer.CUTOUT
    };

    private static SkipReason lastSkipReason;
    private static boolean firstTerrainSuccessLogged;

    private ShadowTerrainPassLite() {
    }

    public static boolean execute(
            Minecraft minecraft,
            LevelRenderer levelRenderer,
            ShadowFrameState shadowState,
            RenderTarget shadowTarget
    ) {
        RenderSystem.assertOnRenderThread();

        if (minecraft.level == null) {
            logSkip(SkipReason.NO_LEVEL);
            return false;
        }

        if (levelRenderer == null) {
            logSkip(SkipReason.NO_LEVEL_RENDERER);
            return false;
        }

        if (shadowTarget == null) {
            logSkip(SkipReason.NO_SHADOW_TARGET);
            return false;
        }

        ChunkSectionsToRender shadowChunks = buildShadowChunkRenders(
                minecraft,
                levelRenderer,
                shadowState.getShadowViewMatrix(),
                shadowState.getShadowProjectionMatrix(),
                shadowState.getShadowOrigin(),
                shadowState.getTerrainShadowDistance()
        );

        if (!hasShadowRelevantDraws(shadowChunks)) {
            logSkip(SkipReason.NO_RELEVANT_DRAWS);
            return false;
        }

        RenderSystem.backupProjectionMatrix();

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.identity();

        try {
            GpuBufferSlice projectionSlice = SHADOW_PROJECTION.getBuffer(
                    shadowState.getShadowProjectionMatrix()
            );
            RenderSystem.setProjectionMatrix(projectionSlice, ProjectionType.ORTHOGRAPHIC);

            renderShadowTerrainGroups(
                    minecraft,
                    shadowChunks,
                    shadowTarget
            );

            lastSkipReason = null;

            if (!firstTerrainSuccessLogged) {
                firstTerrainSuccessLogged = true;
                VulkanPostFX.LOGGER.info(
                        "[{}] Shadow terrain pass submitted custom shadow-only terrain pipelines successfully",
                        VulkanPostFX.MOD_ID
                );
            }

            return true;
        } catch (Throwable t) {
            VulkanPostFX.LOGGER.error(
                    "[{}] Shadow terrain pass failed during custom shadow terrain submission",
                    VulkanPostFX.MOD_ID,
                    t
            );
            return false;
        } finally {
            modelViewStack.popMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private static boolean hasShadowRelevantDraws(ChunkSectionsToRender chunkSections) {
        if (chunkSections == null) {
            return false;
        }

        for (ChunkSectionLayer layer : SHADOW_CASTER_LAYERS) {
            Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>> drawGroup =
                    chunkSections.drawGroupsPerLayer().get(layer);

            if (drawGroup == null || drawGroup.isEmpty()) {
                continue;
            }

            for (List<RenderPass.Draw<GpuBufferSlice[]>> draws : drawGroup.values()) {
                if (draws != null && !draws.isEmpty()) {
                    return true;
                }
            }
        }

        return false;
    }

    private static ChunkSectionsToRender buildShadowChunkRenders(
            Minecraft minecraft,
            LevelRenderer levelRenderer,
            Matrix4fc shadowModelViewMatrix,
            Matrix4fc shadowProjectionMatrix,
            Vec3 shadowCameraPos,
            float terrainShadowDistance
    ) {
        LevelRendererShadowAccess access = (LevelRendererShadowAccess) levelRenderer;
        ViewArea viewArea = access.vulkanpostfx$getViewArea();
        List<SectionRenderDispatcher.RenderSection> visibleSections = access.vulkanpostfx$getVisibleSections();

        if (viewArea == null || visibleSections == null) {
            return emptyChunkRenders(minecraft);
        }

        LevelRendererViewAreaAccessor viewAreaAccessor = (LevelRendererViewAreaAccessor) viewArea;
        var sections = viewAreaAccessor.vulkanpostfx$getSections();
        if (sections.size() == 0) {
            return emptyChunkRenders(minecraft);
        }

        List<SectionRenderDispatcher.RenderSection> mainVisibleSections = new ArrayList<>(visibleSections);

        try {
            visibleSections.clear();
            collectShadowVisibleSections(
                    viewArea,
                    visibleSections,
                    shadowModelViewMatrix,
                    shadowProjectionMatrix,
                    shadowCameraPos,
                    terrainShadowDistance
            );

            if (visibleSections.isEmpty()) {
                return emptyChunkRenders(minecraft);
            }

            return levelRenderer.prepareChunkRenders(shadowModelViewMatrix);
        } finally {
            visibleSections.clear();
            visibleSections.addAll(mainVisibleSections);
        }
    }

    private static void collectShadowVisibleSections(
            ViewArea viewArea,
            List<SectionRenderDispatcher.RenderSection> visibleSections,
            Matrix4fc shadowModelViewMatrix,
            Matrix4fc shadowProjectionMatrix,
            Vec3 shadowCameraPos,
            float terrainShadowDistance
    ) {
        Frustum shadowFrustum = new Frustum(
                shadowModelViewMatrix,
                new Matrix4f(shadowProjectionMatrix)
        );
        shadowFrustum.prepare(shadowCameraPos.x, shadowCameraPos.y, shadowCameraPos.z);

        double maxDistance = terrainShadowDistance + 24.0;
        double maxDistanceSquared = maxDistance * maxDistance;

        LevelRendererViewAreaAccessor viewAreaAccessor = (LevelRendererViewAreaAccessor) viewArea;
        for (SectionRenderDispatcher.RenderSection section : viewAreaAccessor.vulkanpostfx$getSections()) {
            if (section == null) {
                continue;
            }

            BlockPos renderOrigin = section.getRenderOrigin();
            double centerX = renderOrigin.getX() + 8.0;
            double centerZ = renderOrigin.getZ() + 8.0;
            double dx = centerX - shadowCameraPos.x;
            double dz = centerZ - shadowCameraPos.z;
            if (dx * dx + dz * dz > maxDistanceSquared) {
                continue;
            }

            AABB sectionBounds = new AABB(
                    renderOrigin.getX(),
                    renderOrigin.getY(),
                    renderOrigin.getZ(),
                    renderOrigin.getX() + 16.0,
                    renderOrigin.getY() + 16.0,
                    renderOrigin.getZ() + 16.0
            );

            if (shadowFrustum.isVisible(sectionBounds)) {
                visibleSections.add(section);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static ChunkSectionsToRender emptyChunkRenders(Minecraft minecraft) {
        EnumMap<ChunkSectionLayer, Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>>> drawGroups =
                new EnumMap<>(ChunkSectionLayer.class);

        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            drawGroups.put(layer, new Int2ObjectOpenHashMap<>());
        }

        var blockAtlas = minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
        return new ChunkSectionsToRender(blockAtlas, drawGroups, 0, new GpuBufferSlice[0]);
    }

    private static void renderShadowTerrainGroups(
            Minecraft minecraft,
            ChunkSectionsToRender chunkSections,
            RenderTarget renderTarget
    ) {
        RenderSystem.AutoStorageIndexBuffer autoIndices =
                RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer defaultIndexBuffer =
                chunkSections.maxIndicesRequired() == 0 ? null : autoIndices.getBuffer(chunkSections.maxIndicesRequired());
        IndexType defaultIndexType =
                chunkSections.maxIndicesRequired() == 0 ? null : autoIndices.type();

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                        () -> "VulkanPostFX Shadow Terrain Custom",
                        renderTarget.getColorTextureView(),
                        Optional.empty(),
                        renderTarget.getDepthTextureView(),
                        OptionalDouble.empty()
                )) {
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform(WorldShadowUniformBuffer.BLOCK_NAME, WorldShadowUniformBuffer.writeAndGetForShadowPass());
            GpuSampler blockAtlasSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            renderPass.bindTexture("Sampler0", chunkSections.textureView(), blockAtlasSampler);

            submitLayer(
                    renderPass,
                    chunkSections,
                    ChunkSectionLayer.SOLID,
                    ShadowRenderPipelines.SHADOW_TERRAIN_SOLID,
                    defaultIndexBuffer,
                    defaultIndexType
            );

            submitLayer(
                    renderPass,
                    chunkSections,
                    ChunkSectionLayer.CUTOUT,
                    ShadowRenderPipelines.SHADOW_TERRAIN_CUTOUT,
                    defaultIndexBuffer,
                    defaultIndexType
            );
        }
    }

    private static void submitLayer(
            RenderPass renderPass,
            ChunkSectionsToRender chunkSections,
            ChunkSectionLayer sourceLayer,
            RenderPipeline shadowPipeline,
            GpuBuffer defaultIndexBuffer,
            IndexType defaultIndexType
    ) {
        Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>> drawGroup =
                chunkSections.drawGroupsPerLayer().get(sourceLayer);

        if (drawGroup == null || drawGroup.isEmpty()) {
            return;
        }

        renderPass.setPipeline(shadowPipeline);

        for (List<RenderPass.Draw<GpuBufferSlice[]>> draws : drawGroup.values()) {
            if (draws == null || draws.isEmpty()) {
                continue;
            }

            renderPass.drawMultipleIndexed(
                    draws,
                    defaultIndexBuffer,
                    defaultIndexType,
                    List.of("ChunkSection"),
                    chunkSections.chunkSectionInfos()
            );
        }
    }

    private static void logSkip(SkipReason reason) {
        if (lastSkipReason != reason) {
            lastSkipReason = reason;
            VulkanPostFX.LOGGER.info(
                    "[{}] Shadow terrain pass skipped: {}",
                    VulkanPostFX.MOD_ID,
                    reason
            );
        }
    }

    private enum SkipReason {
        NO_LEVEL,
        NO_LEVEL_RENDERER,
        NO_SHADOW_TARGET,
        NO_RELEVANT_DRAWS
    }
}
