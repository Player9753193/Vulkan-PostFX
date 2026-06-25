package com.ionhex975.vulkanpostfx.client.diagnostics.doctor;

public record VpfxDoctorCheck(
        VpfxDoctorSeverity severity,
        String code,
        String label,
        String value,
        String detail
) {
    public VpfxDoctorCheck {
        severity = severity == null ? VpfxDoctorSeverity.INFO : severity;
        code = blankAs(code, "unknown");
        label = blankAs(label, code);
        value = blankAs(value, "");
        detail = blankAs(detail, "");
    }

    public static VpfxDoctorCheck ok(String code, String label, Object value) {
        return new VpfxDoctorCheck(VpfxDoctorSeverity.OK, code, label, String.valueOf(value), "");
    }

    public static VpfxDoctorCheck info(String code, String label, Object value) {
        return new VpfxDoctorCheck(VpfxDoctorSeverity.INFO, code, label, String.valueOf(value), "");
    }

    public static VpfxDoctorCheck info(String code, String label, Object value, String detail) {
        return new VpfxDoctorCheck(VpfxDoctorSeverity.INFO, code, label, String.valueOf(value), detail);
    }

    public static VpfxDoctorCheck warn(String code, String label, Object value, String detail) {
        return new VpfxDoctorCheck(VpfxDoctorSeverity.WARN, code, label, String.valueOf(value), detail);
    }

    public static VpfxDoctorCheck error(String code, String label, Object value, String detail) {
        return new VpfxDoctorCheck(VpfxDoctorSeverity.ERROR, code, label, String.valueOf(value), detail);
    }

    public static VpfxDoctorCheck skipped(String code, String label, String detail) {
        return new VpfxDoctorCheck(VpfxDoctorSeverity.SKIPPED, code, label, "skipped", detail);
    }

    private static String blankAs(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
