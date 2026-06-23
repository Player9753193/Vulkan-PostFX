package com.ionhex975.vulkanpostfx.client.pack.vpfx;

public final class VpfxPassInput {
    private final String samplerName;
    private final String target;
    private final String texture;
    private final boolean useDepthBuffer;

    public VpfxPassInput(
            String samplerName,
            String target,
            String texture,
            boolean useDepthBuffer
    ) {
        this.samplerName = samplerName;
        this.target = target;
        this.texture = texture;
        this.useDepthBuffer = useDepthBuffer;
    }

    public String getSamplerName() {
        return samplerName;
    }

    public String getTarget() {
        return target;
    }

    public String getTexture() {
        return texture;
    }

    public boolean isUseDepthBuffer() {
        return useDepthBuffer;
    }

    public boolean isTargetInput() {
        return target != null && !target.isBlank();
    }

    public boolean isTextureInput() {
        return texture != null && !texture.isBlank();
    }
}