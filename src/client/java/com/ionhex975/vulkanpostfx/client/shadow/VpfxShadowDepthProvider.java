package com.ionhex975.vulkanpostfx.client.shadow;

import com.ionhex975.vulkanpostfx.client.runtime.texture.dynamic.VpfxRuntimeTextureBus;

/**
 * Central state provider for VPFX shadow-depth availability.
 *
 * Backends and diagnostics should query this provider instead of independently
 * guessing whether ShadowRenderTargetsLite / ShadowFrameState are usable.
 */
public final class VpfxShadowDepthProvider {
    private static volatile VpfxShadowDepthState state = VpfxShadowDepthState.unavailable("not prepared yet");

    private VpfxShadowDepthProvider() {
    }

    public static VpfxShadowDepthState currentState() {
        return state;
    }

    public static boolean isAvailable() {
        return state.available();
    }

    public static boolean isTargetReady() {
        return state.targetReady();
    }

    public static void markAllocated(int size) {
        VpfxRuntimeTextureBus.markUnavailable(VpfxRuntimeTextureBus.SHADOW_DEPTH, "target allocated; shadow pass not executed yet");
        ShadowFrameState frameState = ShadowFrameState.get();
        state = new VpfxShadowDepthState(
                false,
                true,
                Math.max(0, size),
                Math.max(0, size),
                state.frameEpoch(),
                VpfxShadowDepthSource.UNAVAILABLE,
                frameState.getPrimaryLight(),
                frameState.getShadowLightIntensity(),
                frameState.isShadowPassEnabled(),
                frameState.wasShadowPassExecuted(),
                frameState.wereShadowCastersRendered(),
                "target allocated; shadow pass not executed yet"
        );
    }

    public static void markPrepared(ShadowFrameState frameState) {
        VpfxRuntimeTextureBus.markUnavailable(VpfxRuntimeTextureBus.SHADOW_DEPTH, "target ready; waiting for shadow pass execution");
        if (frameState == null) {
            markUnavailable("shadow frame state is null");
            return;
        }

        state = new VpfxShadowDepthState(
                false,
                frameState.isShadowTargetReady(),
                Math.max(0, frameState.getShadowMapSize()),
                Math.max(0, frameState.getShadowMapSize()),
                state.frameEpoch(),
                VpfxShadowDepthSource.UNAVAILABLE,
                frameState.getPrimaryLight(),
                frameState.getShadowLightIntensity(),
                frameState.isShadowPassEnabled(),
                frameState.wasShadowPassExecuted(),
                frameState.wereShadowCastersRendered(),
                frameState.isShadowTargetReady()
                        ? "target ready; waiting for shadow pass execution"
                        : "shadow target is not ready"
        );
    }

    public static void markPassExecuted(ShadowFrameState frameState, boolean castersRendered, int frameEpoch) {
        if (frameState != null) {
            VpfxRuntimeTextureBus.markReady(
                    VpfxRuntimeTextureBus.SHADOW_DEPTH,
                    Math.max(0, frameState.getShadowMapSize()),
                    Math.max(0, frameState.getShadowMapSize()),
                    frameEpoch,
                    castersRendered ? "shadow depth captured" : "shadow pass executed without casters"
            );
        }
        if (frameState == null) {
            markUnavailable("shadow frame state is null after pass execution");
            return;
        }

        state = new VpfxShadowDepthState(
                true,
                frameState.isShadowTargetReady(),
                Math.max(0, frameState.getShadowMapSize()),
                Math.max(0, frameState.getShadowMapSize()),
                frameEpoch,
                VpfxShadowDepthSource.VPFX_SHADOW_DEPTH_LITE,
                frameState.getPrimaryLight(),
                frameState.getShadowLightIntensity(),
                frameState.isShadowPassEnabled(),
                true,
                castersRendered,
                castersRendered ? "ok" : "shadow pass executed; no casters rendered"
        );
    }

    public static void markUnavailable(String reason) {
        VpfxRuntimeTextureBus.markUnavailable(VpfxRuntimeTextureBus.SHADOW_DEPTH, reason);
        VpfxShadowDepthState previous = state;
        ShadowFrameState frameState = ShadowFrameState.get();
        state = new VpfxShadowDepthState(
                false,
                previous.targetReady(),
                previous.width(),
                previous.height(),
                previous.frameEpoch(),
                VpfxShadowDepthSource.UNAVAILABLE,
                frameState.getPrimaryLight(),
                frameState.getShadowLightIntensity(),
                frameState.isShadowPassEnabled(),
                frameState.wasShadowPassExecuted(),
                frameState.wereShadowCastersRendered(),
                reason == null || reason.isBlank() ? "shadow depth unavailable" : reason
        );
    }

    public static void markReleased(String reason) {
        VpfxRuntimeTextureBus.markUnavailable(VpfxRuntimeTextureBus.SHADOW_DEPTH, reason == null || reason.isBlank() ? "released" : reason);
        state = VpfxShadowDepthState.unavailable(reason == null || reason.isBlank() ? "released" : reason);
    }
}
