package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.framegraph;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.VpfxNativePipelineKey;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.graph.VpfxNativeInputBinding;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.graph.VpfxNativePassNode;
import com.ionhex975.vulkanpostfx.client.shader.uniform.VpfxBuiltinUniformBuffer;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class VpfxNativeFrameGraphPipelineCache {
    private static final BindGroupLayout VPFX_BUILTINS_LAYOUT =
            BindGroupLayout.builder()
                    .withUniform(VpfxBuiltinUniformBuffer.BLOCK_NAME, UniformType.UNIFORM_BUFFER)
                    .build();

    private static final Map<VpfxNativePipelineKey, RenderPipeline> SUCCESS_CACHE = new LinkedHashMap<>();
    private static final Map<VpfxNativePipelineKey, String> FAILURE_CACHE = new LinkedHashMap<>();

    private VpfxNativeFrameGraphPipelineCache() {
    }

    public static RenderPipeline getOrCreate(
            String runtimeNamespace,
            String packId,
            VpfxNativePassNode pass
    ) {
        VpfxNativePipelineKey key = createKey(packId, pass);

        RenderPipeline cached = SUCCESS_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        if (FAILURE_CACHE.containsKey(key)) {
            throw new IllegalStateException("cached pipeline failure: " + FAILURE_CACHE.get(key));
        }

        try {
            Identifier vertexShaderId = Identifier.fromNamespaceAndPath(
                    runtimeNamespace,
                    extractShaderPath(pass.vertexShaderRef())
            );
            Identifier fragmentShaderId = Identifier.fromNamespaceAndPath(
                    runtimeNamespace,
                    extractShaderPath(pass.fragmentShaderRef())
            );

            BindGroupLayout samplerLayout = buildSamplerLayout(pass);

            RenderPipeline pipeline = RenderPipeline.builder()
                    .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                    .withBindGroupLayout(samplerLayout)
                    .withBindGroupLayout(VPFX_BUILTINS_LAYOUT)
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .withCull(false)
                    .withVertexShader(vertexShaderId)
                    .withFragmentShader(fragmentShaderId)
                    .withLocation("pipeline/vpfx_native_framegraph/" + safeLocation(pass.passId()))
                    .build();

            SUCCESS_CACHE.put(key, pipeline);

            VulkanPostFX.LOGGER.info(
                    "[{}] VPFX NR-2.0: RenderPipeline created for pass '{}': vs={}, fs={}, samplers=[{}], uniforms=[{}]",
                    VulkanPostFX.MOD_ID,
                    pass.passId(),
                    vertexShaderId,
                    fragmentShaderId,
                    samplerConvention(pass),
                    VpfxBuiltinUniformBuffer.BLOCK_NAME
            );
            return pipeline;
        } catch (Throwable t) {
            FAILURE_CACHE.put(key, t.getMessage());
            throw new IllegalStateException(
                    "failed to create native framegraph pipeline for pass '" + pass.passId() + "': " + t.getMessage(),
                    t
            );
        }
    }

    public static void clear() {
        SUCCESS_CACHE.clear();
        FAILURE_CACHE.clear();
    }

    private static VpfxNativePipelineKey createKey(String packId, VpfxNativePassNode pass) {
        return new VpfxNativePipelineKey(
                packId,
                pass.passId(),
                pass.passType(),
                pass.vertexShaderRef(),
                pass.fragmentShaderRef(),
                "RGBA8_UNORM",
                "",
                "",
                samplerConvention(pass) + ";uniform=" + VpfxBuiltinUniformBuffer.BLOCK_NAME
        );
    }

    private static BindGroupLayout buildSamplerLayout(VpfxNativePassNode pass) {
        var builder = BindGroupLayout.builder();
        for (VpfxNativeInputBinding input : pass.inputs()) {
            builder.withSampler(input.glslSamplerName());
        }
        return builder.build();
    }

    private static String samplerConvention(VpfxNativePassNode pass) {
        return pass.inputs().stream()
                .map(VpfxNativeInputBinding::glslSamplerName)
                .collect(Collectors.joining(","));
    }

    private static String extractShaderPath(String ref) {
        if (ref == null) {
            throw new IllegalArgumentException("shader ref is null");
        }
        int colon = ref.indexOf(':');
        if (colon < 0 || colon == ref.length() - 1) {
            throw new IllegalArgumentException("shader ref must be namespace:path, got " + ref);
        }
        return ref.substring(colon + 1);
    }

    private static String safeLocation(String passId) {
        return passId == null ? "pass" : passId.replaceAll("[^A-Za-z0-9_./-]", "_");
    }
}
