package com.ionhex975.vulkanpostfx.client.diagnostics.doctor;

import java.util.List;

public record VpfxDoctorSection(
        String title,
        List<VpfxDoctorCheck> checks
) {
    public VpfxDoctorSection {
        title = title == null || title.isBlank() ? "Untitled" : title;
        checks = checks == null ? List.of() : List.copyOf(checks);
    }
}
