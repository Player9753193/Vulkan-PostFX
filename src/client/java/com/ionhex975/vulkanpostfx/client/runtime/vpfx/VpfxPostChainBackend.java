package com.ionhex975.vulkanpostfx.client.runtime.vpfx;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.pack.ShaderPackContainer;
import com.ionhex975.vulkanpostfx.client.runtime.zip.RuntimeZipPackMaterializationResult;
import com.ionhex975.vulkanpostfx.client.runtime.zip.ZipPackMaterializer;

import java.io.IOException;
import java.nio.file.Path;

public final class VpfxPostChainBackend implements VpfxRuntimeBackend {

	private static final String BACKEND_ID = "minecraft_postchain";
	private static final String BACKEND_DISPLAY = "Minecraft PostChain Backend";

	private static final VpfxRuntimeBackendCapabilities CAPABILITIES =
			new VpfxRuntimeBackendCapabilities(
					true,   // usesPostChain
					false,  // nativeRuntime
					false,  // supportsCompute
					true,   // supportsShadowDepth (experimental)
					true    // supportsCustomTargets
			);

	private static boolean firstMaterializeLogged;

	private static final String[] PROVIDED_EXTERNAL_TARGETS = {
			"minecraft:main",
			"minecraft:scene_color",
			"vulkanpostfx:scene_depth",
			"vulkanpostfx:shadow_depth"
	};

	@Override
	public String id() {
		return BACKEND_ID;
	}

	@Override
	public String displayName() {
		return BACKEND_DISPLAY;
	}

	@Override
	public VpfxRuntimeBackendCapabilities capabilities() {
		return CAPABILITIES;
	}

	@Override
	public RuntimeZipPackMaterializationResult materialize(
			ShaderPackContainer container,
			Path outputRoot
	) throws IOException {
		if (!firstMaterializeLogged) {
			firstMaterializeLogged = true;
			VulkanPostFX.LOGGER.info(
					"[{}] VPFX runtime backend: id={}, display={}, {}, providedExternalTargets={}",
					VulkanPostFX.MOD_ID,
					BACKEND_ID,
					BACKEND_DISPLAY,
					CAPABILITIES,
					java.util.Arrays.toString(PROVIDED_EXTERNAL_TARGETS)
			);
		}

		return ZipPackMaterializer.materialize(container, outputRoot);
	}
}
