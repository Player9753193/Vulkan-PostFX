package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx;

import com.ionhex975.vulkanpostfx.client.runtime.vpfx.VpfxRuntimeBackend;

/**
 * Backend selection decision for VPFX native-first safe fallback.
 *
 * The selection step happens before the runtime pack has been materialized, so it cannot write
 * fallback state directly to PostFxRuntimeState because the generated external post effect id is
 * not known yet. ActivePostEffectBridge stores this result, materializes the selected backend,
 * then records the fallback stage/reason once the runtime id exists.
 */
public final class VpfxBackendSelectionResult {

    public static final String STAGE_NONE = "NONE";
    public static final String STAGE_NATIVE_DISABLED = "NATIVE_DISABLED";
    public static final String STAGE_SUPPORT_CHECK = "SUPPORT_CHECK";
    public static final String STAGE_PREPARE = "PREPARE";
    public static final String STAGE_FRAME_EXECUTION = "FRAME_EXECUTION";

    private final VpfxRuntimeBackend backend;
    private final boolean nativeAttempted;
    private final boolean nativeSupported;
    private final boolean fallbackUsed;
    private final String fallbackStage;
    private final String fallbackReason;

    private VpfxBackendSelectionResult(
            VpfxRuntimeBackend backend,
            boolean nativeAttempted,
            boolean nativeSupported,
            boolean fallbackUsed,
            String fallbackStage,
            String fallbackReason
    ) {
        this.backend = backend;
        this.nativeAttempted = nativeAttempted;
        this.nativeSupported = nativeSupported;
        this.fallbackUsed = fallbackUsed;
        this.fallbackStage = fallbackStage == null || fallbackStage.isBlank() ? STAGE_NONE : fallbackStage;
        this.fallbackReason = fallbackReason == null || fallbackReason.isBlank() ? "none" : fallbackReason;
    }

    public static VpfxBackendSelectionResult nativeSelected(VpfxRuntimeBackend backend) {
        return new VpfxBackendSelectionResult(backend, true, true, false, STAGE_NONE, "none");
    }

    public static VpfxBackendSelectionResult nativeForced(VpfxRuntimeBackend backend, String supportFailureReason) {
        String reason = supportFailureReason == null || supportFailureReason.isBlank()
                ? "native support check failed, but force mode selected native backend"
                : supportFailureReason;
        return new VpfxBackendSelectionResult(backend, true, false, false, STAGE_NONE, reason);
    }

    public static VpfxBackendSelectionResult postChainSelected(VpfxRuntimeBackend backend, String reason) {
        return new VpfxBackendSelectionResult(backend, false, false, false, STAGE_NATIVE_DISABLED, reason);
    }

    public static VpfxBackendSelectionResult postChainFallback(
            VpfxRuntimeBackend backend,
            boolean nativeAttempted,
            boolean nativeSupported,
            String fallbackStage,
            String fallbackReason
    ) {
        return new VpfxBackendSelectionResult(
                backend,
                nativeAttempted,
                nativeSupported,
                true,
                fallbackStage,
                fallbackReason
        );
    }

    public VpfxRuntimeBackend backend() {
        return backend;
    }

    public boolean nativeAttempted() {
        return nativeAttempted;
    }

    public boolean nativeSupported() {
        return nativeSupported;
    }

    public boolean fallbackUsed() {
        return fallbackUsed;
    }

    public String fallbackStage() {
        return fallbackStage;
    }

    public String fallbackReason() {
        return fallbackReason;
    }

    @Override
    public String toString() {
        return "VpfxBackendSelectionResult{" 
                + "backend=" + (backend == null ? "null" : backend.id())
                + ", nativeAttempted=" + nativeAttempted
                + ", nativeSupported=" + nativeSupported
                + ", fallbackUsed=" + fallbackUsed
                + ", fallbackStage='" + fallbackStage + '\''
                + ", fallbackReason='" + fallbackReason + '\''
                + '}';
    }
}
