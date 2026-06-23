package com.ionhex975.vulkanpostfx.client.runtime.texture;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class VpfxRuntimeTextureManifest {
    private final String runtimeNamespace;
    private final Map<String, VpfxRuntimeTextureDescriptor> textures;

    public VpfxRuntimeTextureManifest(
            String runtimeNamespace,
            Map<String, VpfxRuntimeTextureDescriptor> textures
    ) {
        this.runtimeNamespace = runtimeNamespace;
        this.textures = Collections.unmodifiableMap(new LinkedHashMap<>(textures));
    }

    public String getRuntimeNamespace() {
        return runtimeNamespace;
    }

    public Map<String, VpfxRuntimeTextureDescriptor> getTextures() {
        return textures;
    }

    public boolean hasTexture(String logicalName) {
        return textures.containsKey(logicalName);
    }

    public VpfxRuntimeTextureDescriptor getTexture(String logicalName) {
        return textures.get(logicalName);
    }
}