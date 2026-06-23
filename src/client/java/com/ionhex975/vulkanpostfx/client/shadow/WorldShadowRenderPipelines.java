package com.ionhex975.vulkanpostfx.client.shadow;

import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;

public final class WorldShadowRenderPipelines {
    private WorldShadowRenderPipelines() {
    }

    private static final BindGroupLayout VPFX_TERRAIN_SHADOW =
            BindGroupLayout.builder().withUniform(WorldShadowUniformBuffer.BLOCK_NAME, UniformType.UNIFORM_BUFFER).build();

    public static final RenderPipeline.Snippet SHADOWED_TERRAIN_SNIPPET = RenderPipeline.builder(
                    RenderPipelines.GENERIC_BLOCKS_SNIPPET
            )
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.CHUNK_SECTION)
            .withBindGroupLayout(VPFX_TERRAIN_SHADOW)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
            .withVertexShader("core/vpfx_world_shadow_terrain")
            .withFragmentShader("core/vpfx_world_shadow_terrain")
            .buildSnippet();

    public static final RenderPipeline SHADOWED_TERRAIN_SOLID = RenderPipeline.builder(SHADOWED_TERRAIN_SNIPPET)
            .withLocation("pipeline/vpfx_shadowed_terrain_solid")
            .build();

    public static final RenderPipeline SHADOWED_TERRAIN_CUTOUT = RenderPipeline.builder(SHADOWED_TERRAIN_SNIPPET)
            .withLocation("pipeline/vpfx_shadowed_terrain_cutout")
            .withShaderDefine("ALPHA_CUTOUT", 0.5F)
            .build();
}
