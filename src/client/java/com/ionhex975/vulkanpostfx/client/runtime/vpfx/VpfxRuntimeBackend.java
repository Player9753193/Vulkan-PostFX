package com.ionhex975.vulkanpostfx.client.runtime.vpfx;

import com.ionhex975.vulkanpostfx.client.pack.ShaderPackContainer;
import com.ionhex975.vulkanpostfx.client.runtime.zip.RuntimeZipPackMaterializationResult;

import java.io.IOException;
import java.nio.file.Path;

public interface VpfxRuntimeBackend {

	String id();

	String displayName();

	VpfxRuntimeBackendCapabilities capabilities();

	RuntimeZipPackMaterializationResult materialize(
			ShaderPackContainer container,
			Path outputRoot
	) throws IOException;
}
