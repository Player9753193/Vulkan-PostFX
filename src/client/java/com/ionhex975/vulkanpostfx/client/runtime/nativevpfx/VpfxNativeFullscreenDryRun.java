package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxGraphDefinition;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxPackManifest;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxPassDefinition;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxPassInput;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public final class VpfxNativeFullscreenDryRun {

	private VpfxNativeFullscreenDryRun() {
	}

	public static void run(Minecraft minecraft, VpfxPackManifest manifest, VpfxGraphDefinition graph) {
		if (!VpfxNativeRuntimeSupport.isExecuteEnabled()) {
			return;
		}

		VpfxNativeRuntimeSupportResult supportResult = VpfxNativeRuntimeSupport.check(graph, manifest);

		if (!supportResult.isSupported()) {
			return;
		}

		RenderTarget mainTarget;
		try {
			if (minecraft == null || minecraft.gameRenderer == null) {
				VulkanPostFX.LOGGER.warn(
						"[{}] VPFX NR-1B: resource import dry-run skipped — Minecraft or GameRenderer unavailable",
						VulkanPostFX.MOD_ID
				);
				return;
			}
			mainTarget = minecraft.gameRenderer.mainRenderTarget();
		} catch (Exception e) {
			VulkanPostFX.LOGGER.warn(
					"[{}] VPFX NR-1B: resource import dry-run failed — could not obtain mainRenderTarget: {}",
					VulkanPostFX.MOD_ID,
					e.getMessage()
			);
			return;
		}

		if (mainTarget == null) {
			VulkanPostFX.LOGGER.warn(
					"[{}] VPFX NR-1B: resource import dry-run failed — mainRenderTarget is null",
					VulkanPostFX.MOD_ID
			);
			return;
		}

		int width = mainTarget.width;
		int height = mainTarget.height;

		if (width <= 0 || height <= 0) {
			VulkanPostFX.LOGGER.warn(
					"[{}] VPFX NR-1B: resource import dry-run failed — mainRenderTarget has invalid dimensions ({}x{})",
					VulkanPostFX.MOD_ID,
					width,
					height
			);
			return;
		}

		String mainTargetClass = mainTarget.getClass().getSimpleName();

		VpfxNativeResolvedResource mainResource = new VpfxNativeResolvedResource(
				"minecraft:main",
				VpfxNativeResolvedResource.Role.MAIN_OUTPUT,
				mainTargetClass,
				width,
				height
		);

		VpfxNativeResolvedResource sceneColorResource = new VpfxNativeResolvedResource(
				"minecraft:scene_color",
				VpfxNativeResolvedResource.Role.SCENE_COLOR_INPUT,
				mainTargetClass,
				width,
				height
		);

		boolean sameTargetHazard = sceneColorResource.isSameTargetAs(mainResource);

		List<VpfxNativeResolvedPass> resolvedPasses = new ArrayList<>();

		for (VpfxPassDefinition passDef : graph.getPasses()) {
			resolvedPasses.add(resolvePass(passDef, sceneColorResource, mainResource, sameTargetHazard));
		}

		VpfxNativeResolvedGraph resolvedGraph = new VpfxNativeResolvedGraph(resolvedPasses);

		boolean wouldExecute = VpfxNativeRuntimeSupport.isExecuteEnabled()
				&& supportResult.isSupported()
				&& mainTarget != null
				&& width > 0
				&& height > 0;

		VpfxNativeExecutionPlan plan = new VpfxNativeExecutionPlan(
				manifest.getPackId(),
				manifest.getName(),
				resolvedGraph,
				mainResource,
				sameTargetHazard,
				sameTargetHazard,
				wouldExecute,
				"minecraft_postchain"
		);

		logPlan(plan, mainTarget, mainTargetClass, supportResult);

		VpfxNativeFullscreenPipeline.runDryRun(manifest, resolvedGraph);

		if (plan.transientTempRequired()) {
			com.ionhex975.vulkanpostfx.client.state.PostFxRuntimeState.markPendingTransientAllocationCheck();
		}
	}

	private static VpfxNativeResolvedPass resolvePass(
			VpfxPassDefinition passDef,
			VpfxNativeResolvedResource sceneColorResource,
			VpfxNativeResolvedResource mainResource,
			boolean sameTargetHazard
	) {
		List<VpfxNativeResolvedResource> resolvedInputs = new ArrayList<>();

		for (VpfxPassInput input : passDef.getInputs()) {
			if (input.isTargetInput()) {
				String target = input.getTarget();
				if ("minecraft:scene_color".equals(target)) {
					resolvedInputs.add(sceneColorResource);
				} else if ("minecraft:main".equals(target)) {
					resolvedInputs.add(mainResource);
				} else if ("minecraft:scene_depth".equals(target)) {
					resolvedInputs.add(new VpfxNativeResolvedResource(
							target,
							VpfxNativeResolvedResource.Role.SCENE_DEPTH_INPUT,
							"unknown",
							0,
							0
					));
				} else if ("minecraft:shadow_depth".equals(target)) {
					resolvedInputs.add(new VpfxNativeResolvedResource(
							target,
							VpfxNativeResolvedResource.Role.SHADOW_DEPTH_INPUT,
							"unknown",
							0,
							0
					));
				}
			}
		}

		boolean readsSceneColor = false;
		for (VpfxPassInput input : passDef.getInputs()) {
			if ("minecraft:scene_color".equals(input.getTarget())) {
				readsSceneColor = true;
				break;
			}
		}

		boolean writesMain = "minecraft:main".equals(passDef.getOutput());

		return new VpfxNativeResolvedPass(
				passDef.identity(),
				VpfxPassType.FULLSCREEN,
				resolvedInputs,
				mainResource,
				readsSceneColor,
				writesMain,
				sameTargetHazard,
				sameTargetHazard,
				passDef.getVertexShader(),
				passDef.getFragmentShader()
		);
	}

	private static void logPlan(
			VpfxNativeExecutionPlan plan,
			RenderTarget mainTarget,
			String mainTargetClass,
			VpfxNativeRuntimeSupportResult supportResult
	) {
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1C: execution plan built for pack '{}' (id={})",
				VulkanPostFX.MOD_ID,
				plan.packName(),
				plan.packId()
		);

		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1C: resolved pass count      = {}",
				VulkanPostFX.MOD_ID,
				plan.resolvedPassCount()
		);

		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1C: temp resource count       = {} (transientColor:{})",
				VulkanPostFX.MOD_ID,
				plan.tempResourceCount(),
				plan.transientTempRequired() ? "yes" : "no"
		);

		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1C: main target              = {} ({} {}x{})",
				VulkanPostFX.MOD_ID,
				mainTargetClass,
				mainTarget,
				plan.mainTargetResource().width(),
				plan.mainTargetResource().height()
		);

		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1C: scene_color source        = {} (same as main target)",
				VulkanPostFX.MOD_ID,
				mainTargetClass
		);

		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1C: output target             = minecraft:main ({})",
				VulkanPostFX.MOD_ID,
				mainTargetClass
		);

		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1C: read/write same-target hazard = {} "
						+ "(scene_color and main reference the same RenderTarget)",
				VulkanPostFX.MOD_ID,
				plan.sameTargetHazard()
		);

		if (plan.sameTargetHazard()) {
			VulkanPostFX.LOGGER.info(
					"[{}] VPFX NR-1C: planned mitigation        = copy mainTarget color to transientColor "
							+ "before native pass; read from transientColor, write to mainTarget; "
							+ "destroy transientColor after pass",
					VulkanPostFX.MOD_ID
			);
		}

		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1C: execution plan summary: {}",
				VulkanPostFX.MOD_ID,
				plan
		);

		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1C: would execute native pass = {}",
				VulkanPostFX.MOD_ID,
				plan.wouldExecute()
		);

		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1C: no native RenderPass / RenderPipeline created. "
						+ "No draw call executed. Fallback backend = minecraft_postchain.",
				VulkanPostFX.MOD_ID
		);
	}
}
