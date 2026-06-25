package com.ionhex975.vulkanpostfx.client.diagnostics.doctor;

import net.minecraft.network.chat.Component;

public final class VpfxDoctorFormatter {
    private VpfxDoctorFormatter() {
    }

    public static Component toChatSummary(VpfxDoctorReport report) {
        if (report == null) {
            return Component.literal("[VPFX Doctor] ERROR\nNo report was generated.");
        }

        String activePack = fallback(report.value("active_pack"), "none");
        String backend = fallback(report.value("active_backend_id"), "unknown");
        String nativeFallback = fallback(report.value("native_fallback"), "none");
        String runtimeNamespace = fallback(report.value("runtime_namespace"), "none");
        String runtimeReload = fallback(report.value("runtime_resource_reload"), "unknown");
        String invalidPacks = fallback(report.value("discovered_invalid_packs"), "0");
        String heldLight = fallback(report.value("held_light"), "none");
        String sceneDepth = fallback(report.value("scene_depth_available"), "unknown");
        String shadowDepth = fallback(report.value("shadow_depth_available"), "unknown");
        String coloredLights = fallback(report.value("colored_lights_count"), "unknown");
        String coloredVolume = fallback(report.value("colored_light_volume_atlas_ready"), "unknown");
        String runtimeTextures = fallback(report.value("runtime_texture_count"), "unknown");

        return Component.literal(
                "[VPFX Doctor] " + report.overallSeverity() + "\n"
                        + "Active pack: " + activePack + "\n"
                        + "Backend: " + backend + "\n"
                        + "Native fallback: " + nativeFallback + "\n"
                        + "Runtime namespace: " + runtimeNamespace + "\n"
                        + "Runtime pack loaded: " + runtimeReload + "\n"
                        + "Invalid packs: " + invalidPacks + "\n"
                        + "Held light: " + heldLight + "\n"
                        + "Scene depth: " + sceneDepth + "\n"
                        + "Shadow depth: " + shadowDepth + "\n"
                        + "Colored lights: " + coloredLights + "\n"
                        + "Runtime textures: " + runtimeTextures + "\n"
                        + "Use /vpfx doctor copy for the full report."
        );
    }

    public static String toPlainText(VpfxDoctorReport report) {
        if (report == null) {
            return "=== VPFX Doctor Report ===\nERROR: report is null\n";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("=== VPFX Doctor Report ===\n");
        builder.append("Created: ").append(report.createdAt()).append('\n');
        builder.append("Overall: ").append(report.overallSeverity()).append('\n');
        builder.append("Errors: ").append(report.countBySeverity(VpfxDoctorSeverity.ERROR)).append('\n');
        builder.append("Warnings: ").append(report.countBySeverity(VpfxDoctorSeverity.WARN)).append('\n');
        builder.append('\n');

        for (VpfxDoctorSection section : report.sections()) {
            builder.append("--- ").append(section.title()).append(" ---\n");
            if (section.checks().isEmpty()) {
                builder.append("[INFO] empty\n\n");
                continue;
            }
            for (VpfxDoctorCheck check : section.checks()) {
                builder.append('[')
                        .append(check.severity())
                        .append("] ")
                        .append(check.code())
                        .append(" | ")
                        .append(check.label())
                        .append(": ")
                        .append(check.value())
                        .append('\n');
                if (!check.detail().isBlank()) {
                    builder.append("      ").append(check.detail()).append('\n');
                }
            }
            builder.append('\n');
        }

        return builder.toString();
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
