package com.ionhex975.vulkanpostfx.client.diagnostics;

/**
 * Central switchboard for development diagnostics that are useful while bringing
 * up new native-rendering milestones, but too noisy for normal mod testing.
 */
public final class VpfxDiagnosticsConfig {
    public static final String LEGACY_NATIVE_DIAGNOSTICS_PROPERTY =
            "vulkanpostfx.debug.legacyNativeDiagnostics";
    public static final String SHADOW_PASS_LOG_PROPERTY =
            "vulkanpostfx.debug.shadowPassLog";
    public static final String SHADOW_SYNC_LOG_PROPERTY =
            "vulkanpostfx.debug.shadowSyncLog";
    public static final String LOG_SUMMARY_INTERVAL_PROPERTY =
            "vulkanpostfx.debug.logSummaryIntervalFrames";
    public static final String SHADOW_PASS_INTERVAL_PROPERTY =
            "vulkanpostfx.shadow.passLogIntervalFrames";
    public static final String SHADOW_SYNC_INTERVAL_PROPERTY =
            "vulkanpostfx.shadow.logIntervalFrames";
    public static final String FAILED_PACK_WARNING_INTERVAL_PROPERTY =
            "vulkanpostfx.debug.failedPackWarningIntervalFrames";

    private static final int DEFAULT_SUMMARY_INTERVAL_FRAMES = 1200;
    private static final int VERBOSE_SUMMARY_INTERVAL_FRAMES = 60;

    private VpfxDiagnosticsConfig() {
    }

    public static boolean legacyNativeDiagnosticsEnabled() {
        return Boolean.getBoolean(LEGACY_NATIVE_DIAGNOSTICS_PROPERTY);
    }

    public static boolean shadowPassLogEnabled() {
        return Boolean.getBoolean(SHADOW_PASS_LOG_PROPERTY);
    }

    public static boolean shadowSyncLogEnabled() {
        return Boolean.getBoolean(SHADOW_SYNC_LOG_PROPERTY);
    }

    public static int summaryIntervalFrames() {
        return readPositiveInt(LOG_SUMMARY_INTERVAL_PROPERTY, DEFAULT_SUMMARY_INTERVAL_FRAMES);
    }

    public static int shadowPassLogIntervalFrames() {
        int defaultInterval = shadowPassLogEnabled()
                ? VERBOSE_SUMMARY_INTERVAL_FRAMES
                : summaryIntervalFrames();
        return readPositiveInt(SHADOW_PASS_INTERVAL_PROPERTY, defaultInterval);
    }

    public static int shadowSyncLogIntervalFrames() {
        int defaultInterval = shadowSyncLogEnabled()
                ? VERBOSE_SUMMARY_INTERVAL_FRAMES
                : summaryIntervalFrames();
        return readPositiveInt(SHADOW_SYNC_INTERVAL_PROPERTY, defaultInterval);
    }

    public static int failedPackWarningIntervalFrames() {
        return readPositiveInt(FAILED_PACK_WARNING_INTERVAL_PROPERTY, summaryIntervalFrames());
    }

    public static int readPositiveInt(String property, int defaultValue) {
        String raw = System.getProperty(property);
        if (raw == null || raw.isBlank()) {
            return Math.max(1, defaultValue);
        }

        try {
            return Math.max(1, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException ignored) {
            return Math.max(1, defaultValue);
        }
    }
}
