package com.ionhex975.vulkanpostfx.client.runtime.texture;

public final class VpfxTextureBindingReference {
    private final String logicalName;
    private final String samplerName;

    public VpfxTextureBindingReference(String logicalName, String samplerName) {
        this.logicalName = logicalName;
        this.samplerName = samplerName;
    }

    public String getLogicalName() {
        return logicalName;
    }

    public String getSamplerName() {
        return samplerName;
    }
}