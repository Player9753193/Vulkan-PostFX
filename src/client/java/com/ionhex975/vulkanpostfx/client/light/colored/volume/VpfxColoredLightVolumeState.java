package com.ionhex975.vulkanpostfx.client.light.colored.volume;

import net.minecraft.resources.Identifier;

/**
 * CPU-built colored-light volume atlas status.
 *
 * This is the second bottom-layer step for VPFX colored-light raymarching:
 * collector -> low-resolution 3D volume -> 2D atlas texture. The state is
 * intentionally diagnostic-first so /vpfx doctor can tell whether the atlas is
 * actually uploaded before any raymarch shader is enabled.
 */
public record VpfxColoredLightVolumeState(
        boolean enabled,
        boolean atlasReady,
        int atlasWidth,
        int atlasHeight,
        int volumeSizeX,
        int volumeSizeY,
        int volumeSizeZ,
        int tilesPerRow,
        float voxelWorldSize,
        double originX,
        double originY,
        double originZ,
        long frameEpoch,
        long lastBuildNanos,
        int sourceLightCount,
        int contributingLightCount,
        float maxRed,
        float maxGreen,
        float maxBlue,
        float maxAlpha,
        Identifier textureId,
        String reason
) {
    public static VpfxColoredLightVolumeState unavailable(String reason) {
        return new VpfxColoredLightVolumeState(
                false,
                false,
                VpfxColoredLightVolumeAtlas.ATLAS_WIDTH,
                VpfxColoredLightVolumeAtlas.ATLAS_HEIGHT,
                VpfxColoredLightVolumeAtlas.VOLUME_SIZE_X,
                VpfxColoredLightVolumeAtlas.VOLUME_SIZE_Y,
                VpfxColoredLightVolumeAtlas.VOLUME_SIZE_Z,
                VpfxColoredLightVolumeAtlas.TILES_PER_ROW,
                VpfxColoredLightVolumeAtlas.VOXEL_WORLD_SIZE,
                0.0D,
                0.0D,
                0.0D,
                -1L,
                0L,
                0,
                0,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                VpfxColoredLightVolumeAtlas.TEXTURE_ID,
                reason == null || reason.isBlank() ? "unavailable" : reason
        );
    }

    public VpfxColoredLightVolumeState {
        if (reason == null || reason.isBlank()) {
            reason = atlasReady ? "ok" : "not ready";
        }
        if (textureId == null) {
            textureId = VpfxColoredLightVolumeAtlas.TEXTURE_ID;
        }
    }

    public String atlasSizeString() {
        return atlasWidth + "x" + atlasHeight;
    }

    public String volumeSizeString() {
        return volumeSizeX + "x" + volumeSizeY + "x" + volumeSizeZ;
    }

    public String originString() {
        return "%.2f/%.2f/%.2f".formatted(originX, originY, originZ);
    }

    public String maxRgbString() {
        return "%.3f/%.3f/%.3f a=%.3f".formatted(maxRed, maxGreen, maxBlue, maxAlpha);
    }

    public String summary() {
        return "enabled=" + enabled
                + ", ready=" + atlasReady
                + ", atlas=" + atlasSizeString()
                + ", volume=" + volumeSizeString()
                + ", lights=" + contributingLightCount + "/" + sourceLightCount
                + ", frame=" + frameEpoch
                + ", origin=" + originString()
                + ", reason=" + reason;
    }
}
