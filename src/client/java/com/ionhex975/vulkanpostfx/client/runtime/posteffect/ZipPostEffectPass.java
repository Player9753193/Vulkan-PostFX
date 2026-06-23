package com.ionhex975.vulkanpostfx.client.runtime.posteffect;

import java.util.List;

/**
 * ZIP 入口后处理中的单个 pass 定义。
 */
public final class ZipPostEffectPass {
    private final String vertexShader;
    private final String fragmentShader;
    private final List<ZipPostEffectInput> inputs;
    private final String output;

    public ZipPostEffectPass(
            String vertexShader,
            String fragmentShader,
            List<ZipPostEffectInput> inputs,
            String output
    ) {
        this.vertexShader = vertexShader;
        this.fragmentShader = fragmentShader;
        this.inputs = List.copyOf(inputs);
        this.output = output;
    }

    public String vertexShader() {
        return vertexShader;
    }

    public String fragmentShader() {
        return fragmentShader;
    }

    public List<ZipPostEffectInput> inputs() {
        return inputs;
    }

    public String output() {
        return output;
    }
}