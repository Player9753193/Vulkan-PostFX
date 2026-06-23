package com.ionhex975.vulkanpostfx.client.pack.vpfx;

import java.nio.file.Path;
import java.util.List;

public final class VpfxNativePackDefinition {
    private final Path zipPath;
    private final VpfxPackManifest manifest;
    private final VpfxGraphDefinition graph;
    private final List<VpfxValidationMessage> validationMessages;

    public VpfxNativePackDefinition(
            Path zipPath,
            VpfxPackManifest manifest,
            VpfxGraphDefinition graph,
            List<VpfxValidationMessage> validationMessages
    ) {
        this.zipPath = zipPath;
        this.manifest = manifest;
        this.graph = graph;
        this.validationMessages = List.copyOf(validationMessages);
    }

    public Path getZipPath() {
        return zipPath;
    }

    public VpfxPackManifest getManifest() {
        return manifest;
    }

    public VpfxGraphDefinition getGraph() {
        return graph;
    }

    public List<VpfxValidationMessage> getValidationMessages() {
        return validationMessages;
    }
}