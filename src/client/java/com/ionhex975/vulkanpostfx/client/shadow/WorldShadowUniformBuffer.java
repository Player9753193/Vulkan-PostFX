package com.ionhex975.vulkanpostfx.client.shadow;

import com.ionhex975.vulkanpostfx.client.state.PostFxRuntimeState;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

public final class WorldShadowUniformBuffer {
    public static final String BLOCK_NAME = "VpfxTerrainShadow";

    private static final int UBO_SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .putVec4()
            .putMat4f()
            .get();

    private static final float BASE_SHADOW_DARKNESS = readFloat(
            "vulkanpostfx.shadow.baseDarkness",
            0.42F
    );
    private static final float BASE_SHADOW_BIAS = readFloat(
            "vulkanpostfx.shadow.bias",
            0.00115F
    );

    private static GpuBuffer buffer;
    private static GpuBufferSlice slice;

    private WorldShadowUniformBuffer() {
    }

    public static boolean isWorldShadowEnabled() {
        ShadowFrameState state = ShadowFrameState.get();
        ShadowRenderTargetsLite targets = ShadowRenderTargetsLite.get();

        return state.isValid()
                && PostFxRuntimeState.isDebugEffectEnabled()
                && state.isShadowPassEnabled()
                && state.hasRenderableShadowLight()
                && state.isShadowTargetReady()
                && state.wasShadowPassExecuted()
                && targets.isReady()
                && targets.getShadowDepthTarget() != null
                && targets.getShadowDepthTarget().getDepthTextureView() != null;
    }

    public static GpuBufferSlice writeAndGet() {
        return writeAndGet(false);
    }

    /**
     * Writes the terrain shadow uniform for the shadow caster pass.
     *
     * Caster shaders need the same shadow origin as the receiver shader, but they run before
     * ShadowFrameState.markShadowPassExecuted(...), so the normal receiver enable gate would still
     * be false. Use this method only while rendering shadow_depth.
     */
    public static GpuBufferSlice writeAndGetForShadowPass() {
        return writeAndGet(true);
    }

    private static GpuBufferSlice writeAndGet(boolean forceEnabled) {
        RenderSystem.assertOnRenderThread();

        ensureAllocated();

        ShadowFrameState state = ShadowFrameState.get();
        Matrix4f shadowViewProjection = state.isValid()
                ? state.getShadowViewProjectionMatrix()
                : new Matrix4f().identity();

        float shadowMapSize = state.isShadowTargetReady()
                ? state.getShadowMapSize()
                : 0.0F;

        float shadowBias = BASE_SHADOW_BIAS;
        float shadowDarkness = BASE_SHADOW_DARKNESS * state.getShadowLightIntensity();
        float enabled = forceEnabled || isWorldShadowEnabled() ? 1.0F : 0.0F;
        var lightDirection = state.getSunDirection();
        var shadowOrigin = state.getShadowOrigin();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, UBO_SIZE)
                    .putVec4(
                            shadowBias,
                            shadowMapSize,
                            shadowDarkness,
                            enabled
                    )
                    .putVec4(
                            lightDirection.x,
                            lightDirection.y,
                            lightDirection.z,
                            0.0F
                    )
                    .putVec4(
                            (float) shadowOrigin.x,
                            (float) shadowOrigin.y,
                            (float) shadowOrigin.z,
                            0.0F
                    )
                    .putMat4f(shadowViewProjection)
                    .get();

            RenderSystem.getDevice()
                    .createCommandEncoder()
                    .writeToBuffer(buffer.slice(), data);
        }

        return slice;
    }

    private static float readFloat(String key, float defaultValue) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static void ensureAllocated() {
        if (buffer != null && !buffer.isClosed() && slice != null) {
            return;
        }

        buffer = RenderSystem.getDevice().createBuffer(
                () -> "VulkanPostFX Terrain Shadow UBO",
                136,
                UBO_SIZE
        );
        slice = buffer.slice(0L, UBO_SIZE);
    }
}
