package com.ionhex975.vulkanpostfx.client.pack.vpfx;

import java.util.Arrays;
import java.util.Optional;

public final class VpfxTargetDefinition {
    private final String id;
    private final Double scale;
    private final boolean useDepth;
    private final float[] clearColor;
    private final boolean persistent;
    private final boolean history;
    private final boolean pingPong;

    public VpfxTargetDefinition(String id, Double scale, boolean useDepth, float[] clearColor) {
        this(id, scale, useDepth, clearColor, false, false, false);
    }

    public VpfxTargetDefinition(
            String id,
            Double scale,
            boolean useDepth,
            float[] clearColor,
            boolean persistent,
            boolean history,
            boolean pingPong
    ) {
        this.id = id;
        this.scale = scale;
        this.useDepth = useDepth;
        this.clearColor = clearColor == null ? null : Arrays.copyOf(clearColor, clearColor.length);
        this.persistent = persistent;
        this.history = history;
        this.pingPong = pingPong;
    }

    public String getId() {
        return id;
    }

    public Optional<Double> getScale() {
        return Optional.ofNullable(scale);
    }

    public boolean isUseDepth() {
        return useDepth;
    }

    public Optional<float[]> getClearColor() {
        return clearColor == null
                ? Optional.empty()
                : Optional.of(Arrays.copyOf(clearColor, clearColor.length));
    }

    /**
     * Persistent targets survive across frames in the native framegraph.
     *
     * history=true and ping_pong=true imply persistence because they need previous-frame storage.
     */
    public boolean isPersistent() {
        return persistent || history || pingPong;
    }

    /**
     * Enables the history:<target_id> input alias.
     */
    public boolean isHistory() {
        return history || pingPong;
    }

    /**
     * Uses two persistent buffers so history:<target_id> samples the previous frame while
     * <target_id> can be written this frame without read/write feedback hazards.
     */
    public boolean isPingPong() {
        return pingPong;
    }
}
