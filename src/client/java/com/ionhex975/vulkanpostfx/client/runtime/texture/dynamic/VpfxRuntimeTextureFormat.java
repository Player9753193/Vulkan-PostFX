package com.ionhex975.vulkanpostfx.client.runtime.texture.dynamic;

/**
 * Logical runtime texture formats used by VPFX-generated data textures.
 *
 * This layer intentionally describes the data contract only. The first
 * implementation path can map these descriptors to Minecraft dynamic textures,
 * while a later native path can map the same descriptors to native GPU images.
 */
public enum VpfxRuntimeTextureFormat {
    RGBA8("rgba8", 4, false),
    RGBA16F("rgba16f", 8, true),
    R8("r8", 1, false),
    R32F("r32f", 4, true);

    private final String id;
    private final int bytesPerPixel;
    private final boolean highDynamicRange;

    VpfxRuntimeTextureFormat(String id, int bytesPerPixel, boolean highDynamicRange) {
        this.id = id;
        this.bytesPerPixel = bytesPerPixel;
        this.highDynamicRange = highDynamicRange;
    }

    public String id() {
        return id;
    }

    public int bytesPerPixel() {
        return bytesPerPixel;
    }

    public boolean highDynamicRange() {
        return highDynamicRange;
    }
}
