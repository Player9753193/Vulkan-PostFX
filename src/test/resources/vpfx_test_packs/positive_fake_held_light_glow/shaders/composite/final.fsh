#version 150

uniform sampler2D InSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 color = texture(InSampler, texCoord);

    vec3 glowColor = vpfx_applyHeldLightGlow(texCoord, color.rgb);

    // Add a very small global lift while held light is active so the effect is
    // visible in dark caves without washing out bright scenes.
    float active = vpfx_hasHeldLight() ? 1.0 : 0.0;
    vec3 lifted = mix(glowColor, max(glowColor, glowColor + vpfx_HeldLightColor * 0.025), active);

    fragColor = vec4(clamp(lifted, 0.0, 1.0), color.a);
}
