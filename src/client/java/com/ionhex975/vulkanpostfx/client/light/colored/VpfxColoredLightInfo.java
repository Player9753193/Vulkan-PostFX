package com.ionhex975.vulkanpostfx.client.light.colored;

/**
 * World-space colored light sample collected by VPFX.
 *
 * This is a post-processing data source, not Minecraft block light. It is used
 * as the first CPU-side input for a later colored-light volume atlas and
 * screen-space raymarch pass.
 */
public record VpfxColoredLightInfo(
        int blockX,
        int blockY,
        int blockZ,
        float red,
        float green,
        float blue,
        float intensity,
        float radius,
        String debugName,
        String source
) {
    public static VpfxColoredLightInfo at(
            int blockX,
            int blockY,
            int blockZ,
            float red,
            float green,
            float blue,
            float intensity,
            float radius,
            String debugName,
            String source
    ) {
        return new VpfxColoredLightInfo(blockX, blockY, blockZ, red, green, blue, intensity, radius, debugName, source);
    }

    public VpfxColoredLightInfo {
        red = clamp01(red);
        green = clamp01(green);
        blue = clamp01(blue);
        intensity = clamp(intensity, 0.0F, 8.0F);
        radius = Math.max(0.0F, radius);
        if (debugName == null || debugName.isBlank()) {
            debugName = "unknown";
        }
        if (source == null || source.isBlank()) {
            source = "unknown";
        }
    }

    public boolean enabled() {
        return intensity > 0.001F && radius > 0.001F;
    }

    public float distanceSquaredTo(double x, double y, double z) {
        double dx = blockX + 0.5D - x;
        double dy = blockY + 0.5D - y;
        double dz = blockZ + 0.5D - z;
        return (float) (dx * dx + dy * dy + dz * dz);
    }

    public String shortDebugString() {
        return debugName + "@" + blockX + "/" + blockY + "/" + blockZ
                + " rgb=" + "%.2f/%.2f/%.2f".formatted(red, green, blue)
                + " intensity=" + "%.2f".formatted(intensity)
                + " radius=" + "%.2f".formatted(radius)
                + " source=" + source;
    }

    private static float clamp01(float value) {
        return clamp(value, 0.0F, 1.0F);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
