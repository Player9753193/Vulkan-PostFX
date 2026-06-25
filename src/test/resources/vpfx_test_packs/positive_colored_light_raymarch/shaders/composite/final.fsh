#version 150

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;
uniform sampler2D ColoredLightVolumeSampler;

in vec2 texCoord;
out vec4 fragColor;

float vpfx_hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float vpfx_safeDenominator(float value) {
    if (abs(value) > 1e-6) {
        return value;
    }
    return value < 0.0 ? -1e-6 : 1e-6;
}

vec3 vpfx_viewRayDirection(vec2 uv) {
    // Build a stable far-plane ray from UV instead of relying on raw scene depth.
    // Sky pixels often have depth=1.0 and can reconstruct to extreme values.
    vec4 clip = vec4(uv * 2.0 - 1.0, 1.0, 1.0);
    vec4 view = vpfx_InverseProjectionMatrix * clip;
    vec3 viewPos = view.xyz / vpfx_safeDenominator(view.w);
    float lenPos = length(viewPos);
    if (lenPos < 1e-5) {
        return vec3(0.0, 0.0, -1.0);
    }
    return viewPos / lenPos;
}

vec3 vpfx_worldRayDirection(vec2 uv) {
    vec3 viewDir = vpfx_viewRayDirection(uv);
    return normalize((vpfx_InverseViewRotationMatrix * vec4(viewDir, 0.0)).xyz);
}

vec3 vpfx_accumulateColoredVolume(vec2 uv, sampler2D depthSampler, sampler2D lightVolumeSampler) {
    float rawDepth = texture(depthSampler, uv).r;
    vec3 viewPos = vpfx_ViewPositionFromRaw(depthSampler, uv);
    float surfaceDistance = length(viewPos);

    // Sky pixels reconstruct at the far plane. For the v0 prototype, only march
    // through the local colored-light volume around the camera/player.
    float maxMarchDistance = rawDepth >= 0.9999
        ? 48.0
        : clamp(surfaceDistance, 1.0, 56.0);

    vec3 worldDir = vpfx_worldRayDirection(uv);
    vec3 start = vpfx_CameraPos + worldDir * 0.35;

    const int SAMPLE_COUNT = 24;
    float jitter = vpfx_hash12(uv * vpfx_ViewSize + vec2(vpfx_FrameIndex, vpfx_Time));
    vec3 accumulated = vec3(0.0);
    float totalWeight = 0.0;

    for (int i = 0; i < SAMPLE_COUNT; i++) {
        float rayT = (float(i) + jitter) / float(SAMPLE_COUNT);
        float dist = mix(0.5, maxMarchDistance, rayT);
        vec3 sampleWorld = start + worldDir * dist;

        vec3 light = vpfx_SampleColoredLightVolumeTrilinear(lightVolumeSampler, sampleWorld);

        // Artistic falloff: nearby colored fog should read strongly, far samples
        // should not wash the entire frame. This is deliberately not RTX/path tracing.
        float distanceFade = exp(-dist * 0.030);
        float segmentWeight = (1.0 - rayT * 0.65) * distanceFade;
        accumulated += light * segmentWeight;
        totalWeight += segmentWeight;
    }

    if (totalWeight <= 1e-5) {
        return vec3(0.0);
    }

    // Keep values in a perceptual range. The source atlas is compressed RGBA8,
    // so this is a visual light-density signal, not physical radiance.
    vec3 lightDensity = accumulated / totalWeight;
    return log(lightDensity + vec3(1.0)) * 0.85;
}

void main() {
    vec4 scene = texture(InSampler, texCoord);
    vec3 color = scene.rgb;

    vec3 coloredLight = vpfx_accumulateColoredVolume(texCoord, DepthSampler, ColoredLightVolumeSampler);

    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    float darkMask = 1.0 - smoothstep(0.18, 0.92, luma);
    float midMask = 1.0 - smoothstep(0.55, 1.0, luma);
    float lightStrength = clamp(0.30 + 0.95 * darkMask + 0.30 * midMask, 0.20, 1.35);

    vec3 lifted = color + coloredLight * lightStrength;

    // Mild exposure curve: makes torch/soul-light volumes show up in caves and
    // night scenes while avoiding full-screen white-out in already bright areas.
    vec3 exposed = vec3(1.0) - exp(-lifted * 1.20);
    float exposureMix = clamp(length(coloredLight) * (0.16 + 0.38 * darkMask), 0.0, 0.42);
    vec3 result = mix(lifted, exposed, exposureMix);

    // Preserve color identity without turning the whole frame into a flat tint.
    float chroma = clamp(length(coloredLight) * 0.18, 0.0, 0.28);
    vec3 tintColor = length(coloredLight) > 0.001 ? normalize(coloredLight + vec3(0.0001)) : vec3(1.0);
    result = mix(result, result * mix(vec3(1.0), tintColor, 0.20), chroma);

    fragColor = vec4(clamp(result, 0.0, 1.0), scene.a);
}
