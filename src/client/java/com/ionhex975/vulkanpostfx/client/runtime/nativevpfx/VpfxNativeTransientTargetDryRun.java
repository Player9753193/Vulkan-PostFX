package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.diagnostics.VpfxDiagnosticsConfig;
import com.ionhex975.vulkanpostfx.client.state.PostFxRuntimeState;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.Minecraft;

import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.graph.VpfxNativeGraphPlanner;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.graph.VpfxNativeGraphPlanResult;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.framegraph.VpfxNativeFrameGraphExecutor;

public final class VpfxNativeTransientTargetDryRun {

	private static final GpuFormat TRANSIENT_FORMAT = GpuFormat.RGBA8_UNORM;

	private static int lastWidth;
	private static int lastHeight;
	private static boolean nr2aPlanLogged;

	private VpfxNativeTransientTargetDryRun() {
	}

	public static void requestCheck() {
		if (!VpfxNativeRuntimeSupport.isExecuteEnabled()) {
			return;
		}

		PostFxRuntimeState.markPendingTransientAllocationCheck();
	}

	public static VpfxNativeExecutionResult attemptDiagnosticDraw(Minecraft minecraft) {
		if (!VpfxNativeRuntimeSupport.isExecuteEnabled()) {
			return VpfxNativeExecutionResult.builder()
					.attempted(false)
					.fallbackReason("execute flag not enabled")
					.failureStage(VpfxNativeFailureStage.EXECUTE_FLAG_DISABLED)
					.failureMessage("nativeRuntime.execute flag not set")
					.postChainFallbackExpected(true)
					.build();
		}

		if (!RenderSystem.isOnRenderThread()) {
			return VpfxNativeExecutionResult.builder()
					.attempted(true)
					.fallbackReason("not on Render thread")
					.failureStage(VpfxNativeFailureStage.NOT_RENDER_THREAD)
					.failureMessage("attemptDiagnosticDraw must be called on Render thread")
					.postChainFallbackExpected(true)
					.build();
		}

		RenderTarget mainTarget;
		try {
			if (minecraft == null || minecraft.gameRenderer == null) {
				return VpfxNativeExecutionResult.builder()
						.attempted(true)
						.fallbackReason("Minecraft or GameRenderer unavailable")
						.failureStage(VpfxNativeFailureStage.MAIN_TARGET_UNAVAILABLE)
						.failureMessage("minecraft or gameRenderer is null")
					.postChainFallbackExpected(true)
						.build();
			}
			mainTarget = minecraft.gameRenderer.mainRenderTarget();
		} catch (Exception e) {
			return VpfxNativeExecutionResult.builder()
					.attempted(true)
					.fallbackReason("could not obtain mainRenderTarget: " + e.getMessage())
					.failureStage(VpfxNativeFailureStage.MAIN_TARGET_UNAVAILABLE)
					.failureMessage("mainRenderTarget access threw: " + e.getMessage())
					.postChainFallbackExpected(true)
					.build();
		}

		if (mainTarget == null) {
			return VpfxNativeExecutionResult.builder()
					.attempted(true)
					.fallbackReason("mainRenderTarget is null")
					.failureStage(VpfxNativeFailureStage.MAIN_TARGET_UNAVAILABLE)
					.failureMessage("mainRenderTarget resolved to null")
					.postChainFallbackExpected(true)
					.build();
		}

		int width = mainTarget.width;
		int height = mainTarget.height;

		if (width <= 0 || height <= 0) {
			return VpfxNativeExecutionResult.builder()
					.attempted(true)
					.fallbackReason("mainRenderTarget has invalid dimensions (" + width + "x" + height + ")")
					.failureStage(VpfxNativeFailureStage.INVALID_MAIN_TARGET_DIMENSIONS)
					.failureMessage("dimensions: " + width + "x" + height)
					.postChainFallbackExpected(true)
					.build();
		}

		VpfxNativeGraphPlanResult nativeFrameGraphPlan = VpfxNativeGraphPlanner.plan();
		if (nativeFrameGraphPlan.planSupported() && nativeFrameGraphPlan.passCount() > 0) {
			if (VpfxDiagnosticsConfig.legacyNativeDiagnosticsEnabled() && !nr2aPlanLogged) {
				nr2aPlanLogged = true;
				VulkanPostFX.LOGGER.info(
						"[{}] VPFX NR-1.5: graph plan summary → planAttempted={}, planSupported={}, passCount={}, targetCount={}, firstPass={}, inputs=[{}], outputs=[{}], samplerConvention={}, unsupportedReason={}, fallbackExpected={}",
						VulkanPostFX.MOD_ID,
						nativeFrameGraphPlan.planAttempted(),
						nativeFrameGraphPlan.planSupported(),
						nativeFrameGraphPlan.passCount(),
						nativeFrameGraphPlan.targetCount(),
						nativeFrameGraphPlan.firstPassName(),
						nativeFrameGraphPlan.inputBindings(),
						nativeFrameGraphPlan.outputBindings(),
						nativeFrameGraphPlan.samplerConvention(),
						nativeFrameGraphPlan.unsupportedReason(),
						nativeFrameGraphPlan.fallbackExpected()
				);
			}
			return VpfxNativeFrameGraphExecutor.execute(minecraft);
		}

		if (lastWidth > 0 && lastHeight > 0 && (width != lastWidth || height != lastHeight)) {
			VulkanPostFX.LOGGER.info(
					"[{}] VPFX NR-1G-A: mainTarget size changed for native transient draw: "
							+ "old={}x{}, new={}x{}; transientColor will be recreated for current frame",
					VulkanPostFX.MOD_ID,
					lastWidth,
					lastHeight,
					width,
					height
			);
		}
		lastWidth = width;
		lastHeight = height;

		TextureTarget transientTarget = null;
		VpfxNativeExecutionResult result;

		try {
			try {
				transientTarget = new TextureTarget(
						"VPFX NR-1F-D diagnostic transientColor",
						width,
						height,
						false,
						TRANSIENT_FORMAT
				);
			} catch (Exception e) {
				return VpfxNativeExecutionResult.builder()
						.attempted(true)
						.fallbackReason("transientColor creation failed: " + e.getMessage())
						.failureStage(VpfxNativeFailureStage.TRANSIENT_TARGET_CREATE)
						.failureMessage("TextureTarget creation threw: " + e.getMessage())
					.postChainFallbackExpected(true)
						.build();
			}

			GpuTexture sourceTexture = mainTarget.getColorTexture();
			GpuTexture destTexture = transientTarget.getColorTexture();

			if (sourceTexture == null || sourceTexture.isClosed()) {
				return VpfxNativeExecutionResult.builder()
						.attempted(true)
						.copySucceeded(false)
						.fallbackReason("mainTarget color texture unavailable or closed")
						.failureStage(VpfxNativeFailureStage.COPY_MAIN_TO_TRANSIENT)
						.failureMessage("mainTarget color texture is null or closed")
					.postChainFallbackExpected(true)
						.build();
			}
			if (destTexture == null || destTexture.isClosed()) {
				return VpfxNativeExecutionResult.builder()
						.attempted(true)
						.copySucceeded(false)
						.fallbackReason("transientColor texture unavailable or closed")
						.failureStage(VpfxNativeFailureStage.COPY_MAIN_TO_TRANSIENT)
						.failureMessage("transientColor texture is null or closed")
					.postChainFallbackExpected(true)
						.build();
			}

			try {
				CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
				encoder.copyTextureToTexture(
						sourceTexture,
						destTexture,
						0, 0, 0, 0, 0,
						width,
						height
				);
			} catch (Exception e) {
				return VpfxNativeExecutionResult.builder()
						.attempted(true)
						.copySucceeded(false)
						.fallbackReason("copyTextureToTexture failed: " + e.getMessage())
						.failureStage(VpfxNativeFailureStage.COPY_MAIN_TO_TRANSIENT)
						.failureMessage("copyTextureToTexture threw: " + e.getMessage())
					.postChainFallbackExpected(true)
						.build();
			}

			VpfxNativeUserPipelineResolveResult userPipelineResult =
					VpfxNativeUserShaderDryRun.resolveOrCreateForActivePackOnRenderThread();

			boolean userAvail = userPipelineResult.available() && userPipelineResult.pipeline() != null;

			if (VpfxDiagnosticsConfig.legacyNativeDiagnosticsEnabled() && !nr2aPlanLogged) {
				nr2aPlanLogged = true;
				VpfxNativeGraphPlanResult plan = VpfxNativeGraphPlanner.plan();
				VulkanPostFX.LOGGER.info(
						"[{}] VPFX NR-2A: graph plan summary → planAttempted={}, planSupported={}, passCount={}, targetCount={}, firstPass={}, inputs=[{}], outputs=[{}], samplerConvention={}, unsupportedReason={}, fallbackExpected={}",
						VulkanPostFX.MOD_ID,
						plan.planAttempted(),
						plan.planSupported(),
						plan.passCount(),
						plan.targetCount(),
						plan.firstPassName(),
						plan.inputBindings(),
						plan.outputBindings(),
						plan.samplerConvention(),
						plan.unsupportedReason(),
						plan.fallbackExpected()
				);
			}

			result = VpfxNativeFullscreenExecutor.execute(
					mainTarget,
					transientTarget.getColorTextureView(),
					true,
					userAvail ? userPipelineResult.pipeline() : null,
					userAvail ? "user:" + userPipelineResult.key().packId() : null,
					userPipelineResult.attempted(),
					userAvail,
					userPipelineResult.successCached(),
					userAvail ? "none" : userPipelineResult.fallbackReason(),
					userAvail ? VpfxNativeFailureStage.NONE : userPipelineResult.failureStage(),
					userAvail ? "none" : userPipelineResult.failureMessage()
			);
		} catch (Exception e) {
			result = VpfxNativeExecutionResult.builder()
					.attempted(true)
					.copySucceeded(false)
					.fallbackReason("native draw preparation failed: " + e.getMessage())
					.failureStage(VpfxNativeFailureStage.NATIVE_DRAW_FAILED)
					.failureMessage("unexpected exception in draw preparation: " + e.getMessage())
					.postChainFallbackExpected(true)
					.build();
		} finally {
			if (transientTarget != null) {
				try {
					transientTarget.destroyBuffers();
				} catch (Exception ignored) {
				}
			}
		}

		return result;
	}

	public static void runOnRenderThread(Minecraft minecraft) {
		VpfxNativeExecutionResult result = attemptDiagnosticDraw(minecraft);
		VpfxNativeFullscreenExecutor.logDiagnosticSummaryOnce(result);
	}

	public static void resetNr2aPlanLog() {
		nr2aPlanLogged = false;
	}
}
