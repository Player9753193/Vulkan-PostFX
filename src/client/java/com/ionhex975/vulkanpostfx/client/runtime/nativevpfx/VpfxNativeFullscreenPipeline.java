package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxPackManifest;

public final class VpfxNativeFullscreenPipeline {

	public static final String BUILTIN_VERTEX_SHADER_PATH = "assets/minecraft/shaders/core/vpfx_native_fullscreen_passthru.vsh";
	public static final String BUILTIN_FRAGMENT_SHADER_PATH = "assets/minecraft/shaders/core/vpfx_native_fullscreen_passthru.fsh";
	public static final String SAMPLER_NAME = "Sampler0";
	public static final String PRIMITIVE = "fullscreen triangle";
	public static final String TOPOLOGY = "TRIANGLES";
	public static final String DEPTH_STATE = "disabled";
	public static final String BLEND_STATE = "disabled";
	public static final String CULL_STATE = "disabled";
	public static final String OUTPUT = "minecraft:main";

	private VpfxNativeFullscreenPipeline() {
	}

	public static void runDryRun(VpfxPackManifest manifest, VpfxNativeResolvedGraph resolvedGraph) {
		if (!VpfxNativeRuntimeSupport.isExecuteEnabled()) {
			return;
		}

		if (resolvedGraph == null || resolvedGraph.isEmpty()) {
			return;
		}

		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1C: pipeline skeleton dry-run starting for pack '{}' (id={})",
				VulkanPostFX.MOD_ID,
				manifest.getName(),
				manifest.getPackId()
		);

		for (VpfxNativeResolvedPass resolvedPass : resolvedGraph.resolvedPasses()) {
			VulkanPostFX.LOGGER.info(
					"[{}] VPFX NR-1C: pipeline skeleton enabled for pass '{}'",
					VulkanPostFX.MOD_ID,
					resolvedPass.identity()
			);

			VulkanPostFX.LOGGER.info(
					"[{}] VPFX NR-1C: pack id                       = {}",
					VulkanPostFX.MOD_ID,
					manifest.getPackId()
			);

			VulkanPostFX.LOGGER.info(
					"[{}] VPFX NR-1C: pass id                       = {}",
					VulkanPostFX.MOD_ID,
					resolvedPass.identity()
			);

			VulkanPostFX.LOGGER.info(
					"[{}] VPFX NR-1C: pass type                     = {}",
					VulkanPostFX.MOD_ID,
					resolvedPass.passType()
			);

			VulkanPostFX.LOGGER.info(
					"[{}] VPFX NR-1C: vertex shader reference       = {}",
					VulkanPostFX.MOD_ID,
					resolvedPass.vertexShader()
			);

			VulkanPostFX.LOGGER.info(
					"[{}] VPFX NR-1C: fragment shader reference     = {}",
					VulkanPostFX.MOD_ID,
					resolvedPass.fragmentShader()
			);

			VulkanPostFX.LOGGER.info(
					"[{}] VPFX NR-1C: builtin passthrough fallback shader paths = "
							+ "{} / {}",
					VulkanPostFX.MOD_ID,
					BUILTIN_VERTEX_SHADER_PATH,
					BUILTIN_FRAGMENT_SHADER_PATH
			);

			VulkanPostFX.LOGGER.info(
					"[{}] VPFX NR-1C: primitive topology planned     = {}",
					VulkanPostFX.MOD_ID,
					TOPOLOGY
			);

			VulkanPostFX.LOGGER.info(
					"[{}] VPFX NR-1C: fullscreen primitive planned   = {}",
					VulkanPostFX.MOD_ID,
					PRIMITIVE
			);

			VulkanPostFX.LOGGER.info(
					"[{}] VPFX NR-1C: depth test planned             = {}",
					VulkanPostFX.MOD_ID,
					DEPTH_STATE
			);

			VulkanPostFX.LOGGER.info(
					"[{}] VPFX NR-1C: blend planned                  = {}",
					VulkanPostFX.MOD_ID,
					BLEND_STATE
			);

			VulkanPostFX.LOGGER.info(
					"[{}] VPFX NR-1C: cull planned                   = {}",
					VulkanPostFX.MOD_ID,
					CULL_STATE
			);

			VulkanPostFX.LOGGER.info(
					"[{}] VPFX NR-1C: sampler planned                = {}",
					VulkanPostFX.MOD_ID,
					SAMPLER_NAME
			);

			VulkanPostFX.LOGGER.info(
					"[{}] VPFX NR-1C: output planned                 = {}",
					VulkanPostFX.MOD_ID,
					OUTPUT
			);

			VulkanPostFX.LOGGER.info(
					"[{}] VPFX NR-1C: pipeline cache key             = {}",
					VulkanPostFX.MOD_ID,
					buildKey(manifest, resolvedPass)
			);
		}

		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1C: no RenderPipeline created. No RenderPass created. "
						+ "No draw call executed. Fallback backend = minecraft_postchain.",
				VulkanPostFX.MOD_ID
		);

		VpfxNativeUserShaderDryRun.run(manifest, resolvedGraph);
	}

	public static VpfxNativePipelineKey buildKey(VpfxPackManifest manifest, VpfxNativeResolvedPass resolvedPass) {
		return new VpfxNativePipelineKey(
				manifest.getPackId(),
				resolvedPass.identity(),
				resolvedPass.passType(),
				resolvedPass.vertexShader(),
				resolvedPass.fragmentShader(),
				"RGBA8_UNORM"
		);
	}
}
