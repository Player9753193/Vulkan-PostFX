package com.ionhex975.vulkanpostfx.client.mixin;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.shadow.ShadowRenderTargetsLite;
import com.ionhex975.vulkanpostfx.client.shadow.WorldShadowRenderPipelines;
import com.ionhex975.vulkanpostfx.client.shadow.WorldShadowUniformBuffer;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

@Mixin(ChunkSectionsToRender.class)
public abstract class ChunkSectionsToRenderWorldShadowMixin {
    @Unique
    private static boolean vulkanpostfx$firstFallbackLogged;

    @Inject(
            method = "renderGroup",
            at = @At("HEAD"),
            cancellable = true
    )
    private void vulkanpostfx$renderGroupWithWorldShadow(
            ChunkSectionLayerGroup group,
            GpuSampler sampler,
            CallbackInfo ci
    ) {
        if (group != ChunkSectionLayerGroup.OPAQUE || !WorldShadowUniformBuffer.isWorldShadowEnabled()) {
            return;
        }

        ChunkSectionsToRender self = (ChunkSectionsToRender) (Object) this;
        ShadowRenderTargetsLite shadowTargets = ShadowRenderTargetsLite.get();
        RenderTarget shadowTarget = shadowTargets.getShadowDepthTarget();
        GpuTextureView shadowDepthView = shadowTarget != null ? shadowTarget.getDepthTextureView() : null;
        if (shadowDepthView == null) {
            return;
        }

        RenderSystem.AutoStorageIndexBuffer autoIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer defaultIndexBuffer = self.maxIndicesRequired() == 0 ? null : autoIndices.getBuffer(self.maxIndicesRequired());
        IndexType defaultIndexType = self.maxIndicesRequired() == 0 ? null : autoIndices.type();
        Minecraft minecraft = Minecraft.getInstance();
        boolean wireframe = SharedConstants.DEBUG_HOTKEYS && minecraft.wireframe;
        if (wireframe) {
            return;
        }

        RenderTarget renderTarget = group.outputTarget();
        GpuSampler shadowSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        GpuBufferSlice shadowUniform = WorldShadowUniformBuffer.writeAndGet();

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                        () -> "Section layers for " + group.label() + " with VPFX world shadow",
                        renderTarget.getColorTextureView(),
                        Optional.empty(),
                        renderTarget.getDepthTextureView(),
                        OptionalDouble.empty()
                )) {
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.bindTexture("Sampler0", self.textureView(), sampler);
            renderPass.bindTexture("Sampler1", shadowDepthView, shadowSampler);
            renderPass.bindTexture("Sampler2", minecraft.gameRenderer.lightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            renderPass.setUniform(WorldShadowUniformBuffer.BLOCK_NAME, shadowUniform);

            for (ChunkSectionLayer layer : group.layers()) {
                RenderPipeline pipeline = switch (layer) {
                    case SOLID -> WorldShadowRenderPipelines.SHADOWED_TERRAIN_SOLID;
                    case CUTOUT -> WorldShadowRenderPipelines.SHADOWED_TERRAIN_CUTOUT;
                    default -> RenderPipelines.WIREFRAME;
                };

                renderPass.setPipeline(pipeline);
                Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>> drawGroup = self.drawGroupsPerLayer().get(layer);
                if (drawGroup == null) {
                    continue;
                }

                for (List<RenderPass.Draw<GpuBufferSlice[]>> draws : drawGroup.values()) {
                    if (draws == null || draws.isEmpty()) {
                        continue;
                    }

                    renderPass.drawMultipleIndexed(
                            draws,
                            defaultIndexBuffer,
                            defaultIndexType,
                            List.of("ChunkSection"),
                            self.chunkSectionInfos()
                    );
                }
            }

            ci.cancel();
        } catch (Throwable t) {
            if (!vulkanpostfx$firstFallbackLogged) {
                vulkanpostfx$firstFallbackLogged = true;
                VulkanPostFX.LOGGER.error(
                        "[{}] World shadow OPAQUE render failed, falling back to vanilla terrain render",
                        VulkanPostFX.MOD_ID,
                        t
                );
            }
        }
    }
}
