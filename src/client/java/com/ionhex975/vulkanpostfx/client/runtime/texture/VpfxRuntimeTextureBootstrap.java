package com.ionhex975.vulkanpostfx.client.runtime.texture;

import com.ionhex975.vulkanpostfx.VulkanPostFX;

import java.io.IOException;
import java.nio.file.Path;

public final class VpfxRuntimeTextureBootstrap {
    private VpfxRuntimeTextureBootstrap() {
    }

    public static void registerRuntimeTextureManifest(Path manifestPath) {
        if (manifestPath == null) {
            return;
        }

        try {
            VpfxRuntimeTextureManifest manifest = VpfxRuntimeTextureManifestReader.read(manifestPath);
            VpfxRuntimeTextureRegistry.register(manifest);

            VulkanPostFX.LOGGER.info(
                    "[{}] Registered runtime texture manifest: namespace={}, textureCount={}",
                    VulkanPostFX.MOD_ID,
                    manifest.getRuntimeNamespace(),
                    manifest.getTextures().size()
            );
        } catch (IOException e) {
            VulkanPostFX.LOGGER.error(
                    "[{}] Failed to register runtime texture manifest from {}: {}",
                    VulkanPostFX.MOD_ID,
                    manifestPath,
                    e.getMessage()
            );
        }
    }
}