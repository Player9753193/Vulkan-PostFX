package com.ionhex975.vulkanpostfx.client.depth;

public record VpfxSceneDepthState(
        boolean available,
        boolean targetReady,
        int width,
        int height,
        int frameEpoch,
        VpfxSceneDepthSource source,
        String reason
) {
    public static VpfxSceneDepthState unavailable(String reason) {
        return new VpfxSceneDepthState(
                false,
                false,
                0,
                0,
                -1,
                VpfxSceneDepthSource.UNAVAILABLE,
                reason == null || reason.isBlank() ? "scene depth unavailable" : reason
        );
    }

    public String sizeString() {
        return width > 0 && height > 0 ? width + "x" + height : "none";
    }
}
