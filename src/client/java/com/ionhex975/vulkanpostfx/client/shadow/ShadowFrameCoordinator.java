package com.ionhex975.vulkanpostfx.client.shadow;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.diagnostics.VpfxDiagnosticsConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 每帧同步阴影矩阵、太阳/月亮主光源状态。
 *
 * 这里有一个非常关键的工程约束：
 * Minecraft 日落/月升附近，太阳与月亮的角度会在地平线附近同时接近可见边界。
 * 如果每帧简单用 sunAngle < 180 判断太阳/月亮，就会在临界点反复切换 shadow matrix，表现为阴影闪烁。
 *
 * 当前策略：
 * - 用光源高度估计 sunlight/moonlight score；
 * - 月光最大阴影强度显著低于太阳；
 * - 用滞回 hysteresis 避免太阳/月亮在临界点抢主光源；
 * - 黄昏/黎明弱光区允许 primaryLight=none，直接淡出动态阴影；
 * - 对阴影强度做轻量时间平滑，避免黄昏/月升时强度跳变；
 * - 阴影诊断日志降频，只在状态变化或固定间隔打印。
 */
public final class ShadowFrameCoordinator {
    private static final float DEFAULT_SUN_PATH_ROTATION = 0.0F;

    private static final ShadowLightTuning TUNING = ShadowLightTuning.load();

    private static int debugLogCounter;
    private static PrimaryLight lastPrimaryLight = PrimaryLight.NONE;
    private static PrimaryLight lastLoggedPrimaryLight = PrimaryLight.NONE;
    private static float lastSmoothedShadowLightIntensity;
    private static boolean tuningLogged;

    private ShadowFrameCoordinator() {
    }

    public static void syncFrame(
            Minecraft minecraft,
            DeltaTracker deltaTracker,
            CameraRenderState cameraState
    ) {
        logTuningOnce();

        if (minecraft.level == null || cameraState == null || !cameraState.initialized) {
            ShadowFrameState.get().invalidate();
            lastPrimaryLight = PrimaryLight.NONE;
            lastLoggedPrimaryLight = PrimaryLight.NONE;
            lastSmoothedShadowLightIntensity = 0.0F;
            return;
        }

        ShadowFrameState state = ShadowFrameState.get();

        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);

        float rawSunAngle = minecraft.gameRenderer
                .mainCamera()
                .attributeProbe()
                .getValue(EnvironmentAttributes.SUN_ANGLE, partialTick);
        float rawMoonAngle = minecraft.gameRenderer
                .mainCamera()
                .attributeProbe()
                .getValue(EnvironmentAttributes.MOON_ANGLE, partialTick);

        Vector3f sunDirection = ShadowMatricesLite.createShadowLightDirection(
                rawSunAngle,
                DEFAULT_SUN_PATH_ROTATION
        );
        Vector3f moonDirection = ShadowMatricesLite.createShadowLightDirection(
                rawMoonAngle,
                DEFAULT_SUN_PATH_ROTATION
        );

        float sunLightScore = smoothstep(TUNING.sunFadeStartY, TUNING.sunFadeEndY, sunDirection.y);
        float moonLightScore = smoothstep(TUNING.moonFadeStartY, TUNING.moonFadeEndY, moonDirection.y)
                * TUNING.moonMaxShadowIntensity;

        PrimaryLight previousPrimaryLight = lastPrimaryLight;
        PrimaryLight primaryLight = choosePrimaryLight(sunLightScore, moonLightScore);
        lastPrimaryLight = primaryLight;

        float selectedRawAngle;
        Vector3f selectedDirection;
        float targetShadowLightIntensity;

        if (primaryLight == PrimaryLight.SUN) {
            selectedRawAngle = rawSunAngle;
            selectedDirection = sunDirection;
            targetShadowLightIntensity = sunLightScore;
        } else if (primaryLight == PrimaryLight.MOON) {
            selectedRawAngle = rawMoonAngle;
            selectedDirection = moonDirection;
            targetShadowLightIntensity = moonLightScore;
        } else {
            // 没有足够稳定的主光源时，矩阵保持一个确定值，但实际阴影强度为 0。
            selectedRawAngle = rawSunAngle;
            selectedDirection = sunDirection;
            targetShadowLightIntensity = 0.0F;
        }

        float shadowLightIntensity = smoothIntensity(targetShadowLightIntensity, primaryLight, previousPrimaryLight);

        float shadowAngleDegrees = wrapDegrees(selectedRawAngle + 90.0F);
        float shadowAngle = shadowAngleDegrees / 360.0F;

        float shadowHalfPlane = Math.max(state.getTerrainShadowDistance(), 1.0F);
        float shadowInterval = resolveShadowTexelInterval(shadowHalfPlane, state.getShadowMapSize());

        // The shadow map is centered near the camera only to choose a local coverage region.
        // Its projection must not use the player's view direction or the main-render camera uniforms.
        // Caster and receiver shaders both subtract this stable origin explicitly.
        var shadowOrigin = ShadowMatricesLite.createStableShadowOrigin(cameraState.pos, shadowInterval);

        Matrix4f shadowView = ShadowMatricesLite.createModelViewMatrix(selectedDirection);

        Matrix4f shadowProjection = ShadowMatricesLite.createOrthoMatrix(
                shadowHalfPlane,
                ShadowMatricesLite.NEAR,
                ShadowMatricesLite.FAR
        );

        state.update(
                cameraState.pos,
                shadowOrigin,
                shadowAngle,
                selectedDirection,
                shadowView,
                shadowProjection,
                primaryLight.id,
                shadowLightIntensity,
                sunLightScore,
                moonLightScore
        );

        if (!state.hasRenderableShadowLight()) {
            state.setShadowPassEnabled(false);
        }

        maybeLogDiagnostics(
                cameraState,
                primaryLight,
                previousPrimaryLight,
                shadowLightIntensity,
                targetShadowLightIntensity,
                rawSunAngle,
                rawMoonAngle,
                sunLightScore,
                moonLightScore,
                shadowAngle,
                shadowHalfPlane,
                shadowInterval,
                selectedDirection,
                shadowOrigin
        );
    }

    private static PrimaryLight choosePrimaryLight(float sunScore, float moonScore) {
        if (lastPrimaryLight == PrimaryLight.SUN
                && sunScore > TUNING.lightNoneThreshold
                && moonScore < sunScore + TUNING.holdSunMargin) {
            return PrimaryLight.SUN;
        }

        if (lastPrimaryLight == PrimaryLight.MOON
                && moonScore > TUNING.lightNoneThreshold
                && sunScore < moonScore + TUNING.holdMoonMargin) {
            return PrimaryLight.MOON;
        }

        if (sunScore >= moonScore && sunScore > TUNING.lightNoneThreshold) {
            return PrimaryLight.SUN;
        }

        if (moonScore > TUNING.lightNoneThreshold) {
            return PrimaryLight.MOON;
        }

        return PrimaryLight.NONE;
    }

    private static float smoothIntensity(
            float targetIntensity,
            PrimaryLight primaryLight,
            PrimaryLight previousPrimaryLight
    ) {
        targetIntensity = clamp01(targetIntensity);

        if (primaryLight != previousPrimaryLight) {
            // 光源切换时更保守，避免太阳/月亮交界突然跳强度。
            lastSmoothedShadowLightIntensity = lerp(
                    lastSmoothedShadowLightIntensity,
                    targetIntensity,
                    TUNING.lightSwitchSmoothing
            );
        } else {
            lastSmoothedShadowLightIntensity = lerp(
                    lastSmoothedShadowLightIntensity,
                    targetIntensity,
                    TUNING.lightIntensitySmoothing
            );
        }

        if (targetIntensity <= 0.0F && lastSmoothedShadowLightIntensity < 0.002F) {
            lastSmoothedShadowLightIntensity = 0.0F;
        }

        return clamp01(lastSmoothedShadowLightIntensity);
    }

    private static void maybeLogDiagnostics(
            CameraRenderState cameraState,
            PrimaryLight primaryLight,
            PrimaryLight previousPrimaryLight,
            float shadowLightIntensity,
            float targetShadowLightIntensity,
            float rawSunAngle,
            float rawMoonAngle,
            float sunLightScore,
            float moonLightScore,
            float shadowAngle,
            float shadowHalfPlane,
            float shadowInterval,
            Vector3f selectedDirection,
            net.minecraft.world.phys.Vec3 shadowOrigin
    ) {
        debugLogCounter++;

        boolean primaryChanged = primaryLight != previousPrimaryLight || primaryLight != lastLoggedPrimaryLight;
        boolean intervalElapsed = debugLogCounter >= TUNING.diagnosticLogIntervalFrames;
        if (!primaryChanged && !intervalElapsed) {
            return;
        }

        debugLogCounter = 0;
        lastLoggedPrimaryLight = primaryLight;

        VulkanPostFX.LOGGER.info(
                "[{}] Shadow pipeline v1 synced: cameraPos={}, primaryLight={}, intensity={}, targetIntensity={}, rawSunAngle={}, rawMoonAngle={}, sunScore={}, moonScore={}, shadowAngle={}, halfPlane={}, shadowInterval={}, shadowOrigin={}, lightDir=({}, {}, {}), logReason={}",
                VulkanPostFX.MOD_ID,
                cameraState.pos,
                primaryLight.id,
                round3(shadowLightIntensity),
                round3(targetShadowLightIntensity),
                round3(rawSunAngle),
                round3(rawMoonAngle),
                round3(sunLightScore),
                round3(moonLightScore),
                round3(shadowAngle),
                round3(shadowHalfPlane),
                round5(shadowInterval),
                shadowOrigin,
                round3(selectedDirection.x),
                round3(selectedDirection.y),
                round3(selectedDirection.z),
                primaryChanged ? "primary-light-change" : "interval"
        );
    }

    private static void logTuningOnce() {
        if (tuningLogged) {
            return;
        }
        tuningLogged = true;

        VulkanPostFX.LOGGER.info(
                "[{}] Shadow light tuning: sunFadeY={}..{}, moonFadeY={}..{}, moonMaxIntensity={}, noneThreshold={}, holdSunMargin={}, holdMoonMargin={}, intensitySmoothing={}, switchSmoothing={}, diagnosticLogIntervalFrames={}",
                VulkanPostFX.MOD_ID,
                TUNING.sunFadeStartY,
                TUNING.sunFadeEndY,
                TUNING.moonFadeStartY,
                TUNING.moonFadeEndY,
                TUNING.moonMaxShadowIntensity,
                TUNING.lightNoneThreshold,
                TUNING.holdSunMargin,
                TUNING.holdMoonMargin,
                TUNING.lightIntensitySmoothing,
                TUNING.lightSwitchSmoothing,
                TUNING.diagnosticLogIntervalFrames
        );
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        if (value <= edge0) {
            return 0.0F;
        }
        if (value >= edge1) {
            return 1.0F;
        }
        float t = (value - edge0) / (edge1 - edge0);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float resolveShadowTexelInterval(float shadowHalfPlane, int shadowMapSize) {
        if (shadowMapSize <= 0) {
            return 0.0F;
        }

        return Math.max((shadowHalfPlane * 2.0F) / shadowMapSize, 1.0F / 4096.0F);
    }

    private static float lerp(float from, float to, float alpha) {
        return from + (to - from) * clamp01(alpha);
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value)) {
            return 0.0F;
        }
        if (value <= 0.0F) {
            return 0.0F;
        }
        if (value >= 1.0F) {
            return 1.0F;
        }
        return value;
    }

    private static float round3(float v) {
        return Math.round(v * 1000.0F) / 1000.0F;
    }

    private static float round5(float v) {
        return Math.round(v * 100000.0F) / 100000.0F;
    }

    private static float wrapDegrees(float value) {
        float wrapped = value % 360.0F;
        if (wrapped < 0.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    private enum PrimaryLight {
        SUN("sun"),
        MOON("moon"),
        NONE("none");

        private final String id;

        PrimaryLight(String id) {
            this.id = id;
        }
    }

    private static final class ShadowLightTuning {
        private final float sunFadeStartY;
        private final float sunFadeEndY;
        private final float moonFadeStartY;
        private final float moonFadeEndY;
        private final float moonMaxShadowIntensity;
        private final float lightNoneThreshold;
        private final float holdSunMargin;
        private final float holdMoonMargin;
        private final float lightIntensitySmoothing;
        private final float lightSwitchSmoothing;
        private final int diagnosticLogIntervalFrames;

        private ShadowLightTuning(
                float sunFadeStartY,
                float sunFadeEndY,
                float moonFadeStartY,
                float moonFadeEndY,
                float moonMaxShadowIntensity,
                float lightNoneThreshold,
                float holdSunMargin,
                float holdMoonMargin,
                float lightIntensitySmoothing,
                float lightSwitchSmoothing,
                int diagnosticLogIntervalFrames
        ) {
            this.sunFadeStartY = sunFadeStartY;
            this.sunFadeEndY = Math.max(sunFadeStartY + 0.001F, sunFadeEndY);
            this.moonFadeStartY = moonFadeStartY;
            this.moonFadeEndY = Math.max(moonFadeStartY + 0.001F, moonFadeEndY);
            this.moonMaxShadowIntensity = clamp01(moonMaxShadowIntensity);
            this.lightNoneThreshold = clamp01(lightNoneThreshold);
            this.holdSunMargin = Math.max(0.0F, holdSunMargin);
            this.holdMoonMargin = Math.max(0.0F, holdMoonMargin);
            this.lightIntensitySmoothing = clamp01(lightIntensitySmoothing);
            this.lightSwitchSmoothing = clamp01(lightSwitchSmoothing);
            this.diagnosticLogIntervalFrames = Math.max(30, diagnosticLogIntervalFrames);
        }

        private static ShadowLightTuning load() {
            return new ShadowLightTuning(
                    readFloat("vulkanpostfx.shadow.sunFadeStartY", 0.025F),
                    readFloat("vulkanpostfx.shadow.sunFadeEndY", 0.180F),
                    readFloat("vulkanpostfx.shadow.moonFadeStartY", 0.080F),
                    readFloat("vulkanpostfx.shadow.moonFadeEndY", 0.360F),
                    readFloat("vulkanpostfx.shadow.moonMaxIntensity", 0.12F),
                    readFloat("vulkanpostfx.shadow.lightNoneThreshold", 0.018F),
                    readFloat("vulkanpostfx.shadow.holdSunMargin", 0.080F),
                    readFloat("vulkanpostfx.shadow.holdMoonMargin", 0.020F),
                    readFloat("vulkanpostfx.shadow.intensitySmoothing", 0.18F),
                    readFloat("vulkanpostfx.shadow.switchSmoothing", 0.08F),
                    VpfxDiagnosticsConfig.shadowSyncLogIntervalFrames()
            );
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

        private static int readInt(String key, int defaultValue) {
            String value = System.getProperty(key);
            if (value == null || value.isBlank()) {
                return defaultValue;
            }
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
    }
}
