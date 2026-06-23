package com.ionhex975.vulkanpostfx.client.shadow;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Iris-style shadow matrix construction adapted to the current runtime.
 *
 * 核心点：
 * 1. shadow terrain 顶点按 VPFX stable shadow origin relative 语义进入 shader；
 * 2. shadow modelview 只包含主光源方向旋转，不使用玩家视角方向；
 * 3. 阴影覆盖区域可以跟随玩家位置，但不能依赖主相机 CameraBlockPos/CameraOffset。
 */
public final class ShadowMatricesLite {
    public static final float NEAR = -100.05F;
    public static final float FAR = 156.0F;
    private static final float MIN_HALF_PLANE = 64.0F;
    private static final float MAX_HALF_PLANE = 512.0F;

    private ShadowMatricesLite() {
    }

    public static Vector3f createShadowLightDirection(float celestialAngleDegrees, float sunPathRotationDegrees) {
        Matrix4f celestial = new Matrix4f()
                .identity()
                .rotateY((float) Math.toRadians(-90.0F))
                .rotateZ((float) Math.toRadians(sunPathRotationDegrees))
                .rotateX((float) Math.toRadians(celestialAngleDegrees));
        Vector3f dir = new Vector3f(0.0F, 100.0F, 0.0F);
        celestial.transformDirection(dir);
        return dir.normalize();
    }

    public static Vector3f createShadowLightDirectionFromHorizontalAngle(
            float horizontalAngleDegrees,
            float sunPathRotationDegrees
    ) {
        float angle = (float) Math.toRadians(horizontalAngleDegrees);
        Vector3f direction = new Vector3f(
                (float) Math.cos(angle),
                (float) Math.sin(angle),
                0.0F
        );

        if (Math.abs(direction.x) < 1.0E-4F) {
            direction.x = 0.0F;
        }
        if (Math.abs(direction.y) < 1.0E-4F) {
            direction.y = 0.0F;
        }

        direction.rotateY((float) Math.toRadians(sunPathRotationDegrees));
        return direction.normalize();
    }

    private static float resolveSkyAngle(float shadowAngle) {
        if (shadowAngle < 0.25F) {
            return shadowAngle + 0.75F;
        }

        return shadowAngle - 0.25F;
    }

    private static void snapModelViewToGrid(
            Matrix4f target,
            float shadowIntervalSize,
            double cameraX,
            double cameraY,
            double cameraZ
    ) {
        if (Math.abs(shadowIntervalSize) == 0.0F) {
            return;
        }

        float offsetX = (float) cameraX % shadowIntervalSize;
        float offsetY = (float) cameraY % shadowIntervalSize;
        float offsetZ = (float) cameraZ % shadowIntervalSize;

        float halfIntervalSize = shadowIntervalSize / 2.0F;
        offsetX -= halfIntervalSize;
        offsetY -= halfIntervalSize;
        offsetZ -= halfIntervalSize;

        target.translate(offsetX, offsetY, offsetZ);
    }

    public static Matrix4f createOrthoMatrix(float halfPlaneLength, float nearPlane, float farPlane) {
        float h = Math.max(halfPlaneLength, MIN_HALF_PLANE);
        h = Math.min(h, MAX_HALF_PLANE);

        /*
         * Minecraft 26.2 uses reversed-Z: depth clears to 0 and terrain pipelines test GREATER.
         * Match Projection#getMatrix by swapping near/far before building the GPU projection,
         * otherwise farther underground geometry can win the shadow depth test over the surface.
         */
        return new Matrix4f().setOrthoSymmetric(
                h * 2.0F,
                h * 2.0F,
                farPlane,
                nearPlane,
                RenderSystem.getDevice().getDeviceInfo().isZZeroToOne()
        );
    }

    public static Matrix4f createModelViewMatrix(Vector3fc lightDirection) {
        return createLightRotationMatrix(lightDirection);
    }

    public static Vec3 createStableShadowOrigin(Vec3 focus, float shadowIntervalSize) {
        if (focus == null) {
            return Vec3.ZERO;
        }

        double grid = Math.max(Math.abs(shadowIntervalSize), 1.0 / 4096.0);
        return new Vec3(
                snapToGrid(focus.x, grid),
                snapToGrid(focus.y, grid),
                snapToGrid(focus.z, grid)
        );
    }

    private static double snapToGrid(double value, double grid) {
        if (!(grid > 0.0) || !Double.isFinite(value)) {
            return value;
        }
        return Math.floor(value / grid) * grid;
    }

    private static Matrix4f createLightRotationMatrix(Vector3fc lightDirection) {
        Vector3f directionToLight = new Vector3f(lightDirection);
        if (directionToLight.lengthSquared() < 1.0E-6F) {
            directionToLight.set(0.0F, 1.0F, 0.0F);
        } else {
            directionToLight.normalize();
        }

        Vector3f up = Math.abs(directionToLight.y) > 0.95F
                ? new Vector3f(0.0F, 0.0F, 1.0F)
                : new Vector3f(0.0F, 1.0F, 0.0F);

        return new Matrix4f().setLookAlong(directionToLight.negate(), up);
    }

    public static Matrix4f createModelViewMatrix(
            Vector3fc lightDirection,
            float shadowIntervalSize,
            double cameraX,
            double cameraY,
            double cameraZ
    ) {
        Matrix4f modelView = createLightRotationMatrix(lightDirection);
        snapModelViewToGrid(modelView, shadowIntervalSize, cameraX, cameraY, cameraZ);
        return modelView;
    }

    public static Matrix4f createModelViewMatrix(
            float shadowAngle,
            float shadowIntervalSize,
            float sunPathRotationDegrees,
            double cameraX,
            double cameraY,
            double cameraZ
    ) {
        Matrix4f modelView = new Matrix4f()
                .identity()
                .rotateX((float) Math.toRadians(90.0F))
                .rotateZ((float) Math.toRadians(resolveSkyAngle(shadowAngle) * -360.0F))
                .rotateX((float) Math.toRadians(sunPathRotationDegrees));
        snapModelViewToGrid(modelView, shadowIntervalSize, cameraX, cameraY, cameraZ);
        return modelView;
    }

    public static Matrix4f createViewProjectionMatrix(
            float halfPlaneLength,
            float shadowAngle,
            float shadowIntervalSize,
            float sunPathRotationDegrees,
            double cameraX,
            double cameraY,
            double cameraZ
    ) {
        Matrix4f projection = createOrthoMatrix(halfPlaneLength, NEAR, FAR);
        Matrix4f modelView = createModelViewMatrix(
                shadowAngle,
                shadowIntervalSize,
                sunPathRotationDegrees,
                cameraX,
                cameraY,
                cameraZ
        );
        return new Matrix4f(projection).mul(modelView);
    }
}
