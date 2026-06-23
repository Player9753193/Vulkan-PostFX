package com.ionhex975.vulkanpostfx.client.shader.uniform;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.attribute.EnvironmentAttributes;

public final class VpfxFrameEnvironmentState {
    private static final Object LOCK = new Object();

    private static boolean valid;

    private static float fogColorR;
    private static float fogColorG;
    private static float fogColorB;
    private static float fogColorA = 1.0F;

    private static float fogStart;
    private static float fogEnd;
    private static float fogRenderDistanceStart;
    private static float fogRenderDistanceEnd;
    private static float fogSkyEnd;
    private static float fogCloudEnd;
    private static float fogKind;

    private static float skyColorR;
    private static float skyColorG;
    private static float skyColorB;

    private static float rawSunAngleDegrees;
    private static float rawMoonAngleDegrees;
    private static float sunAngle;
    private static float moonAngle;
    private static float shadowAngle;
    private static boolean isDay;

    private VpfxFrameEnvironmentState() {
    }

    public static void capture(CameraRenderState cameraState, float partialTick) {
        if (cameraState == null || !cameraState.initialized) {
            clear();
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer != null ? minecraft.gameRenderer.mainCamera() : null;
        if (camera == null) {
            clear();
            return;
        }

        FogData fogData = cameraState.fogData;
        int skyColor = camera.attributeProbe().getValue(EnvironmentAttributes.SKY_COLOR, partialTick);
        float rawSunAngle = camera.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, partialTick);
        float rawMoonAngle = camera.attributeProbe().getValue(EnvironmentAttributes.MOON_ANGLE, partialTick);

        float wrappedSunAngle = wrapDegrees(rawSunAngle + 90.0F);
        float wrappedMoonAngle = wrapDegrees(rawMoonAngle + 90.0F);
        boolean day = wrappedSunAngle < 180.0F;

        synchronized (LOCK) {
            fogColorR = fogData.color.x;
            fogColorG = fogData.color.y;
            fogColorB = fogData.color.z;
            fogColorA = fogData.color.w;

            fogStart = fogData.environmentalStart;
            fogEnd = fogData.environmentalEnd;
            fogRenderDistanceStart = fogData.renderDistanceStart;
            fogRenderDistanceEnd = fogData.renderDistanceEnd;
            fogSkyEnd = fogData.skyEnd;
            fogCloudEnd = fogData.cloudEnd;
            fogKind = cameraState.fogType.ordinal();

            skyColorR = ARGB.redFloat(skyColor);
            skyColorG = ARGB.greenFloat(skyColor);
            skyColorB = ARGB.blueFloat(skyColor);

            rawSunAngleDegrees = rawSunAngle;
            rawMoonAngleDegrees = rawMoonAngle;
            sunAngle = wrappedSunAngle / 360.0F;
            moonAngle = wrappedMoonAngle / 360.0F;
            shadowAngle = (day ? wrappedSunAngle : wrappedMoonAngle) / 360.0F;
            isDay = day;
            valid = true;
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            valid = false;

            fogColorR = 0.0F;
            fogColorG = 0.0F;
            fogColorB = 0.0F;
            fogColorA = 1.0F;

            fogStart = 0.0F;
            fogEnd = 0.0F;
            fogRenderDistanceStart = 0.0F;
            fogRenderDistanceEnd = 0.0F;
            fogSkyEnd = 0.0F;
            fogCloudEnd = 0.0F;
            fogKind = 0.0F;

            skyColorR = 0.0F;
            skyColorG = 0.0F;
            skyColorB = 0.0F;

            rawSunAngleDegrees = 0.0F;
            rawMoonAngleDegrees = 180.0F;
            sunAngle = 0.0F;
            moonAngle = 0.5F;
            shadowAngle = 0.0F;
            isDay = true;
        }
    }

    public static Snapshot snapshot() {
        synchronized (LOCK) {
            return new Snapshot(
                    valid,
                    fogColorR,
                    fogColorG,
                    fogColorB,
                    fogColorA,
                    fogStart,
                    fogEnd,
                    fogRenderDistanceStart,
                    fogRenderDistanceEnd,
                    fogSkyEnd,
                    fogCloudEnd,
                    fogKind,
                    skyColorR,
                    skyColorG,
                    skyColorB,
                    rawSunAngleDegrees,
                    rawMoonAngleDegrees,
                    sunAngle,
                    moonAngle,
                    shadowAngle,
                    isDay
            );
        }
    }

    private static float wrapDegrees(float value) {
        float wrapped = value % 360.0F;
        if (wrapped < 0.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    public record Snapshot(
            boolean valid,
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
            float rawSunAngleDegrees,
            float rawMoonAngleDegrees,
            float sunAngle,
            float moonAngle,
            float shadowAngle,
            boolean isDay
    ) {
    }
}
