package com.ionhex975.vulkanpostfx.client.ui.model;

import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxValidationMessage;

import java.util.List;

public record VpfxPackListEntry(
        Status status,
        String id,
        String name,
        String source,
        String sourcePath,
        boolean selected,
        boolean valid,
        boolean canActivate,
        boolean nativeCompatible,
        String backendHint,
        int passCount,
        int targetCount,
        String diagnosticSummary,
        List<VpfxValidationMessage> messages
) {
    public enum Status {
        VALID,
        WARNING,
        INVALID
    }

    public VpfxPackListEntry {
        status = status == null ? (valid ? Status.VALID : Status.INVALID) : status;
        id = emptyAs(id, "unknown");
        name = emptyAs(name, "unknown VPFX pack");
        source = emptyAs(source, "unknown");
        sourcePath = emptyAs(sourcePath, "unknown");
        backendHint = emptyAs(backendHint, status == Status.INVALID ? "Invalid" : "Unknown");
        diagnosticSummary = emptyAs(diagnosticSummary, status == Status.INVALID ? "Invalid VPFX pack" : "No diagnostics");
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public boolean invalid() {
        return status == Status.INVALID;
    }

    public boolean warning() {
        return status == Status.WARNING;
    }

    public String statusLabel() {
        return switch (status) {
            case VALID -> "Valid";
            case WARNING -> "Warning";
            case INVALID -> "Invalid";
        };
    }

    public String diagnosticsText() {
        StringBuilder builder = new StringBuilder();
        builder.append("=== VPFX Pack Validation Report ===\n");
        builder.append("Pack: ").append(name).append('\n');
        builder.append("Pack ID: ").append(id).append('\n');
        builder.append("Source: ").append(source).append('\n');
        builder.append("Path: ").append(sourcePath).append('\n');
        builder.append("Status: ").append(statusLabel().toUpperCase()).append('\n');
        builder.append("Can Activate: ").append(canActivate).append('\n');
        builder.append("Backend Hint: ").append(backendHint).append('\n');
        builder.append("Passes / Targets: ").append(passCount).append(" / ").append(targetCount).append('\n');
        builder.append("Summary: ").append(diagnosticSummary).append('\n');
        builder.append("\n--- Messages ---\n");
        if (messages.isEmpty()) {
            builder.append("none\n");
        } else {
            for (VpfxValidationMessage message : messages) {
                builder.append('[')
                        .append(formatSeverity(message.getSeverity()))
                        .append("] ")
                        .append(emptyAs(message.getCode(), "unknown"))
                        .append('\n');
                builder.append(emptyAs(message.getPath(), "unknown path"))
                        .append(" - ")
                        .append(emptyAs(message.getMessage(), "no message"))
                        .append("\n\n");
            }
        }
        return builder.toString();
    }

    private static String formatSeverity(VpfxValidationMessage.Severity severity) {
        if (severity == null) {
            return "INFO";
        }
        return switch (severity) {
            case FATAL -> "ERROR";
            case WARNING -> "WARNING";
        };
    }

    private static String emptyAs(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
