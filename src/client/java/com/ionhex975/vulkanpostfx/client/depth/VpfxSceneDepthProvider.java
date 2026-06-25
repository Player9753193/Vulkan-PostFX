package com.ionhex975.vulkanpostfx.client.depth;

import com.ionhex975.vulkanpostfx.client.runtime.texture.dynamic.VpfxRuntimeTextureBus;

public final class VpfxSceneDepthProvider {
    private static volatile VpfxSceneDepthState state = VpfxSceneDepthState.unavailable("not captured yet");

    private VpfxSceneDepthProvider() {
    }

    public static VpfxSceneDepthState currentState() {
        return state;
    }

    public static boolean isAvailable() {
        return state.available();
    }

    public static boolean isTargetReady() {
        return state.targetReady();
    }

    public static void markAllocated(int width, int height) {
        VpfxRuntimeTextureBus.markUnavailable(VpfxRuntimeTextureBus.SCENE_DEPTH, "target allocated; depth not captured yet");
        state = new VpfxSceneDepthState(
                false,
                true,
                Math.max(0, width),
                Math.max(0, height),
                state.frameEpoch(),
                VpfxSceneDepthSource.UNAVAILABLE,
                "target allocated; depth not captured yet"
        );
    }

    public static void markCaptured(int width, int height, int frameEpoch) {
        VpfxRuntimeTextureBus.markReady(VpfxRuntimeTextureBus.SCENE_DEPTH, width, height, frameEpoch, "scene depth captured from Minecraft main target");
        state = new VpfxSceneDepthState(
                true,
                true,
                Math.max(0, width),
                Math.max(0, height),
                frameEpoch,
                VpfxSceneDepthSource.MINECRAFT_MAIN_DEPTH_COPY,
                "ok"
        );
    }

    public static void markUnavailable(String reason) {
        VpfxRuntimeTextureBus.markUnavailable(VpfxRuntimeTextureBus.SCENE_DEPTH, reason);
        VpfxSceneDepthState previous = state;
        state = new VpfxSceneDepthState(
                false,
                previous.targetReady(),
                previous.width(),
                previous.height(),
                previous.frameEpoch(),
                VpfxSceneDepthSource.UNAVAILABLE,
                reason == null || reason.isBlank() ? "scene depth unavailable" : reason
        );
    }

    public static void markReleased(String reason) {
        VpfxRuntimeTextureBus.markUnavailable(VpfxRuntimeTextureBus.SCENE_DEPTH, reason == null || reason.isBlank() ? "released" : reason);
        state = VpfxSceneDepthState.unavailable(reason == null || reason.isBlank() ? "released" : reason);
    }
}
