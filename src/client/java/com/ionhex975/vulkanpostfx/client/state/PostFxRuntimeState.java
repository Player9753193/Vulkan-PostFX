package com.ionhex975.vulkanpostfx.client.state;

import com.ionhex975.vulkanpostfx.client.effect.PostFxEffectRegistry;
import com.ionhex975.vulkanpostfx.client.runtime.vpfx.VpfxPostChainBackend;
import com.ionhex975.vulkanpostfx.client.runtime.vpfx.VpfxRuntimeBackend;
import com.ionhex975.vulkanpostfx.client.runtime.vpfx.VpfxRuntimeBackendCapabilities;
import net.minecraft.resources.Identifier;

/**
 * 当前阶段状态：
 * 1. 客户端是否初始化；
 * 2. 是否已命中真正的世界渲染入口；
 * 3. 是否已命中 PostFX 候选插入位；
 * 4. 当前图形后端名称；
 * 5. 调试效果是否启用；
 * 6. 是否请求在下一帧重新应用效果（用于资源热重载后恢复）；
 * 7. 当前调试效果逻辑名（builtin fallback 使用）；
 * 8. 当前外部 ZIP 运行时入口 post effect id（若存在，则优先使用）。
 */
public final class PostFxRuntimeState {
    private static volatile boolean clientInitialized;
    private static volatile boolean worldRenderObserved;
    private static volatile boolean postSlotObserved;
    private static volatile boolean debugEffectEnabled;
    private static volatile boolean reapplyRequested;
    private static volatile boolean worldStageExternalEffectApplied;
    private static volatile boolean shadowDepthDebugViewEnabled;
    private static volatile boolean debugHudVisible = Boolean.getBoolean("vulkanpostfx.debug.hud");
    private static volatile boolean debugEffectEnabledBeforeShadowDepthDebug;
    private static volatile String effectKeyBeforeShadowDepthDebug = "debug_invert";
    private static volatile Identifier externalPostEffectIdBeforeShadowDepthDebug;
    private static volatile String backendName = "unknown";
    private static volatile String activeRuntimeBackendId = "minecraft_postchain";
    private static volatile String activeRuntimeBackendDisplayName = "Minecraft PostChain Backend";
    private static volatile VpfxRuntimeBackendCapabilities activeRuntimeBackendCapabilities = new VpfxPostChainBackend().capabilities();
    private static volatile String activeEffectKey = "debug_invert";
    private static volatile Identifier activeExternalPostEffectId;
    private static volatile Identifier failedExternalPostEffectId;
    private static volatile Identifier nativeRuntimeFallbackExternalPostEffectId;
    private static volatile String nativeRuntimeFallbackStage = "NONE";
    private static volatile String nativeRuntimeFallbackReason = "none";
    private static volatile boolean pendingTransientAllocationCheck;
    private static volatile boolean pendingUserShaderPipelineCreate;
    private static volatile boolean skipPostChainThisFrame;
    private static volatile boolean nativeDiagnosticDrawSucceededThisFrame;
    private static volatile String skipPostChainReason = "none";
    private static int nativeDiagnosticDrawSuccessCount;
    private static int postChainSkippedFrameCount;
    private static int nativeDiagnosticDrawFailureCount;
    private static final java.util.concurrent.atomic.AtomicInteger perFrameEpoch = new java.util.concurrent.atomic.AtomicInteger();
    private static int lastResetEpoch = -1;

    private PostFxRuntimeState() {
    }

    public static void markClientInit() {
        clientInitialized = true;
    }

    public static boolean isClientInitialized() {
        return clientInitialized;
    }

    public static void markWorldRenderObserved() {
        worldRenderObserved = true;
    }

    public static boolean isWorldRenderObserved() {
        return worldRenderObserved;
    }

    public static void markPostSlotObserved() {
        postSlotObserved = true;
    }

    public static boolean isPostSlotObserved() {
        return postSlotObserved;
    }

    public static void setBackendName(String backend) {
        backendName = backend;
    }

    public static String getBackendName() {
        return backendName;
    }

    public static void setActiveRuntimeBackend(VpfxRuntimeBackend backend) {
        if (backend == null) {
            setActiveRuntimeBackend(new VpfxPostChainBackend());
            return;
        }

        activeRuntimeBackendId = backend.id();
        activeRuntimeBackendDisplayName = backend.displayName();
        activeRuntimeBackendCapabilities = backend.capabilities();
    }

    public static void fallbackActiveRuntimeBackendToPostChain(Identifier externalId, String reason) {
        fallbackActiveRuntimeBackendToPostChain(externalId, "UNKNOWN", reason);
    }

    public static void fallbackActiveRuntimeBackendToPostChain(Identifier externalId, String stage, String reason) {
        setActiveRuntimeBackend(new VpfxPostChainBackend());
        markNativeRuntimeFallback(
                externalId != null ? externalId : activeExternalPostEffectId,
                stage,
                reason != null && !reason.isBlank()
                        ? reason
                        : "native runtime unavailable; using minecraft_postchain"
        );
        clearPendingNativeRuntimeChecks();
    }

    public static String getActiveRuntimeBackendId() {
        return activeRuntimeBackendId != null && !activeRuntimeBackendId.isBlank()
                ? activeRuntimeBackendId
                : "minecraft_postchain";
    }

    public static String getActiveRuntimeBackendDisplayName() {
        return activeRuntimeBackendDisplayName != null && !activeRuntimeBackendDisplayName.isBlank()
                ? activeRuntimeBackendDisplayName
                : getActiveRuntimeBackendId();
    }

    public static VpfxRuntimeBackendCapabilities getActiveRuntimeBackendCapabilities() {
        if (activeRuntimeBackendCapabilities == null) {
            activeRuntimeBackendCapabilities = new VpfxPostChainBackend().capabilities();
        }
        return activeRuntimeBackendCapabilities;
    }

    public static boolean isActiveNativeRuntimeBackend() {
        VpfxRuntimeBackendCapabilities capabilities = getActiveRuntimeBackendCapabilities();
        return capabilities != null && capabilities.nativeRuntime();
    }

    public static boolean activeRuntimeBackendUsesPostChain() {
        VpfxRuntimeBackendCapabilities capabilities = getActiveRuntimeBackendCapabilities();
        return capabilities == null || capabilities.usesPostChain();
    }

    public static boolean isDebugEffectEnabled() {
        return debugEffectEnabled;
    }

    public static boolean isDebugHudVisible() {
        return debugHudVisible;
    }

    public static void setDebugHudVisible(boolean visible) {
        debugHudVisible = visible;
    }

    public static boolean toggleDebugHudVisible() {
        debugHudVisible = !debugHudVisible;
        return debugHudVisible;
    }

    public static void setDebugEffectEnabled(boolean enabled) {
        if (debugEffectEnabled != enabled) {
            reapplyRequested = true;
        }
        debugEffectEnabled = enabled;
    }

    public static boolean toggleDebugEffectEnabled() {
        debugEffectEnabled = !debugEffectEnabled;
        reapplyRequested = true;
        return debugEffectEnabled;
    }

    public static boolean toggleShadowDepthDebugView() {
        if (!shadowDepthDebugViewEnabled) {
            debugEffectEnabledBeforeShadowDepthDebug = debugEffectEnabled;
            effectKeyBeforeShadowDepthDebug = activeEffectKey;
            externalPostEffectIdBeforeShadowDepthDebug = activeExternalPostEffectId;

            shadowDepthDebugViewEnabled = true;
            debugEffectEnabled = true;
            activeEffectKey = PostFxEffectRegistry.DEBUG_SHADOW_DEPTH;
            activeExternalPostEffectId = null;
            reapplyRequested = true;
            return true;
        }

        shadowDepthDebugViewEnabled = false;
        debugEffectEnabled = debugEffectEnabledBeforeShadowDepthDebug;
        activeEffectKey = effectKeyBeforeShadowDepthDebug;
        activeExternalPostEffectId = externalPostEffectIdBeforeShadowDepthDebug;
        reapplyRequested = true;
        return false;
    }

    public static boolean isShadowDepthDebugViewEnabled() {
        return shadowDepthDebugViewEnabled;
    }

    public static void requestReapply() {
        reapplyRequested = true;
    }

    public static boolean consumeReapplyRequest() {
        boolean requested = reapplyRequested;
        reapplyRequested = false;
        return requested;
    }

    public static void resetWorldStageExternalEffectApplied() {
        worldStageExternalEffectApplied = false;
    }

    public static void markWorldStageExternalEffectApplied() {
        worldStageExternalEffectApplied = true;
    }

    public static boolean isWorldStageExternalEffectApplied() {
        return worldStageExternalEffectApplied;
    }

    public static String getActiveEffectKey() {
        return activeEffectKey;
    }

    public static void setActiveEffectKey(String effectKey) {
        activeEffectKey = effectKey;
        reapplyRequested = true;
    }

    public static Identifier getActiveExternalPostEffectId() {
        return activeExternalPostEffectId;
    }

    public static void setActiveExternalPostEffectId(Identifier id) {
        activeExternalPostEffectId = id;
        if (id != null) {
            activeEffectKey = "external_zip";
        }
        failedExternalPostEffectId = null;
        clearNativeRuntimeFallback();
        reapplyRequested = true;
    }

    public static void clearActiveExternalPostEffectId() {
        activeExternalPostEffectId = null;
        failedExternalPostEffectId = null;
        clearNativeRuntimeFallback();
        reapplyRequested = true;
    }

    public static Identifier getFailedExternalPostEffectId() {
        return failedExternalPostEffectId;
    }

    public static void setFailedExternalPostEffectId(Identifier id) {
        failedExternalPostEffectId = id;
    }

    public static void clearFailedExternalPostEffectId() {
        failedExternalPostEffectId = null;
    }

    public static boolean isExternalPackMarkedFailed() {
        return failedExternalPostEffectId != null;
    }

    public static void markNativeRuntimeFallback(Identifier externalId, String reason) {
        markNativeRuntimeFallback(externalId, "UNKNOWN", reason);
    }

    public static void markNativeRuntimeFallback(Identifier externalId, String stage, String reason) {
        nativeRuntimeFallbackExternalPostEffectId = externalId;
        nativeRuntimeFallbackStage = stage != null && !stage.isBlank() ? stage : "UNKNOWN";
        nativeRuntimeFallbackReason = reason != null && !reason.isBlank()
                ? reason
                : "native runtime unavailable; using minecraft_postchain";
    }

    public static void clearNativeRuntimeFallback() {
        nativeRuntimeFallbackExternalPostEffectId = null;
        nativeRuntimeFallbackStage = "NONE";
        nativeRuntimeFallbackReason = "none";
    }

    public static boolean isNativeRuntimeFallbackActive() {
        return nativeRuntimeFallbackExternalPostEffectId != null;
    }

    public static Identifier getNativeRuntimeFallbackExternalPostEffectId() {
        return nativeRuntimeFallbackExternalPostEffectId;
    }

    public static String getNativeRuntimeFallbackStage() {
        return nativeRuntimeFallbackStage;
    }

    public static String getNativeRuntimeFallbackReason() {
        return nativeRuntimeFallbackReason;
    }

    public static String getNativeRuntimeFallbackSummary() {
        if (!isNativeRuntimeFallbackActive()) {
            return "none";
        }
        return "stage=" + getNativeRuntimeFallbackStage() + ", reason=" + getNativeRuntimeFallbackReason();
    }

    public static void markPendingTransientAllocationCheck() {
        pendingTransientAllocationCheck = true;
    }

    public static boolean consumePendingTransientAllocationCheck() {
        boolean pending = pendingTransientAllocationCheck;
        pendingTransientAllocationCheck = false;
        return pending;
    }

    public static void markPendingUserShaderPipelineCreate() {
        pendingUserShaderPipelineCreate = true;
    }

    public static boolean consumePendingUserShaderPipelineCreate() {
        boolean pending = pendingUserShaderPipelineCreate;
        pendingUserShaderPipelineCreate = false;
        return pending;
    }

    public static void clearPendingNativeRuntimeChecks() {
        pendingTransientAllocationCheck = false;
        pendingUserShaderPipelineCreate = false;
    }

    public static void markSkipPostChainThisFrame(boolean nativeDrawSucceeded, String reason) {
        skipPostChainThisFrame = nativeDrawSucceeded;
        nativeDiagnosticDrawSucceededThisFrame = nativeDrawSucceeded;
        skipPostChainReason = reason != null ? reason : "none";
    }

    public static boolean consumeSkipPostChainThisFrame() {
        boolean skip = skipPostChainThisFrame;
        skipPostChainThisFrame = false;
        return skip;
    }

    public static boolean isSkipPostChainThisFrame() {
        return skipPostChainThisFrame;
    }

    public static String skipPostChainReasonForLog() {
        return skipPostChainReason;
    }

    public static boolean nativeDiagnosticDrawSucceededThisFrame() {
        return nativeDiagnosticDrawSucceededThisFrame;
    }

    public static String skipPostChainReason() {
        return skipPostChainReason;
    }

    public static int incrementFrameEpoch() {
        return perFrameEpoch.incrementAndGet();
    }

    public static void resetPerFrameState() {
        int epoch = perFrameEpoch.get();
        if (lastResetEpoch == epoch) {
            return;
        }
        lastResetEpoch = epoch;
        nativeDiagnosticDrawSucceededThisFrame = false;
        skipPostChainThisFrame = false;
        skipPostChainReason = "none";
    }

    public static void incrementNativeDiagnosticDrawSuccess() {
        nativeDiagnosticDrawSuccessCount++;
    }

    public static void incrementPostChainSkipped() {
        postChainSkippedFrameCount++;
    }

    public static void incrementNativeDiagnosticDrawFailure() {
        nativeDiagnosticDrawFailureCount++;
    }

    public static int nativeDiagnosticDrawSuccessCount() {
        return nativeDiagnosticDrawSuccessCount;
    }

    public static int postChainSkippedFrameCount() {
        return postChainSkippedFrameCount;
    }

    public static int nativeDiagnosticDrawFailureCount() {
        return nativeDiagnosticDrawFailureCount;
    }

    public static String nativeDiagnosticSummary() {
        return "native draw succeeded=" + nativeDiagnosticDrawSuccessCount
                + ", postchain skipped=" + postChainSkippedFrameCount
                + ", native draw failed=" + nativeDiagnosticDrawFailureCount;
    }
}
