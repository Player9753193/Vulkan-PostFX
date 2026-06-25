package com.ionhex975.vulkanpostfx.client.diagnostics.doctor;

public enum VpfxDoctorSeverity {
    OK,
    INFO,
    WARN,
    ERROR,
    SKIPPED;

    public boolean worseThan(VpfxDoctorSeverity other) {
        return rank(this) > rank(other);
    }

    private static int rank(VpfxDoctorSeverity severity) {
        if (severity == null) {
            return 0;
        }
        return switch (severity) {
            case OK -> 0;
            case INFO -> 1;
            case SKIPPED -> 2;
            case WARN -> 3;
            case ERROR -> 4;
        };
    }
}
