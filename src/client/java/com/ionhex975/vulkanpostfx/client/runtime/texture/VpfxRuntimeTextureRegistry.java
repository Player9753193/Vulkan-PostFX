package com.ionhex975.vulkanpostfx.client.runtime.texture;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VpfxRuntimeTextureRegistry {
    private static final Map<String, VpfxRuntimeTextureManifest> MANIFESTS = new ConcurrentHashMap<>();

    private VpfxRuntimeTextureRegistry() {
    }

    public static void register(VpfxRuntimeTextureManifest manifest) {
        if (manifest == null) {
            return;
        }
        MANIFESTS.put(manifest.getRuntimeNamespace(), manifest);
    }

    public static void clear() {
        MANIFESTS.clear();
    }

    public static VpfxRuntimeTextureManifest getManifest(String runtimeNamespace) {
        return MANIFESTS.get(runtimeNamespace);
    }

    public static VpfxRuntimeTextureDescriptor getTexture(String runtimeNamespace, String logicalName) {
        VpfxRuntimeTextureManifest manifest = MANIFESTS.get(runtimeNamespace);
        if (manifest == null) {
            return null;
        }
        return manifest.getTexture(logicalName);
    }
}