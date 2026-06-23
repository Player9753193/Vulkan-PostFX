package com.ionhex975.vulkanpostfx.client.pack.vpfx;

import java.util.List;

public final class VpfxPassDefinition {
    private final String id;
    private final String debugLabel;
    private final String vertexShader;
    private final String fragmentShader;
    private final List<VpfxPassInput> inputs;
    private final String output;

    public VpfxPassDefinition(
            String id,
            String debugLabel,
            String vertexShader,
            String fragmentShader,
            List<VpfxPassInput> inputs,
            String output
    ) {
        this.id = (id != null && !id.isBlank()) ? id : "";
        this.debugLabel = (debugLabel != null && !debugLabel.isBlank()) ? debugLabel : "";
        this.vertexShader = vertexShader;
        this.fragmentShader = fragmentShader;
        this.inputs = List.copyOf(inputs);
        this.output = output;
    }

    public String getId() {
        return id;
    }

    public boolean hasId() {
        return !id.isBlank();
    }

    public String getDebugLabel() {
        return debugLabel;
    }

    public boolean hasDebugLabel() {
        return !debugLabel.isBlank();
    }

    public String identity() {
        if (!id.isBlank()) {
            return id;
        }
        if (!debugLabel.isBlank()) {
            return debugLabel;
        }
        return "(anonymous)";
    }

    public String identityOrIndex(int fallbackIndex) {
        if (!id.isBlank()) {
            return "pass[" + id + "]";
        }
        if (!debugLabel.isBlank()) {
            return "pass[" + debugLabel + "]";
        }
        return "passes[" + fallbackIndex + "]";
    }

    public String getVertexShader() {
        return vertexShader;
    }

    public String getFragmentShader() {
        return fragmentShader;
    }

    public List<VpfxPassInput> getInputs() {
        return inputs;
    }

    public String getOutput() {
        return output;
    }
}