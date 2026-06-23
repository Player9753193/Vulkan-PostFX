package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx;

import com.ionhex975.vulkanpostfx.client.runtime.vpfx.VpfxRuntimeBackendCapabilities;

public final class VpfxNativeRuntimeCapabilities {

    public static final VpfxRuntimeBackendCapabilities V0 = new VpfxRuntimeBackendCapabilities(
            false,  // usesPostChain
            true,   // nativeRuntime
            false,  // supportsCompute
            true,   // supportsShadowDepth
            true    // supportsCustomTargets
    );

    private VpfxNativeRuntimeCapabilities() {
    }
}
