package com.ionhex975.vulkanpostfx.client.shader.uniform;

/**
 * VPFX builtin uniform 源码注入器。
 *
 * Builtin uniform injection:
 * - SceneDepth / Shadow Apply matrices
 * - held-light screen-space glow inputs
 * - helper functions:
 *   - vpfx_ViewPositionFromRaw(...)
 *   - vpfx_WorldPositionFromRaw(...)
 *   - vpfx_hasHeldLight()
 *   - vpfx_applyHeldLightGlow(...)
 */
public final class VpfxBuiltinUniformSourceInjector {
    private static final String BLOCK = """
#ifndef VPFX_BUILTIN_UNIFORMS
#define VPFX_BUILTIN_UNIFORMS

layout(std140) uniform VpfxBuiltins {
    vec4 vpfx_TimeInfo;
    vec4 vpfx_ViewInfo;
    vec4 vpfx_SceneInfo;
    vec4 vpfx_ShadowInfo;
    vec4 vpfx_FogColorInfo;
    vec4 vpfx_FogDistanceInfoA;
    vec4 vpfx_FogDistanceInfoB;
    vec4 vpfx_SkyColorInfo;
    vec4 vpfx_CelestialAngleInfo;
    vec4 vpfx_SunPositionInfo;
    vec4 vpfx_MoonPositionInfo;
    vec4 vpfx_ShadowLightPositionInfo;
    vec4 vpfx_UpPositionInfo;
    vec4 vpfx_HeldLightColorInfo;
    vec4 vpfx_HeldLightParamsInfo;
    mat4 gbufferProjection;
    mat4 vpfx_InverseProjectionMatrix;
    mat4 gbufferPreviousProjection;
    mat4 vpfx_PreviousInverseProjectionMatrix;
    mat4 gbufferModelView;
    mat4 vpfx_InverseViewRotationMatrix;
    mat4 gbufferPreviousModelView;
    mat4 vpfx_PreviousInverseViewRotationMatrix;
    mat4 vpfx_ViewProjectionMatrix;
    mat4 vpfx_InverseViewProjectionMatrix;
    mat4 shadowModelView;
    mat4 vpfx_InverseShadowViewMatrix;
    mat4 shadowProjection;
    mat4 vpfx_InverseShadowProjectionMatrix;
    mat4 vpfx_ShadowViewProjectionMatrix;
};

#define vpfx_Time           (vpfx_TimeInfo.x)
#define vpfx_DeltaTime      (vpfx_TimeInfo.y)
#define vpfx_GameTime       (vpfx_TimeInfo.z)
#define vpfx_FrameIndex     (vpfx_TimeInfo.w)

#define vpfx_CameraPos      (vpfx_ViewInfo.xyz)
#define vpfx_RainStrength   (vpfx_ViewInfo.w)

#define vpfx_ViewSize       (vpfx_SceneInfo.xy)
#define vpfx_InvViewSize    (vpfx_SceneInfo.zw)

#define vpfx_ZNear          (vpfx_ShadowInfo.x)
#define vpfx_ZFar           (vpfx_ShadowInfo.y)
#define vpfx_ShadowMapSize  (vpfx_ShadowInfo.z)
#define vpfx_ShadowBias     (vpfx_ShadowInfo.w)

#define vpfx_FogColor       (vpfx_FogColorInfo)
#define vpfx_FogStart       (vpfx_FogDistanceInfoA.x)
#define vpfx_FogEnd         (vpfx_FogDistanceInfoA.y)
#define vpfx_SkyFogEnd      (vpfx_FogDistanceInfoB.x)
#define vpfx_CloudFogEnd    (vpfx_FogDistanceInfoB.y)
#define vpfx_FogKind        (vpfx_FogDistanceInfoB.z)

#define vpfx_SkyColor       (vpfx_SkyColorInfo.rgb)
#define vpfx_IsDay          (vpfx_SkyColorInfo.w > 0.5)

#define vpfx_SunAngle       (vpfx_CelestialAngleInfo.x)
#define vpfx_MoonAngle      (vpfx_CelestialAngleInfo.y)
#define vpfx_ShadowAngle    (vpfx_CelestialAngleInfo.z)

#define vpfx_SunPosition            (vpfx_SunPositionInfo.xyz)
#define vpfx_MoonPosition           (vpfx_MoonPositionInfo.xyz)
#define vpfx_ShadowLightPosition    (vpfx_ShadowLightPositionInfo.xyz)
#define vpfx_UpPosition             (vpfx_UpPositionInfo.xyz)

#define vpfx_HeldLightColor         (vpfx_HeldLightColorInfo.rgb)
#define vpfx_HeldLightIntensity     (vpfx_HeldLightColorInfo.a)
#define vpfx_HeldLightRadius        (vpfx_HeldLightParamsInfo.x)
#define vpfx_HeldLightEnabled       (vpfx_HeldLightParamsInfo.y > 0.5)

#define vpfx_ProjectionMatrix            (gbufferProjection)
#define vpfx_PreviousProjectionMatrix    (gbufferPreviousProjection)
#define vpfx_ViewRotationMatrix          (gbufferModelView)
#define vpfx_PreviousViewRotationMatrix  (gbufferPreviousModelView)
#define vpfx_ShadowViewMatrix            (shadowModelView)
#define vpfx_ShadowProjectionMatrix      (shadowProjection)

// Iris-style aliases:
// - gbufferModelView 在 post 阶段按 camera-relative 旋转矩阵提供，不含世界平移
// - shadow* 与当前 shadow pass 实际使用矩阵保持一致
#define gbufferProjectionInverse         (vpfx_InverseProjectionMatrix)
#define gbufferPreviousProjectionInverse (vpfx_PreviousInverseProjectionMatrix)
#define gbufferModelViewInverse          (vpfx_InverseViewRotationMatrix)
#define gbufferPreviousModelViewInverse  (vpfx_PreviousInverseViewRotationMatrix)
#define shadowModelViewInverse           (vpfx_InverseShadowViewMatrix)
#define shadowProjectionInverse          (vpfx_InverseShadowProjectionMatrix)
#define shadowViewProjection             (vpfx_ShadowViewProjectionMatrix)
#define fogColor                         (vpfx_FogColor.rgb)
#define fogStart                         (vpfx_FogStart)
#define fogEnd                           (vpfx_FogEnd)
#define skyColor                         (vpfx_SkyColor)
#define sunAngle                         (vpfx_SunAngle)
#define moonAngle                        (vpfx_MoonAngle)
#define shadowAngle                      (vpfx_ShadowAngle)
#define sunPosition                      (vpfx_SunPosition)
#define moonPosition                     (vpfx_MoonPosition)
#define shadowLightPosition              (vpfx_ShadowLightPosition)
#define upPosition                       (vpfx_UpPosition)

bool vpfx_hasHeldLight() {
    return vpfx_HeldLightEnabled && vpfx_HeldLightIntensity > 0.001;
}

float vpfx_HeldLightRadialMask(vec2 uv, float innerRadius, float outerRadius) {
    vec2 p = uv * 2.0 - 1.0;
    p.x *= vpfx_ViewSize.x * vpfx_InvViewSize.y;
    return 1.0 - smoothstep(innerRadius, outerRadius, length(p));
}

vec3 vpfx_applyHeldLightGlow(vec2 uv, vec3 color) {
    if (!vpfx_hasHeldLight()) {
        return color;
    }

    float radius = max(vpfx_HeldLightRadius, 0.001);
    float glow = vpfx_HeldLightRadialMask(uv, 0.05 * radius, 1.35 * radius);
    glow *= vpfx_HeldLightIntensity;

    vec3 lightColor = vpfx_HeldLightColor;
    float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
    float darkBoost = 1.0 - smoothstep(0.18, 0.82, luminance);

    return color + lightColor * glow * mix(0.045, 0.18, darkBoost);
}

float vpfx_InternalSafeSignedDenominator(float value) {
    if (abs(value) > 1e-6) {
        return value;
    }
    return value < 0.0 ? -1e-6 : 1e-6;
}

/**
 * 从 raw scene depth 重建 view-space position。
 *
 * 当前按 Vulkan / zero-to-one depth 路线处理。
 * 如果后续发现 y 方向需要翻转，只改这里就行。
 */
vec3 vpfx_ViewPositionFromRaw(sampler2D depthSampler, vec2 uv) {
    float rawDepth = texture(depthSampler, uv).r;

    vec2 ndcUv = uv;

    vec4 clip = vec4(
        ndcUv * 2.0 - 1.0,
        rawDepth,
        1.0
    );

    vec4 view = vpfx_InverseProjectionMatrix * clip;
    return view.xyz / vpfx_InternalSafeSignedDenominator(view.w);
}

/**
 * 从 raw scene depth 重建 world-space position。
 *
 * 注意：
 * 这里的 vpfx_InverseViewRotationMatrix 只做方向旋转，
 * 平移单独通过 vpfx_CameraPos 补回。
 */
vec3 vpfx_WorldPositionFromRaw(sampler2D depthSampler, vec2 uv) {
    float rawDepth = texture(depthSampler, uv).r;

    vec2 ndcUv = uv;

    vec4 clip = vec4(
        ndcUv * 2.0 - 1.0,
        rawDepth,
        1.0
    );

    vec4 worldRelative = vpfx_InverseViewProjectionMatrix * clip;
    return worldRelative.xyz / vpfx_InternalSafeSignedDenominator(worldRelative.w) + vpfx_CameraPos;
}

#endif
""";

    private VpfxBuiltinUniformSourceInjector() {
    }

    public static String inject(String source) {
        if (source == null || source.isBlank()) {
            return source;
        }

        if (source.contains("VPFX_BUILTIN_UNIFORMS")
                || source.contains("layout(std140) uniform VpfxBuiltins")
                || source.contains("uniform VpfxBuiltins")) {
            return source;
        }

        int versionIndex = source.indexOf("#version");
        if (versionIndex < 0) {
            return BLOCK + "\n" + source;
        }

        int lineEnd = source.indexOf('\n', versionIndex);
        if (lineEnd < 0) {
            return source + "\n" + BLOCK + "\n";
        }

        return source.substring(0, lineEnd + 1)
                + "\n"
                + BLOCK
                + "\n"
                + source.substring(lineEnd + 1);
    }
}
