package com.ionhex975.vulkanpostfx.client.ui.model;

import java.util.List;

public record VpfxUiState(
        String activePackId,
        String activePackName,
        String activePackSource,
        String backendId,
        String backendDisplayName,
        boolean vpfxEnabled,
        boolean shadowDepthDebug,
        boolean nativeDirect,
        boolean postChainRuntime,
        int passCount,
        int targetCount,
        String effectId,
        String failedEffectId,
        String fallbackReason,
        String configMode,
        String runtimeNamespace,
        String runtimeRoot,
        List<VpfxPackListEntry> packs
) {
}
