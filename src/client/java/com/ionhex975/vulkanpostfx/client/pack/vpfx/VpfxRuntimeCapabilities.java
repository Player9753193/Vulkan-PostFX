package com.ionhex975.vulkanpostfx.client.pack.vpfx;

public final class VpfxRuntimeCapabilities {
    private final boolean sceneColor;
    private final boolean sceneDepth;
    private final boolean shadowDepth;
    private final boolean customTargets;
    private final boolean compute;

    public VpfxRuntimeCapabilities(
            boolean sceneColor,
            boolean sceneDepth,
            boolean shadowDepth,
            boolean customTargets,
            boolean compute
    ) {
        this.sceneColor = sceneColor;
        this.sceneDepth = sceneDepth;
        this.shadowDepth = shadowDepth;
        this.customTargets = customTargets;
        this.compute = compute;
    }

    public boolean isSceneColor() {
        return sceneColor;
    }

    public boolean isSceneDepth() {
        return sceneDepth;
    }

    public boolean isShadowDepth() {
        return shadowDepth;
    }

    public boolean isCustomTargets() {
        return customTargets;
    }

    public boolean isCompute() {
        return compute;
    }

    @Override
    public String toString() {
        return "VpfxRuntimeCapabilities{" +
                "sceneColor=" + sceneColor +
                ", sceneDepth=" + sceneDepth +
                ", shadowDepth=" + shadowDepth +
                ", customTargets=" + customTargets +
                ", compute=" + compute +
                '}';
    }
}