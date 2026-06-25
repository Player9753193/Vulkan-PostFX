package com.ionhex975.vulkanpostfx.client.diagnostics.doctor;

import java.time.Instant;
import java.util.List;

public final class VpfxDoctorReport {
    private final Instant createdAt;
    private final VpfxDoctorSeverity overallSeverity;
    private final List<VpfxDoctorSection> sections;

    public VpfxDoctorReport(Instant createdAt, List<VpfxDoctorSection> sections) {
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
        this.sections = sections == null ? List.of() : List.copyOf(sections);
        this.overallSeverity = computeOverallSeverity(this.sections);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public VpfxDoctorSeverity overallSeverity() {
        return overallSeverity;
    }

    public List<VpfxDoctorSection> sections() {
        return sections;
    }

    public String value(String code) {
        VpfxDoctorCheck check = check(code);
        return check == null ? "" : check.value();
    }

    public String detail(String code) {
        VpfxDoctorCheck check = check(code);
        return check == null ? "" : check.detail();
    }

    public VpfxDoctorCheck check(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (VpfxDoctorSection section : sections) {
            for (VpfxDoctorCheck check : section.checks()) {
                if (code.equals(check.code())) {
                    return check;
                }
            }
        }
        return null;
    }

    public int countBySeverity(VpfxDoctorSeverity severity) {
        int count = 0;
        for (VpfxDoctorSection section : sections) {
            for (VpfxDoctorCheck check : section.checks()) {
                if (check.severity() == severity) {
                    count++;
                }
            }
        }
        return count;
    }

    private static VpfxDoctorSeverity computeOverallSeverity(List<VpfxDoctorSection> sections) {
        VpfxDoctorSeverity worst = VpfxDoctorSeverity.OK;
        for (VpfxDoctorSection section : sections) {
            for (VpfxDoctorCheck check : section.checks()) {
                if (check.severity().worseThan(worst)) {
                    worst = check.severity();
                }
            }
        }
        return worst;
    }
}
