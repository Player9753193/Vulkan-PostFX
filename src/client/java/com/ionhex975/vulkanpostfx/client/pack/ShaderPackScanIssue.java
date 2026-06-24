package com.ionhex975.vulkanpostfx.client.pack;

import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxValidationMessage;

import java.nio.file.Path;
import java.util.List;

/**
 * A discovery-time issue for a shader pack candidate.
 *
 * Runtime pack activation must only receive valid ShaderPackContainer instances.
 * The UI can still show these issues so broken ZIP packs do not silently vanish
 * from the VPFX pack list.
 */
public record ShaderPackScanIssue(
        String sourceId,
        Path sourcePath,
        String displayName,
        String packId,
        String version,
        List<VpfxValidationMessage> messages
) {
    public ShaderPackScanIssue {
        displayName = displayName == null || displayName.isBlank() ? fallbackName(sourcePath) : displayName;
        packId = packId == null || packId.isBlank() ? "unknown" : packId;
        version = version == null || version.isBlank() ? "unknown" : version;
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public static ShaderPackScanIssue fatal(
            String sourceId,
            Path sourcePath,
            String displayName,
            String code,
            String path,
            String message
    ) {
        return new ShaderPackScanIssue(
                sourceId,
                sourcePath,
                displayName,
                "unknown",
                "unknown",
                List.of(new VpfxValidationMessage(
                        VpfxValidationMessage.Severity.FATAL,
                        code,
                        path,
                        message
                ))
        );
    }

    private static String fallbackName(Path sourcePath) {
        if (sourcePath == null) {
            return "unknown VPFX pack";
        }
        Path fileName = sourcePath.getFileName();
        return fileName == null ? sourcePath.toString() : fileName.toString();
    }
}
