package com.ionhex975.vulkanpostfx.client.pack.vpfx;

import java.util.List;
import java.util.Map;

public final class VpfxGraphDefinition {
    private final Map<String, VpfxTargetDefinition> targets;
    private final List<VpfxPassDefinition> passes;

    public VpfxGraphDefinition(Map<String, VpfxTargetDefinition> targets, List<VpfxPassDefinition> passes) {
        this.targets = Map.copyOf(targets);
        this.passes = List.copyOf(passes);
    }

    public Map<String, VpfxTargetDefinition> getTargets() {
        return targets;
    }

    public List<VpfxPassDefinition> getPasses() {
        return passes;
    }
}