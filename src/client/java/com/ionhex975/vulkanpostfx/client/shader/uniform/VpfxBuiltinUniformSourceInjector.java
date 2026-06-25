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

// Held-light values reuse spare .w lanes in the pre-existing 13-vec4 layout.
// Do not add new vec4 fields here unless all native and PostChain UBO paths are migrated together.
#define vpfx_HeldLightColor         (vec3(vpfx_SunPositionInfo.w, vpfx_MoonPositionInfo.w, vpfx_ShadowLightPositionInfo.w))
#define vpfx_HeldLightIntensity     (vpfx_UpPositionInfo.w)
#define vpfx_HeldLightRadius        (vpfx_FogDistanceInfoB.w)
#define vpfx_HeldLightEnabled       (vpfx_CelestialAngleInfo.w > 0.5)

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

float vpfx_HeldLightRadialMask(vec2 uv, float radius) {
    // The held item is rendered near the lower-right area of the screen, so the
    // fake light should originate from that direction rather than from the
    // exact screen center. This makes the effect read as a hand-held lamp.
    vec2 center = vec2(0.68, 0.72);

    float aspect = max(vpfx_ViewSize.x, 1.0) / max(vpfx_ViewSize.y, 1.0);
    vec2 p = uv;
    vec2 c = center;
    p.x *= aspect;
    c.x *= aspect;

    float dist = distance(p, c);
    float outerRadius = max(0.42, radius * 0.95);
    return 1.0 - smoothstep(0.04, outerRadius, dist);
}

vec3 vpfx_applyHeldLightGlow(vec2 uv, vec3 color) {
    if (!vpfx_hasHeldLight()) {
        return color;
    }

    vec3 lightColor = vpfx_HeldLightColor;
    float intensity = clamp(vpfx_HeldLightIntensity, 0.0, 4.0);
    float radius = max(vpfx_HeldLightRadius, 0.001);

    float radial = vpfx_HeldLightRadialMask(uv, radius);
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));

    // Keep bright vanilla-lit areas from washing out, while making unlit areas
    // visibly brighter. The old v1 shader mostly tinted the frame; this v2 path
    // actually lifts exposure in dark and mid-tone regions.
    float darkMask = 1.0 - smoothstep(0.08, 0.86, luma);
    darkMask = pow(clamp(darkMask, 0.0, 1.0), 0.70);

    float localStrength = radial * intensity;
    float ambientStrength = darkMask * intensity;

    // Add both a local cone-like lift from the hand and a softer ambient lift
    // so the held light remains visible even when the held item is partly off
    // screen. The values are intentionally artistic rather than physically exact.
    vec3 additive = lightColor * (
            localStrength * (0.10 + 0.62 * darkMask)
          + ambientStrength * 0.16
    );

    vec3 lifted = color + additive;

    // A mild exposure curve makes dark terrain brighten more naturally than a
    // plain linear add, while still preserving highlights through clamping.
    float exposureAmount = clamp((localStrength * darkMask * 0.42) + (ambientStrength * 0.12), 0.0, 0.70);
    vec3 exposed = vec3(1.0) - exp(-lifted * 1.35);
    lifted = mix(lifted, exposed, exposureAmount);

    // Keep a subtle color identity for soul/copper/redstone light sources.
    float tintAmount = clamp((localStrength + ambientStrength) * 0.10, 0.0, 0.22);
    lifted = mix(lifted, lifted * mix(vec3(1.0), lightColor, 0.25), tintAmount);

    return clamp(lifted, 0.0, 1.0);
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
