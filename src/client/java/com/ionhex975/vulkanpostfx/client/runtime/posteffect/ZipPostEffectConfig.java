package com.ionhex975.vulkanpostfx.client.runtime.posteffect;

import java.util.List;
import java.util.Set;

/**
 * ZIP 入口后处理的最小运行时中间表示。
 */
public final class ZipPostEffectConfig {
    private final Set<String> targets;
    private final List<ZipPostEffectPass> passes;

    public ZipPostEffectConfig(Set<String> targets, List<ZipPostEffectPass> passes) {
        this.targets = Set.copyOf(targets);
        this.passes = List.copyOf(passes);
    }

    public Set<String> targets() {
        return targets;
    }

    public List<ZipPostEffectPass> passes() {
        return passes;
    }
}