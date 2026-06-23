package com.ionhex975.vulkanpostfx.client.runtime.posteffect;

/**
 * ZIP 入口后处理中的单个输入定义。
 *
 * 当前支持两类输入：
 * 1. target input
 *    - sampler_name + target
 * 2. texture input
 *    - sampler_name + texture
 */
public final class ZipPostEffectInput {
    private final String samplerName;
    private final String target;
    private final String texture;
    private final boolean useDepthBuffer;

    public ZipPostEffectInput(
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

    public String samplerName() {
        return samplerName;
    }

    public String target() {
        return target;
    }

    public String texture() {
        return texture;
    }

    public boolean useDepthBuffer() {
        return useDepthBuffer;
    }

    public boolean isTargetInput() {
        return target != null && !target.isBlank();
    }

    public boolean isTextureInput() {
        return texture != null && !texture.isBlank();
    }
}