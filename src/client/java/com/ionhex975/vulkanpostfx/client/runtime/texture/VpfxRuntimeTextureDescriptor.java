package com.ionhex975.vulkanpostfx.client.runtime.texture;

import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxTextureFilter;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxTextureWrap;

public final class VpfxRuntimeTextureDescriptor {
    private final String logicalName;
    private final String sourceZipPath;
    private final String effectPath;
    private final String locationId;
    private final int width;
    private final int height;
    private final boolean bilinear;
    private final VpfxTextureFilter filter;
    private final VpfxTextureWrap wrap;

    public VpfxRuntimeTextureDescriptor(
            String logicalName,
            String sourceZipPath,
            String effectPath,
            String locationId,
            int width,
            int height,
            boolean bilinear,
            VpfxTextureFilter filter,
            VpfxTextureWrap wrap
    ) {
        this.logicalName = logicalName;
        this.sourceZipPath = sourceZipPath;
        this.effectPath = effectPath;
        this.locationId = locationId;
        this.width = width;
        this.height = height;
        this.bilinear = bilinear;
        this.filter = filter;
        this.wrap = wrap;
    }

    public String getLogicalName() {
        return logicalName;
    }

    public String getSourceZipPath() {
        return sourceZipPath;
    }

    public String getEffectPath() {
        return effectPath;
    }

    public String getLocationId() {
        return locationId;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isBilinear() {
        return bilinear;
    }

    public VpfxTextureFilter getFilter() {
        return filter;
    }

    public VpfxTextureWrap getWrap() {
        return wrap;
    }
}