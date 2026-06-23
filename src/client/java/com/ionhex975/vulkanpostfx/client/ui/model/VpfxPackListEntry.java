package com.ionhex975.vulkanpostfx.client.ui.model;

public record VpfxPackListEntry(
        String id,
        String name,
        String source,
        boolean selected,
        boolean valid,
        boolean nativeCompatible,
        String backendHint,
        int passCount,
        int targetCount,
        String diagnosticSummary
) {
}
