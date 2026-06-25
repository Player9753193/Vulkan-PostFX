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
};

// Keep this standalone shader on the same 13-vec4 VpfxBuiltins layout as native framegraph.
// Held-light data is packed into spare .w components instead of adding new UBO fields.
#define vpfx_HeldLightColor     (vec3(vpfx_SunPositionInfo.w, vpfx_MoonPositionInfo.w, vpfx_ShadowLightPositionInfo.w))
#define vpfx_HeldLightIntensity (vpfx_UpPositionInfo.w)
#define vpfx_HeldLightRadius    (vpfx_FogDistanceInfoB.w)
#define vpfx_HeldLightEnabled   (vpfx_CelestialAngleInfo.w > 0.5)

in vec2 texCoord;
out vec4 fragColor;

bool vpfx_hasHeldLightStandalone() {
    return vpfx_HeldLightEnabled && vpfx_HeldLightIntensity > 0.001;
}

float heldLightRadialMask(vec2 uv, float radius) {
    vec2 center = vec2(0.68, 0.72);
    float aspect = max(vpfx_SceneInfo.x, 1.0) / max(vpfx_SceneInfo.y, 1.0);

    vec2 p = uv;
    vec2 c = center;
    p.x *= aspect;
    c.x *= aspect;

    float dist = distance(p, c);
    float outerRadius = max(0.42, radius * 0.95);
    return 1.0 - smoothstep(0.04, outerRadius, dist);
}

vec3 applyHeldLightGlowStandalone(vec2 uv, vec3 color) {
    if (!vpfx_hasHeldLightStandalone()) {
        return color;
    }

    vec3 lightColor = vpfx_HeldLightColor;
    float intensity = clamp(vpfx_HeldLightIntensity, 0.0, 4.0);
    float radius = max(vpfx_HeldLightRadius, 0.001);

    float radial = heldLightRadialMask(uv, radius);
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    float darkMask = 1.0 - smoothstep(0.08, 0.86, luma);
    darkMask = pow(clamp(darkMask, 0.0, 1.0), 0.70);

    float localStrength = radial * intensity;
    float ambientStrength = darkMask * intensity;

    vec3 additive = lightColor * (
            localStrength * (0.10 + 0.62 * darkMask)
          + ambientStrength * 0.16
    );

    vec3 lifted = color + additive;
    float exposureAmount = clamp((localStrength * darkMask * 0.42) + (ambientStrength * 0.12), 0.0, 0.70);
    vec3 exposed = vec3(1.0) - exp(-lifted * 1.35);
    lifted = mix(lifted, exposed, exposureAmount);

    float tintAmount = clamp((localStrength + ambientStrength) * 0.10, 0.0, 0.22);
    lifted = mix(lifted, lifted * mix(vec3(1.0), lightColor, 0.25), tintAmount);

    return clamp(lifted, 0.0, 1.0);
}

void main() {
    vec4 color = texture(InSampler, texCoord);
    fragColor = vec4(applyHeldLightGlowStandalone(texCoord, color.rgb), color.a);
}
