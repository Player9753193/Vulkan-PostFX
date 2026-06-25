package com.ionhex975.vulkanpostfx.client.light;

import net.minecraft.client.Minecraft;

/**
 * Resolves the local player's strongest held light source once per builtin UBO
 * update. Main hand and off hand are both considered; the stronger light wins.
 */
public final class VpfxHeldLightProvider {
    private VpfxHeldLightProvider() {
    }

    public static VpfxHeldLightInfo currentHeldLight() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return VpfxHeldLightInfo.NONE;
        }

        VpfxHeldLightInfo main = VpfxHeldLightRegistry.resolve(minecraft.player.getMainHandItem());
        VpfxHeldLightInfo off = VpfxHeldLightRegistry.resolve(minecraft.player.getOffhandItem());

        return stronger(main, off);
    }

    private static VpfxHeldLightInfo stronger(VpfxHeldLightInfo a, VpfxHeldLightInfo b) {
        if (a == null || !a.enabled()) {
            return b == null ? VpfxHeldLightInfo.NONE : b;
        }
        if (b == null || !b.enabled()) {
            return a;
        }

        // Use intensity * radius as a cheap perceptual score.
        float scoreA = a.intensity() * Math.max(0.25F, a.radius());
        float scoreB = b.intensity() * Math.max(0.25F, b.radius());
        return scoreB > scoreA ? b : a;
    }
}
