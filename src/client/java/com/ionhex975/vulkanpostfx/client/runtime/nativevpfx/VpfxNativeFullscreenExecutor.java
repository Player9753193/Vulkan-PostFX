package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.diagnostics.VpfxDiagnosticsConfig;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.BindGroupLayouts;

import java.util.Optional;
import java.util.OptionalDouble;

public final class VpfxNativeFullscreenExecutor {

	private static final String SHADER_NAME = "core/vpfx_native_fullscreen_passthru";

	private static final BindGroupLayout PASSTHRU_SAMPLER_LAYOUT =
			BindGroupLayout.builder().withSampler("Sampler0").build();

	private static final BindGroupLayout USER_SAMPLER_LAYOUT =
			BindGroupLayout.builder().withSampler("InSampler").build();

	private static volatile RenderPipeline cachedPipeline;
	private static boolean pipelineCreationFailed;

	private static boolean firstDrawLogged;
	private static boolean firstDiagnosticSummaryLogged;

	private VpfxNativeFullscreenExecutor() {
	}

	public static VpfxNativeExecutionResult execute(RenderTarget mainTarget, GpuTextureView transientColorView, boolean copySucceeded) {
		return execute(mainTarget, transientColorView, copySucceeded, null, null,
				false, false, false, "none",
				VpfxNativeFailureStage.NONE, "none");
	}

	public static VpfxNativeExecutionResult execute(
			RenderTarget mainTarget,
			GpuTextureView transientColorView,
			boolean copySucceeded,
			RenderPipeline preferredPipeline,
			String pipelineLabel,
			boolean userPipelineAttempted,
			boolean userPipelineAvailable,
			boolean userPipelineCached,
			String pipelineFallbackReason,
			VpfxNativeFailureStage upstreamFailureStage,
			String upstreamFailureMessage
	) {
		boolean userShaderPipeline = preferredPipeline != null && userPipelineAvailable;
		boolean userPipelineFallback = userPipelineAttempted && !userPipelineAvailable;
		String effectiveLabel = pipelineLabel != null ? pipelineLabel : "builtin passthrough";

		VpfxNativeExecutionResult.Builder result = VpfxNativeExecutionResult.builder()
				.attempted(true)
				.copySucceeded(copySucceeded)
				.userPipelineAttempted(userPipelineAttempted)
				.userPipelineAvailable(userPipelineAvailable)
				.userPipelineCached(userPipelineCached)
				.userPipelineFallbackUsed(userPipelineFallback)
				.actualPipeline(userShaderPipeline ? effectiveLabel : "builtin passthrough")
				.userShaderNativeExecution(userShaderPipeline)
				.builtinPassthroughOnly(!userShaderPipeline)
				.pipelineFallbackReason(pipelineFallbackReason);

		if (userPipelineFallback && upstreamFailureStage != VpfxNativeFailureStage.NONE) {
			result.failureStage(upstreamFailureStage)
					.failureMessage(upstreamFailureMessage);
		}

		if (!VpfxNativeRuntimeSupport.isExecuteEnabled()) {
			return result.fallbackReason("execute flag not enabled")
					.failureStage(VpfxNativeFailureStage.EXECUTE_FLAG_DISABLED)
					.failureMessage("nativeRuntime.execute flag not set")
					.postChainFallbackExpected(true)
					.build();
		}

		if (mainTarget == null || transientColorView == null) {
			return result.fallbackReason("mainTarget or transientColorView is null")
					.failureStage(VpfxNativeFailureStage.MAIN_TARGET_UNAVAILABLE)
					.failureMessage("mainTarget or transientColorView is null")
					.postChainFallbackExpected(true)
					.build();
		}

		if (!RenderSystem.isOnRenderThread()) {
			return result.fallbackReason("not on Render thread")
					.failureStage(VpfxNativeFailureStage.NOT_RENDER_THREAD)
					.failureMessage("execute() must be called on Render thread")
					.postChainFallbackExpected(true)
					.build();
		}

		RenderPipeline pipeline;
		boolean pipelineFromCache = false;
		boolean builtinPipelineCreationSet = false;
		if (preferredPipeline != null) {
			pipeline = preferredPipeline;
			pipelineFromCache = userPipelineCached;
			result.pipelineCreated(!pipelineFromCache);
		} else {
			pipeline = getOrCreatePipeline(result);
			if (result.failureStage.equals(VpfxNativeFailureStage.BUILTIN_PIPELINE_CREATE)) {
				builtinPipelineCreationSet = true;
			}
		}

		if (pipeline == null) {
			if (!builtinPipelineCreationSet) {
				result.failureStage(VpfxNativeFailureStage.NATIVE_DRAW_FAILED)
						.failureMessage("no pipeline available and not created");
			}
			result.postChainFallbackExpected(true);
			return result.build();
		}

		String samplerName = userShaderPipeline ? "InSampler" : "Sampler0";
		GpuTextureView colorView = mainTarget.getColorTextureView();
		GpuTextureView depthView = mainTarget.getDepthTextureView();
		GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

		try (RenderPass renderPass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(
						() -> "VPFX NR-1F-D native fullscreen draw: " + effectiveLabel,
						colorView,
						Optional.empty(),
						depthView,
						OptionalDouble.empty()
				)) {

			result.renderPassCreated(true);

			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setPipeline(pipeline);
			renderPass.bindTexture(samplerName, transientColorView, sampler);
			renderPass.draw(3, 1, 0, 0);

			result.drawExecuted(true).nativeSucceeded(true);

			if (!firstDrawLogged) {
				firstDrawLogged = true;
				VulkanPostFX.LOGGER.info(
						"[{}] VPFX NR-1F-D: native fullscreen draw executed successfully. "
								+ "pipeline={}, userShader={}, builtinOnly={}",
						VulkanPostFX.MOD_ID,
						effectiveLabel,
						userShaderPipeline,
						!userShaderPipeline
				);
			}

			return result.build();
		} catch (Throwable t) {
			String reason = "draw failed: " + t.getMessage();

			if (userShaderPipeline) {
				VulkanPostFX.LOGGER.warn(
						"[{}] VPFX NR-1F-D: user shader draw failed: {}. "
								+ "Attempting fallback to builtin passthrough.",
						VulkanPostFX.MOD_ID,
						t.getMessage()
				);

				result.failureStage(VpfxNativeFailureStage.USER_SHADER_DRAW)
						.failureMessage(reason)
						.builtinFallbackAttempted(true);

				RenderPipeline fallback = getOrCreatePipeline(result);
				if (fallback != null) {
					try (RenderPass fallbackPass = RenderSystem.getDevice()
							.createCommandEncoder()
							.createRenderPass(
									() -> "VPFX NR-1F-D fallback builtin passthrough",
									mainTarget.getColorTextureView(),
									Optional.empty(),
									mainTarget.getDepthTextureView(),
									OptionalDouble.empty()
							)) {

						RenderSystem.bindDefaultUniforms(fallbackPass);
						fallbackPass.setPipeline(fallback);
						fallbackPass.bindTexture("Sampler0", transientColorView, sampler);
						fallbackPass.draw(3, 1, 0, 0);

						return result
								.userShaderNativeExecution(false)
								.builtinPassthroughOnly(true)
								.userPipelineFallbackUsed(true)
								.actualPipeline("builtin passthrough (user failed)")
								.pipelineFallbackReason(reason)
								.builtinFallbackSucceeded(true)
								.drawExecuted(true)
								.nativeSucceeded(true)
								.build();
					} catch (Throwable ft) {
						return result
								.fallbackReason("user draw failed and builtin fallback also failed: " + ft.getMessage())
								.failureStage(VpfxNativeFailureStage.BUILTIN_FALLBACK_DRAW)
								.failureMessage("builtin fallback draw threw: " + ft.getMessage())
								.builtinFallbackSucceeded(false)
								.postChainFallbackExpected(true)
								.build();
					}
				} else {
					return result
							.fallbackReason("user draw failed and builtin fallback pipeline unavailable")
							.builtinFallbackAttempted(true)
							.builtinFallbackSucceeded(false)
							.postChainFallbackExpected(true)
							.build();
				}
			}

			result.fallbackReason(reason)
					.failureStage(VpfxNativeFailureStage.NATIVE_DRAW_FAILED)
					.failureMessage(reason);
			return result.build();
		}
	}

	private static RenderPipeline getOrCreatePipeline(VpfxNativeExecutionResult.Builder result) {
		if (pipelineCreationFailed) {
			result.fallbackReason("pipeline creation previously failed")
					.failureStage(VpfxNativeFailureStage.BUILTIN_PIPELINE_CREATE)
					.failureMessage("builtin passthrough RenderPipeline creation previously failed");
			return null;
		}

		if (cachedPipeline != null) {
			return cachedPipeline;
		}

		try {
			cachedPipeline = RenderPipeline.builder()
					.withBindGroupLayout(BindGroupLayouts.GLOBALS)
					.withBindGroupLayout(PASSTHRU_SAMPLER_LAYOUT)
					.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
					.withCull(false)
					.withVertexShader(SHADER_NAME)
					.withFragmentShader(SHADER_NAME)
					.withLocation("pipeline/vpfx_native_fullscreen_passthru")
					.build();

			result.pipelineCreated(true);

			if (VpfxDiagnosticsConfig.legacyNativeDiagnosticsEnabled()) {
				VulkanPostFX.LOGGER.info(
						"[{}] VPFX NR-1F-D: RenderPipeline created: shader={}, pipeline={}",
						VulkanPostFX.MOD_ID,
						SHADER_NAME,
						cachedPipeline
				);
			}

			return cachedPipeline;
		} catch (Throwable t) {
			pipelineCreationFailed = true;
			result.fallbackReason("pipeline creation failed: " + t.getMessage())
					.failureStage(VpfxNativeFailureStage.BUILTIN_PIPELINE_CREATE)
					.failureMessage("builtin passthrough RenderPipeline creation threw: " + t.getMessage());
			VulkanPostFX.LOGGER.error(
					"[{}] VPFX NR-1F-D: RenderPipeline creation failed: {}. "
							+ "Native fullscreen pass will not be available this session.",
					VulkanPostFX.MOD_ID,
					t.getMessage()
			);
			return null;
		}
	}

	public static void logDiagnosticSummaryOnce(VpfxNativeExecutionResult result) {
		if (result == null) {
			return;
		}
		if (result.nativeSucceeded() && !VpfxDiagnosticsConfig.legacyNativeDiagnosticsEnabled()) {
			return;
		}
		if (firstDiagnosticSummaryLogged) {
			return;
		}
		firstDiagnosticSummaryLogged = true;

		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-D: === DIAGNOSTIC NATIVE FULLSCREEN DRAW SUMMARY ===",
				VulkanPostFX.MOD_ID
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-D: diagnostic native draw succeeded   = {}",
				VulkanPostFX.MOD_ID,
				result.nativeSucceeded()
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-D: actual pipeline                     = {}",
				VulkanPostFX.MOD_ID,
				result.actualPipeline()
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-D: user shader native execution       = {}",
				VulkanPostFX.MOD_ID,
				result.userShaderNativeExecution()
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-D: builtin passthrough only           = {}",
				VulkanPostFX.MOD_ID,
				result.builtinPassthroughOnly()
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-D: user pipeline attempted            = {}",
				VulkanPostFX.MOD_ID,
				result.userPipelineAttempted()
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-D: user pipeline available             = {}",
				VulkanPostFX.MOD_ID,
				result.userPipelineAvailable()
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-D: user pipeline cached                = {}",
				VulkanPostFX.MOD_ID,
				result.userPipelineCached()
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-D: user pipeline fallback used         = {}",
				VulkanPostFX.MOD_ID,
				result.userPipelineFallbackUsed()
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-D: pipeline fallback reason            = {}",
				VulkanPostFX.MOD_ID,
				result.pipelineFallbackReason()
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-D: failure stage                         = {}",
				VulkanPostFX.MOD_ID,
				result.failureStage()
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-D: failure message                        = {}",
				VulkanPostFX.MOD_ID,
				result.failureMessage()
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-D: builtin fallback attempted            = {}",
				VulkanPostFX.MOD_ID,
				result.builtinFallbackAttempted()
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-D: builtin fallback succeeded            = {}",
				VulkanPostFX.MOD_ID,
				result.builtinFallbackSucceeded()
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-D: postChain fallback expected           = {}",
				VulkanPostFX.MOD_ID,
				result.postChainFallbackExpected()
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-D: minecraft_postchain remains the default/fallback backend; actual frame pipeline reported by actualPipeline",
				VulkanPostFX.MOD_ID
		);
	}

	public static void invalidatePipeline() {
		cachedPipeline = null;
		pipelineCreationFailed = false;
		firstDrawLogged = false;
		firstDiagnosticSummaryLogged = false;
	}
}
