package com.ionhex975.vulkanpostfx.client.shadow;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import com.mojang.blaze3d.shaders.UniformType;

public final class ShadowRenderPipelines {
    private static final BindGroupLayout VPFX_TERRAIN_SHADOW =
            BindGroupLayout.builder().withUniform(WorldShadowUniformBuffer.BLOCK_NAME, UniformType.UNIFORM_BUFFER).build();

    private ShadowRenderPipelines() {
    }

    public static final RenderPipeline.Snippet SHADOW_TERRAIN_SNIPPET = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.CHUNK_SECTION)
            .withBindGroupLayout(VPFX_TERRAIN_SHADOW)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withVertexBinding(0, DefaultVertexFormat.BLOCK)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withCull(false)
            .withVertexShader("core/shadow_terrain")
            .withFragmentShader("core/shadow_terrain")
            .buildSnippet();

    public static final RenderPipeline SHADOW_TERRAIN_SOLID = RenderPipeline.builder(SHADOW_TERRAIN_SNIPPET)
            .withLocation("pipeline/vpfx_shadow_terrain_solid")
            .build();

    public static final RenderPipeline SHADOW_TERRAIN_CUTOUT = RenderPipeline.builder(SHADOW_TERRAIN_SNIPPET)
            .withLocation("pipeline/vpfx_shadow_terrain_cutout")
            .withShaderDefine("ALPHA_CUTOUT", 0.5F)
            .build();
}
