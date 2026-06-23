package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.diagnostics.VpfxDiagnosticsConfig;
import com.mojang.blaze3d.pipeline.RenderPipeline;

import java.util.LinkedHashMap;
import java.util.Map;

public final class VpfxNativeUserPipelineCache {

	private static final Map<VpfxNativePipelineKey, RenderPipeline> successCache = new LinkedHashMap<>();
	private static final Map<VpfxNativePipelineKey, String> failureCache = new LinkedHashMap<>();

	private VpfxNativeUserPipelineCache() {
	}

	public static RenderPipeline getSuccess(VpfxNativePipelineKey key) {
		return successCache.get(key);
	}

	public static boolean isSuccessCached(VpfxNativePipelineKey key) {
		return successCache.containsKey(key);
	}

	public static boolean isFailedCached(VpfxNativePipelineKey key) {
		return failureCache.containsKey(key);
	}

	public static String getFailureReason(VpfxNativePipelineKey key) {
		return failureCache.get(key);
	}

	public static void putSuccess(VpfxNativePipelineKey key, RenderPipeline pipeline) {
		failureCache.remove(key);
		successCache.put(key, pipeline);
		logPostPutProbe(key);
	}

	public static void putFailure(VpfxNativePipelineKey key, String reason) {
		successCache.remove(key);
		failureCache.put(key, reason != null ? reason : "unknown");
		logPostPutProbe(key);
	}

	public static boolean hasAnyEntry(VpfxNativePipelineKey key) {
		return isSuccessCached(key) || isFailedCached(key);
	}

	public static String cacheDiagnostics(VpfxNativePipelineKey key) {
		int successSize = successCache.size();
		int failureSize = failureCache.size();
		int totalSize = successSize + failureSize;
		int keyHashCode = key.hashCode();
		boolean contains = hasAnyEntry(key);
		return "cacheTotalSize=" + totalSize
				+ ", successSize=" + successSize
				+ ", failureSize=" + failureSize
				+ ", keyHashCode=" + keyHashCode
				+ ", containsKey=" + contains;
	}

	public static String postPutDiagnostics(VpfxNativePipelineKey key) {
		int totalSize = successCache.size() + failureCache.size();
		boolean contains = hasAnyEntry(key);
		return "postPut cacheTotalSize=" + totalSize
				+ ", containsKey=" + contains;
	}

	public static void clear() {
		if (VpfxDiagnosticsConfig.legacyNativeDiagnosticsEnabled()) {
			VulkanPostFX.LOGGER.info(
					"[{}] VPFX NR-1F-C.2: user shader pipeline cache cleared. successCount={}, failureCount={}",
					VulkanPostFX.MOD_ID,
					successCache.size(),
					failureCache.size()
			);
		}
		successCache.clear();
		failureCache.clear();
	}

	public static int successCount() {
		return successCache.size();
	}

	public static int failureCount() {
		return failureCache.size();
	}

	private static void logPostPutProbe(VpfxNativePipelineKey key) {
		boolean probeHit = hasAnyEntry(key);
		boolean probeSuccessCached = isSuccessCached(key);
		boolean probeFailureCached = isFailedCached(key);
		boolean wouldSkipCreation = probeHit;

		if (!VpfxDiagnosticsConfig.legacyNativeDiagnosticsEnabled()) {
			if (!probeHit) {
				VulkanPostFX.LOGGER.warn(
						"[{}] VPFX NR-1F-C.2: post-put cache probe FAILED — key not found. diagnostics={}",
						VulkanPostFX.MOD_ID,
						cacheDiagnostics(key)
				);
			}
			return;
		}

		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C.2: post-put immediate cache probe hit       = {}",
				VulkanPostFX.MOD_ID,
				probeHit
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C.2: post-put success cached                   = {}",
				VulkanPostFX.MOD_ID,
				probeSuccessCached
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C.2: post-put failure cached                   = {}",
				VulkanPostFX.MOD_ID,
				probeFailureCached
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C.2: post-put would skip RenderPipeline creation= {}",
				VulkanPostFX.MOD_ID,
				wouldSkipCreation
		);

		if (!probeHit) {
			VulkanPostFX.LOGGER.warn(
					"[{}] VPFX NR-1F-C.2: post-put cache probe FAILED — key not found. diagnostics={}",
					VulkanPostFX.MOD_ID,
					cacheDiagnostics(key)
			);
		}
	}
}
