package com.ionhex975.vulkanpostfx.client.light.colored;

import java.util.List;

public record VpfxColoredLightSnapshot(
        boolean enabled,
        int scanRadius,
        int maxLights,
        int originX,
        int originY,
        int originZ,
        long frameEpoch,
        long lastScanNanos,
        int rawLightCount,
        boolean clippedByLimit,
        List<VpfxColoredLightInfo> lights,
        String reason
) {
    public static VpfxColoredLightSnapshot unavailable(String reason) {
        return new VpfxColoredLightSnapshot(
                false,
                VpfxColoredLightCollector.DEFAULT_SCAN_RADIUS,
                VpfxColoredLightCollector.DEFAULT_MAX_LIGHTS,
                0,
                0,
                0,
                -1L,
                0L,
                0,
                false,
                List.of(),
                reason == null || reason.isBlank() ? "unavailable" : reason
        );
    }

    public VpfxColoredLightSnapshot {
        lights = List.copyOf(lights == null ? List.of() : lights);
        if (reason == null || reason.isBlank()) {
            reason = enabled ? "ok" : "unavailable";
        }
    }

    public int lightCount() {
        return lights.size();
    }

    public String originString() {
        return originX + "/" + originY + "/" + originZ;
    }

    public String summary() {
        return "enabled=" + enabled
                + ", lights=" + lightCount()
                + ", raw=" + rawLightCount
                + ", radius=" + scanRadius
                + ", max=" + maxLights
                + ", clipped=" + clippedByLimit
                + ", origin=" + originString()
                + ", reason=" + reason;
    }
}
