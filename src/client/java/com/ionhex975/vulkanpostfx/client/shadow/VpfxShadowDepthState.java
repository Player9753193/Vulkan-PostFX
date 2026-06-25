package com.ionhex975.vulkanpostfx.client.shadow;

/**
 * Read-only snapshot of the current VPFX shadow-depth resource state.
 *
 * This intentionally separates:
 * - targetReady: the shadow target exists;
 * - available: the current frame produced a shadow-depth image that can be sampled;
 * - passExecuted / castersRendered: what the shadow pass actually did.
 */
public record VpfxShadowDepthState(
        boolean available,
        boolean targetReady,
        int width,
        int height,
        int frameEpoch,
        VpfxShadowDepthSource source,
        String primaryLight,
        float lightIntensity,
        boolean shadowPassEnabled,
        boolean passExecuted,
        boolean castersRendered,
        String reason
) {
    public static VpfxShadowDepthState unavailable(String reason) {
        return new VpfxShadowDepthState(
                false,
                false,
                0,
                0,
                -1,
                VpfxShadowDepthSource.UNAVAILABLE,
                "none",
                0.0F,
                false,
                false,
                false,
                reason == null || reason.isBlank() ? "shadow depth unavailable" : reason
        );
    }

    public String sizeString() {
        return width > 0 && height > 0 ? width + "x" + height : "none";
    }
}
