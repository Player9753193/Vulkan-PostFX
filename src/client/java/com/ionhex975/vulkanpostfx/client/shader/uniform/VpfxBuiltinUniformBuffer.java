package com.ionhex975.vulkanpostfx.client.shader.uniform;

import com.ionhex975.vulkanpostfx.client.light.VpfxHeldLightInfo;
import com.ionhex975.vulkanpostfx.client.light.VpfxHeldLightProvider;
import com.ionhex975.vulkanpostfx.client.shadow.ShadowFrameState;
import com.ionhex975.vulkanpostfx.client.shadow.ShadowMatricesLite;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

/**
 * VpfxBuiltins UBO writer。
 * 完整 GLSL 块定义见 {@link VpfxBuiltinUniformSourceInjector}。
 * 布局：13 × vec4 + 15 × mat4 = 1168 bytes (std140)。
 *
 * Held-light 数据故意复用旧布局中的 spare .w 分量，而不是新增 vec4 slot。
 * 这样可以保持 native direct backend 的 VpfxBuiltins UBO layout 稳定，避免旧 native pipeline 因 block size 改变而失效。
 */
public final class VpfxBuiltinUniformBuffer {
    public static final String BLOCK_NAME = "VpfxBuiltins";

    public static final int UBO_SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putMat4f()
            .putMat4f()
            .putMat4f()
            .putMat4f()
            .putMat4f()
            .putMat4f()
            .putMat4f()
            .putMat4f()
            .putMat4f()
            .putMat4f()
            .putMat4f()
            .putMat4f()
            .putMat4f()
            .putMat4f()
            .putMat4f()
            .get();

    private static long lastGameTick = Long.MIN_VALUE;
    private static float lastPartialTick = Float.NaN;

    private static GpuBuffer nativeUniformBuffer;
    private static GpuBufferSlice nativeUniformSlice;

    private static float cachedTimeSeconds = 0.0F;
    private static float cachedDeltaSeconds = 0.0F;
    private static float cachedGameTimeSeconds = 0.0F;
    private static float cachedFrameIndex = 0.0F;

    private static long lastFallbackNanoTime = System.nanoTime();

    private VpfxBuiltinUniformBuffer() {
    }

    /**
     * Native VPFX framegraph path owns its own VpfxBuiltins uniform buffer.
     *
     * Minecraft PostChain creates custom uniform buffers from post_effect JSON,
     * but the native framegraph bypasses PostChain, so it must allocate and bind
     * the same std140 block explicitly.
     */
    public static GpuBufferSlice writeAndGetNativeSlice() {
        RenderSystem.assertOnRenderThread();
        ensureNativeUniformAllocated();
        writeToExisting(nativeUniformBuffer);
        return nativeUniformSlice;
    }

    private static void ensureNativeUniformAllocated() {
        if (nativeUniformBuffer != null && !nativeUniformBuffer.isClosed() && nativeUniformSlice != null) {
            return;
        }

        nativeUniformBuffer = RenderSystem.getDevice().createBuffer(
                () -> "VulkanPostFX Native VpfxBuiltins UBO",
                136,
                UBO_SIZE
        );
        nativeUniformSlice = nativeUniformBuffer.slice(0L, UBO_SIZE);
    }

    public static void writeToExisting(GpuBuffer buffer) {
        if (buffer == null || buffer.isClosed()) {
            return;
        }

        RenderSystem.assertOnRenderThread();

        Snapshot snapshot = snapshot();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, UBO_SIZE)
                    .putVec4(
                            snapshot.timeSeconds,
                            snapshot.deltaSeconds,
                            snapshot.gameTimeSeconds,
                            snapshot.frameIndex
                    )
                    .putVec4(
                            snapshot.cameraX,
                            snapshot.cameraY,
                            snapshot.cameraZ,
                            snapshot.rainStrength
                    )
                    .putVec4(
                            snapshot.viewWidth,
                            snapshot.viewHeight,
                            snapshot.invViewWidth,
                            snapshot.invViewHeight
                    )
                    .putVec4(
                            snapshot.zNear,
                            snapshot.zFar,
                            snapshot.shadowMapSize,
                            snapshot.shadowBias
                    )
                    .putVec4(
                            snapshot.fogColorR,
                            snapshot.fogColorG,
                            snapshot.fogColorB,
                            snapshot.fogColorA
                    )
                    .putVec4(
                            snapshot.fogStart,
                            snapshot.fogEnd,
                            snapshot.fogRenderDistanceStart,
                            snapshot.fogRenderDistanceEnd
                    )
                    .putVec4(
                            snapshot.fogSkyEnd,
                            snapshot.fogCloudEnd,
                            snapshot.fogKind,
                            snapshot.heldLightRadius
                    )
                    .putVec4(
                            snapshot.skyColorR,
                            snapshot.skyColorG,
                            snapshot.skyColorB,
                            snapshot.isDay ? 1.0F : 0.0F
                    )
                    .putVec4(
                            snapshot.sunAngle,
                            snapshot.moonAngle,
                            snapshot.shadowAngle,
                            snapshot.heldLightEnabled ? 1.0F : 0.0F
                    )
                    .putVec4(
                            snapshot.sunPositionX,
                            snapshot.sunPositionY,
                            snapshot.sunPositionZ,
                            snapshot.heldLightRed
                    )
                    .putVec4(
                            snapshot.moonPositionX,
                            snapshot.moonPositionY,
                            snapshot.moonPositionZ,
                            snapshot.heldLightGreen
                    )
                    .putVec4(
                            snapshot.shadowLightPositionX,
                            snapshot.shadowLightPositionY,
                            snapshot.shadowLightPositionZ,
                            snapshot.heldLightBlue
                    )
                    .putVec4(
                            snapshot.upPositionX,
                            snapshot.upPositionY,
                            snapshot.upPositionZ,
                            snapshot.heldLightIntensity
                    )
                    .putMat4f(snapshot.projectionMatrix)
                    .putMat4f(snapshot.inverseProjectionMatrix)
                    .putMat4f(snapshot.previousProjectionMatrix)
                    .putMat4f(snapshot.previousInverseProjectionMatrix)
                    .putMat4f(snapshot.viewRotationMatrix)
                    .putMat4f(snapshot.inverseViewRotationMatrix)
                    .putMat4f(snapshot.previousViewRotationMatrix)
                    .putMat4f(snapshot.previousInverseViewRotationMatrix)
                    .putMat4f(snapshot.viewProjectionMatrix)
                    .putMat4f(snapshot.inverseViewProjectionMatrix)
                    .putMat4f(snapshot.shadowViewMatrix)
                    .putMat4f(snapshot.inverseShadowViewMatrix)
                    .putMat4f(snapshot.shadowProjectionMatrix)
                    .putMat4f(snapshot.inverseShadowProjectionMatrix)
                    .putMat4f(snapshot.shadowViewProjectionMatrix)
                    .get();

            RenderSystem.getDevice()
                    .createCommandEncoder()
                    .writeToBuffer(buffer.slice(), data);
        }
    }

    private static Snapshot snapshot() {
        Minecraft minecraft = Minecraft.getInstance();

        long currentNano = System.nanoTime();
        float fallbackDeltaSeconds = clamp((currentNano - lastFallbackNanoTime) / 1_000_000_000.0F, 0.0F, 0.25F);
        lastFallbackNanoTime = currentNano;

        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        long gameTick = minecraft.level != null ? minecraft.level.getGameTime() : Long.MIN_VALUE;

        boolean advancedFrame = gameTick != lastGameTick
                || Float.compare(partialTick, lastPartialTick) != 0;

        if (advancedFrame) {
            float deltaSeconds = clamp(
                    minecraft.getDeltaTracker().getRealtimeDeltaTicks() / 20.0F,
                    0.0F,
                    0.25F
            );

            if (!(deltaSeconds > 0.0F)) {
                deltaSeconds = fallbackDeltaSeconds;
            }

            cachedDeltaSeconds = deltaSeconds;
            cachedTimeSeconds += deltaSeconds;

            if (minecraft.level != null) {
                cachedGameTimeSeconds = (float) ((minecraft.level.getGameTime() + (double) partialTick) / 20.0);
            } else {
                cachedGameTimeSeconds = cachedTimeSeconds;
            }

            cachedFrameIndex += 1.0F;

            lastGameTick = gameTick;
            lastPartialTick = partialTick;
        }

        float cameraX = 0.0F;
        float cameraY = 0.0F;
        float cameraZ = 0.0F;

        ShadowFrameState shadowState = ShadowFrameState.get();
        if (shadowState.isValid()) {
            Vec3 cam = shadowState.getCameraPos();
            cameraX = (float) cam.x;
            cameraY = (float) cam.y;
            cameraZ = (float) cam.z;
        } else {
            Entity cameraEntity = minecraft.getCameraEntity();
            if (cameraEntity == null) {
                cameraEntity = minecraft.player;
            }

            if (cameraEntity != null) {
                cameraX = (float) cameraEntity.getX();
                cameraY = (float) cameraEntity.getY();
                cameraZ = (float) cameraEntity.getZ();
            }
        }

        float rainStrength = 0.0F;
        if (minecraft.level != null) {
            rainStrength = clamp(minecraft.level.getRainLevel(partialTick), 0.0F, 1.0F);
        }

        VpfxFrameProjectionState.Snapshot projection = VpfxFrameProjectionState.snapshot();
        VpfxFrameEnvironmentState.Snapshot environment = VpfxFrameEnvironmentState.snapshot();

        float viewWidth = projection.valid() ? projection.screenWidth() : 1.0F;
        float viewHeight = projection.valid() ? projection.screenHeight() : 1.0F;
        float invViewWidth = 1.0F / Math.max(1.0F, viewWidth);
        float invViewHeight = 1.0F / Math.max(1.0F, viewHeight);

        Matrix4f inverseProjection = projection.valid()
                ? projection.inverseProjectionMatrix()
                : new Matrix4f().identity();

        Matrix4f previousProjection = projection.valid()
                ? projection.previousProjectionMatrix()
                : new Matrix4f().identity();

        Matrix4f previousInverseProjection = projection.valid()
                ? projection.previousInverseProjectionMatrix()
                : new Matrix4f().identity();

        Matrix4f viewRotation = projection.valid()
                ? projection.viewRotationMatrix()
                : new Matrix4f().identity();

        Matrix4f inverseViewRotation = projection.valid()
                ? projection.inverseViewRotationMatrix()
                : new Matrix4f().identity();

        Matrix4f previousViewRotation = projection.valid()
                ? projection.previousViewRotationMatrix()
                : new Matrix4f().identity();

        Matrix4f previousInverseViewRotation = projection.valid()
                ? projection.previousInverseViewRotationMatrix()
                : new Matrix4f().identity();

        Matrix4f viewProjection = projection.valid()
                ? projection.viewProjectionMatrix()
                : new Matrix4f().identity();

        Vector3f sunPosition = resolveCelestialViewPosition(
                viewRotation,
                environment.valid(),
                environment.rawSunAngleDegrees()
        );
        Vector3f moonPosition = resolveCelestialViewPosition(
                viewRotation,
                environment.valid(),
                environment.rawMoonAngleDegrees()
        );
        Vector3f shadowLightPosition = environment.isDay()
                ? new Vector3f(sunPosition)
                : new Vector3f(moonPosition);
        Vector3f upPosition = resolveViewUpPosition(viewRotation);

        Matrix4f inverseViewProjection = projection.valid()
                ? projection.inverseViewProjectionMatrix()
                : new Matrix4f().identity();

        Matrix4f shadowView = shadowState.isValid()
                ? shadowState.getShadowViewMatrix()
                : new Matrix4f().identity();

        Matrix4f inverseShadowView = new Matrix4f(shadowView).invert();

        Matrix4f shadowProjection = shadowState.isValid()
                ? shadowState.getShadowProjectionMatrix()
                : new Matrix4f().identity();

        Matrix4f inverseShadowProjection = new Matrix4f(shadowProjection).invert();

        Matrix4f shadowViewProjection = shadowState.isValid()
                ? shadowState.getShadowViewProjectionMatrix()
                : new Matrix4f().identity();

        float shadowMapSize = shadowState.isShadowTargetReady()
                ? (float) shadowState.getShadowMapSize()
                : 0.0F;

        float shadowBias = 0.0015F;

        VpfxHeldLightInfo heldLight = VpfxHeldLightProvider.currentHeldLight();

        return new Snapshot(
                cachedTimeSeconds,
                cachedDeltaSeconds,
                cachedGameTimeSeconds,
                cachedFrameIndex,
                cameraX,
                cameraY,
                cameraZ,
                rainStrength,
                viewWidth,
                viewHeight,
                invViewWidth,
                invViewHeight,
                projection.valid() ? projection.zNear() : 0.05F,
                projection.valid() ? projection.zFar() : 1.0F,
                shadowMapSize,
                shadowBias,
                environment.valid() ? environment.fogColorR() : 0.0F,
                environment.valid() ? environment.fogColorG() : 0.0F,
                environment.valid() ? environment.fogColorB() : 0.0F,
                environment.valid() ? environment.fogColorA() : 1.0F,
                environment.valid() ? environment.fogStart() : 0.0F,
                environment.valid() ? environment.fogEnd() : 0.0F,
                environment.valid() ? environment.fogRenderDistanceStart() : 0.0F,
                environment.valid() ? environment.fogRenderDistanceEnd() : 0.0F,
                environment.valid() ? environment.fogSkyEnd() : 0.0F,
                environment.valid() ? environment.fogCloudEnd() : 0.0F,
                environment.valid() ? environment.fogKind() : 0.0F,
                environment.valid() ? environment.skyColorR() : 0.0F,
                environment.valid() ? environment.skyColorG() : 0.0F,
                environment.valid() ? environment.skyColorB() : 0.0F,
                environment.valid() ? environment.sunAngle() : 0.0F,
                environment.valid() ? environment.moonAngle() : 0.5F,
                environment.valid() ? environment.shadowAngle() : 0.0F,
                environment.valid() && environment.isDay(),
                sunPosition.x,
                sunPosition.y,
                sunPosition.z,
                moonPosition.x,
                moonPosition.y,
                moonPosition.z,
                shadowLightPosition.x,
                shadowLightPosition.y,
                shadowLightPosition.z,
                upPosition.x,
                upPosition.y,
                upPosition.z,
                heldLight.red(),
                heldLight.green(),
                heldLight.blue(),
                heldLight.intensity(),
                heldLight.radius(),
                heldLight.enabled(),
                projection.valid() ? projection.projectionMatrix() : new Matrix4f().identity(),
                inverseProjection,
                previousProjection,
                previousInverseProjection,
                viewRotation,
                inverseViewRotation,
                previousViewRotation,
                previousInverseViewRotation,
                viewProjection,
                inverseViewProjection,
                shadowView,
                inverseShadowView,
                shadowProjection,
                inverseShadowProjection,
                shadowViewProjection
        );
    }

    private static Vector3f resolveCelestialViewPosition(
            Matrix4f viewRotation,
            boolean environmentValid,
            float rawAngleDegrees
    ) {
        if (!environmentValid) {
            return new Vector3f();
        }

        Vector3f worldDirection = ShadowMatricesLite.createShadowLightDirection(rawAngleDegrees, 0.0F);
        Vector3f viewDirection = new Vector3f(worldDirection).mul(100.0F);
        viewRotation.transformDirection(viewDirection);
        return viewDirection;
    }

    private static Vector3f resolveViewUpPosition(Matrix4f viewRotation) {
        Vector3f up = new Vector3f(0.0F, 100.0F, 0.0F);
        viewRotation.transformDirection(up);
        return up;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Snapshot(
            float timeSeconds,
            float deltaSeconds,
            float gameTimeSeconds,
            float frameIndex,
            float cameraX,
            float cameraY,
            float cameraZ,
            float rainStrength,
            float viewWidth,
            float viewHeight,
            float invViewWidth,
            float invViewHeight,
            float zNear,
            float zFar,
            float shadowMapSize,
            float shadowBias,
            float fogColorR,
            float fogColorG,
            float fogColorB,
            float fogColorA,
            float fogStart,
            float fogEnd,
            float fogRenderDistanceStart,
            float fogRenderDistanceEnd,
            float fogSkyEnd,
            float fogCloudEnd,
            float fogKind,
            float skyColorR,
            float skyColorG,
            float skyColorB,
            float sunAngle,
            float moonAngle,
            float shadowAngle,
            boolean isDay,
            float sunPositionX,
            float sunPositionY,
            float sunPositionZ,
            float moonPositionX,
            float moonPositionY,
            float moonPositionZ,
            float shadowLightPositionX,
            float shadowLightPositionY,
            float shadowLightPositionZ,
            float upPositionX,
            float upPositionY,
            float upPositionZ,
            float heldLightRed,
            float heldLightGreen,
            float heldLightBlue,
            float heldLightIntensity,
            float heldLightRadius,
            boolean heldLightEnabled,
            Matrix4f projectionMatrix,
            Matrix4f inverseProjectionMatrix,
            Matrix4f previousProjectionMatrix,
            Matrix4f previousInverseProjectionMatrix,
            Matrix4f viewRotationMatrix,
            Matrix4f inverseViewRotationMatrix,
            Matrix4f previousViewRotationMatrix,
            Matrix4f previousInverseViewRotationMatrix,
            Matrix4f viewProjectionMatrix,
            Matrix4f inverseViewProjectionMatrix,
            Matrix4f shadowViewMatrix,
            Matrix4f inverseShadowViewMatrix,
            Matrix4f shadowProjectionMatrix,
            Matrix4f inverseShadowProjectionMatrix,
            Matrix4f shadowViewProjectionMatrix
    ) {
    }
}
