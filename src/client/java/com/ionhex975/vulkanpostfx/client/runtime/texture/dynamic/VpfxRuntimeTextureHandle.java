package com.ionhex975.vulkanpostfx.client.runtime.texture.dynamic;

import net.minecraft.resources.Identifier;

/**
 * Read-only descriptor for a VPFX runtime data texture.
 *
 * A handle does not guarantee that a GPU texture has already been created. It
 * gives the pack/runtime system a stable logical name, expected identifier,
 * size, format, and readiness reason.
 */
public record VpfxRuntimeTextureHandle(
        String logicalName,
        Identifier location,
        int width,
        int height,
        VpfxRuntimeTextureFormat format,
        boolean dynamic,
        boolean ready,
        long frameEpoch,
        String reason
) {
    public VpfxRuntimeTextureHandle {
        if (logicalName == null || logicalName.isBlank()) {
            logicalName = "unknown";
        }
        width = Math.max(0, width);
        height = Math.max(0, height);
        if (format == null) {
            format = VpfxRuntimeTextureFormat.RGBA8;
        }
        if (reason == null || reason.isBlank()) {
            reason = ready ? "ready" : "not ready";
        }
    }

    public static VpfxRuntimeTextureHandle declared(
            String logicalName,
            Identifier location,
            int width,
            int height,
            VpfxRuntimeTextureFormat format,
            boolean dynamic,
            String reason
    ) {
        return new VpfxRuntimeTextureHandle(
                logicalName,
                location,
                width,
                height,
                format,
                dynamic,
                false,
                -1L,
                reason
        );
    }

    public VpfxRuntimeTextureHandle markReady(int newWidth, int newHeight, long newFrameEpoch, String newReason) {
        return new VpfxRuntimeTextureHandle(
                logicalName,
                location,
                newWidth,
                newHeight,
                format,
                dynamic,
                true,
                newFrameEpoch,
                newReason
        );
    }

    public VpfxRuntimeTextureHandle markUnavailable(String newReason) {
        return new VpfxRuntimeTextureHandle(
                logicalName,
                location,
                width,
                height,
                format,
                dynamic,
                false,
                frameEpoch,
                newReason
        );
    }

    public String sizeString() {
        return width + "x" + height;
    }
}
