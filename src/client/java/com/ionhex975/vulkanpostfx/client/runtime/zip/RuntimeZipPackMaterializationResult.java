package com.ionhex975.vulkanpostfx.client.runtime.zip;

import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxTargetDefinition;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;
import java.util.Map;

public final class RuntimeZipPackMaterializationResult {
    private final String packId;
    private final String runtimeNamespace;
    private final Path runtimeRoot;
    private final Identifier externalPostEffectId;
    private final Path runtimeTextureManifestPath;
    private final Map<String, VpfxTargetDefinition> targetDefinitions;

    public RuntimeZipPackMaterializationResult(
            String packId,
            String runtimeNamespace,
            Path runtimeRoot,
            Identifier externalPostEffectId,
            Path runtimeTextureManifestPath
    ) {
        this(packId, runtimeNamespace, runtimeRoot, externalPostEffectId, runtimeTextureManifestPath, Map.of());
    }

    public RuntimeZipPackMaterializationResult(
            String packId,
            String runtimeNamespace,
            Path runtimeRoot,
            Identifier externalPostEffectId,
            Path runtimeTextureManifestPath,
            Map<String, VpfxTargetDefinition> targetDefinitions
    ) {
        this.packId = packId;
        this.runtimeNamespace = runtimeNamespace;
        this.runtimeRoot = runtimeRoot;
        this.externalPostEffectId = externalPostEffectId;
        this.runtimeTextureManifestPath = runtimeTextureManifestPath;
        this.targetDefinitions = Map.copyOf(targetDefinitions);
    }

    public String getPackId() {
        return packId;
    }

    public String getRuntimeNamespace() {
        return runtimeNamespace;
    }

    public Path getRuntimeRoot() {
        return runtimeRoot;
    }

    public Identifier getExternalPostEffectId() {
        return externalPostEffectId;
    }

    public Path getRuntimeTextureManifestPath() {
        return runtimeTextureManifestPath;
    }

    public Map<String, VpfxTargetDefinition> getTargetDefinitions() {
        return targetDefinitions;
    }

    public String packId() {
        return packId;
    }

    public String runtimeNamespace() {
        return runtimeNamespace;
    }

    public Path runtimeRoot() {
        return runtimeRoot;
    }

    public Identifier externalPostEffectId() {
        return externalPostEffectId;
    }

    public Path runtimeTextureManifestPath() {
        return runtimeTextureManifestPath;
    }

    public Map<String, VpfxTargetDefinition> targetDefinitions() {
        return targetDefinitions;
    }
}