package com.ionhex975.vulkanpostfx.client.light;

/**
 * Screen-space held-light descriptor for VPFX post-processing shaders.
 *
 * This is not world lighting. It only describes the strongest light-emitting
 * item currently held by the local player so a post effect can add a cheap
 * screen-space glow.
 */
public record VpfxHeldLightInfo(
        float red,
        float green,
        float blue,
        float intensity,
        float radius,
        String debugName
) {
    public static final VpfxHeldLightInfo NONE = new VpfxHeldLightInfo(
            0.0F,
            0.0F,
            0.0F,
            0.0F,
            0.0F,
            "none"
    );

    public VpfxHeldLightInfo {
        red = clamp01(red);
        green = clamp01(green);
        blue = clamp01(blue);
        intensity = clamp(intensity, 0.0F, 4.0F);
        radius = Math.max(0.0F, radius);
        if (debugName == null || debugName.isBlank()) {
            debugName = "unknown";
        }
    }

    public boolean enabled() {
        return intensity > 0.001F && radius > 0.001F;
    }

    public static VpfxHeldLightInfo of(
            float red,
            float green,
            float blue,
            float intensity,
            float radius,
            String debugName
    ) {
        return new VpfxHeldLightInfo(red, green, blue, intensity, radius, debugName);
    }

    private static float clamp01(float value) {
        return clamp(value, 0.0F, 1.0F);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
