#version 150

uniform sampler2D InSampler;

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
};

in vec2 texCoord;
out vec4 fragColor;

float heldLightRadialMask(vec2 uv, float radius) {
    vec2 p = uv * 2.0 - 1.0;
    p.x *= max(vpfx_SceneInfo.x, 1.0) / max(vpfx_SceneInfo.y, 1.0);
    return 1.0 - smoothstep(0.05 * radius, 1.35 * radius, length(p));
}

void main() {
    vec4 color = texture(InSampler, texCoord);

    vec3 heldColor = vpfx_HeldLightColorInfo.rgb;
    float intensity = vpfx_HeldLightColorInfo.a;
    float radius = max(vpfx_HeldLightParamsInfo.x, 0.001);
    float enabled = vpfx_HeldLightParamsInfo.y;

    if (enabled < 0.5 || intensity <= 0.001) {
        fragColor = color;
        return;
    }

    float glow = heldLightRadialMask(texCoord, radius) * intensity;
    float luma = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
    float darkBoost = 1.0 - smoothstep(0.18, 0.82, luma);
    vec3 result = color.rgb + heldColor * glow * mix(0.045, 0.18, darkBoost);
    result = max(result, result + heldColor * 0.025);

    fragColor = vec4(clamp(result, 0.0, 1.0), color.a);
}
