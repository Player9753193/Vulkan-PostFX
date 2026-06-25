package com.ionhex975.vulkanpostfx.client.diagnostics.exporter;

import java.nio.file.Path;
import java.util.List;

public record VpfxDiagnosticExportResult(
        Path zipPath,
        int filesWritten,
        List<String> skippedFiles
) {
    public VpfxDiagnosticExportResult {
        skippedFiles = skippedFiles == null ? List.of() : List.copyOf(skippedFiles);
    }
}
