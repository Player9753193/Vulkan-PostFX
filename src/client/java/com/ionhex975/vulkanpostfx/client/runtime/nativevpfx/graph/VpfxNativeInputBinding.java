package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.graph;

import java.util.Objects;

public final class VpfxNativeInputBinding {
    private final String samplerName;
    private final String targetId;
    private final String textureName;
    private final boolean textureInput;
    private final boolean depthInput;

    public VpfxNativeInputBinding(String samplerName, String targetId) {
        this(samplerName, targetId, "", false, false);
    }

    public static VpfxNativeInputBinding target(String samplerName, String targetId, boolean depthInput) {
        return new VpfxNativeInputBinding(samplerName, targetId, "", false, depthInput);
    }

    private VpfxNativeInputBinding(
            String samplerName,
            String targetId,
            String textureName,
            boolean textureInput,
            boolean depthInput
    ) {
        this.samplerName = samplerName != null && !samplerName.isBlank() ? samplerName : "In";
        this.targetId = targetId != null ? targetId : "";
        this.textureName = textureName != null ? textureName : "";
        this.textureInput = textureInput;
        this.depthInput = !textureInput && depthInput;
    }

    public static VpfxNativeInputBinding texture(String samplerName, String textureName) {
        return new VpfxNativeInputBinding(samplerName, "", textureName, true, false);
    }

    public String samplerName() {
        return samplerName;
    }

    public String targetId() {
        return targetId;
    }

    public String textureName() {
        return textureName;
    }

    public boolean isTextureInput() {
        return textureInput;
    }

    public boolean isTargetInput() {
        return !textureInput;
    }

    public boolean isDepthInput() {
        return depthInput;
    }

    public String glslSamplerName() {
        return samplerName + "Sampler";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VpfxNativeInputBinding that)) return false;
        return textureInput == that.textureInput
                && depthInput == that.depthInput
                && samplerName.equals(that.samplerName)
                && targetId.equals(that.targetId)
                && textureName.equals(that.textureName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(samplerName, targetId, textureName, textureInput, depthInput);
    }

    @Override
    public String toString() {
        if (textureInput) {
            return "InputBinding{" + samplerName + " → texture:" + textureName + '}';
        }
        return "InputBinding{" + samplerName + " → " + targetId + (depthInput ? " depth" : "") + '}';
    }
}
