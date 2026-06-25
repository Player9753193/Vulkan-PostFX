#version 150

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 color = texture(InSampler, texCoord);
    float rawDepth = texture(DepthSampler, texCoord).r;

    // Reconstruct view-space distance through VPFX builtins. The include/injector
    // provides vpfx_ViewPositionFromRaw(...), vpfx_ZNear, vpfx_ZFar, and fog data.
    vec3 viewPos = vpfx_ViewPositionFromRaw(DepthSampler, texCoord);
    float viewDistance = length(viewPos);

    // Use Minecraft fog distances if available, but clamp to a useful range for
    // debug visibility. This makes the sample obvious without needing a UI slider.
    float fogStart = max(8.0, min(vpfx_FogStart, 48.0));
    float fogEnd = max(fogStart + 8.0, min(max(vpfx_FogEnd, 64.0), max(vpfx_ZFar, fogStart + 8.0)));
    float fogAmount = smoothstep(fogStart, fogEnd, viewDistance);

    // Sky pixels often sit at the far plane. Keep them fogged but not fully flat.
    if (rawDepth >= 0.9999) {
        fogAmount *= 0.55;
    }

    vec3 fogColor = mix(vec3(0.66, 0.72, 0.82), vpfx_SkyColor, 0.35);
    vec3 result = mix(color.rgb, fogColor, clamp(fogAmount * 0.72, 0.0, 0.82));
    fragColor = vec4(result, color.a);
}
