package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.pack.ShaderPackContainer;
import com.ionhex975.vulkanpostfx.client.runtime.vpfx.VpfxRuntimeBackend;
import com.ionhex975.vulkanpostfx.client.runtime.vpfx.VpfxRuntimeBackendCapabilities;
import com.ionhex975.vulkanpostfx.client.runtime.zip.RuntimeZipPackMaterializationResult;
import com.ionhex975.vulkanpostfx.client.runtime.zip.ZipPackMaterializer;

import java.io.IOException;
import java.nio.file.Path;

/**
 * VPFX native direct runtime backend v1.
 *
 * This backend still materializes shader assets into the generated runtime pack so Minecraft's
 * RenderPipeline shader lookup can resolve the generated shader resource ids. The important
 * difference from minecraft_postchain is frame execution: PostFxHookBridge bypasses
 * ShaderManager#getPostChain and executes the fullscreen pass through nativevpfx instead.
 */
public final class VpfxNativeRuntimeBackend implements VpfxRuntimeBackend {

	public static final String BACKEND_ID = "vpfx_native_direct_v1";
	private static final String BACKEND_DISPLAY = "VPFX Native Direct Backend v1";

	private final boolean forceMode;
	private static boolean firstMaterializeLogged;

	public VpfxNativeRuntimeBackend() {
		this(false);
	}

	public VpfxNativeRuntimeBackend(boolean forceMode) {
		this.forceMode = forceMode;
	}

	@Override
	public String id() {
		return BACKEND_ID;
	}

	@Override
	public String displayName() {
		return forceMode ? BACKEND_DISPLAY + " (force)" : BACKEND_DISPLAY;
	}

	@Override
	public VpfxRuntimeBackendCapabilities capabilities() {
		return VpfxNativeRuntimeCapabilities.V0;
	}

	@Override
	public RuntimeZipPackMaterializationResult materialize(
			ShaderPackContainer container,
			Path outputRoot
	) throws IOException {
		if (!firstMaterializeLogged) {
			firstMaterializeLogged = true;
			VulkanPostFX.LOGGER.info(
					"[{}] VPFX Native Direct Backend v1 active: materializing shader assets for native RenderPipeline lookup. "
							+ "Frame execution will bypass Minecraft PostChain. forceMode={}",
					VulkanPostFX.MOD_ID,
					forceMode
			);
		}

		return ZipPackMaterializer.materialize(container, outputRoot);
	}
}