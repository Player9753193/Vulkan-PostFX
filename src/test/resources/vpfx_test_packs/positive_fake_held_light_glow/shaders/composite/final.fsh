#version 150

uniform sampler2D InSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 color = texture(InSampler, texCoord);

    // v2: make the fake held light visibly change brightness, not only tint.
    // The implementation still remains strictly screen-space: no world light
    // propagation, no chunk/entity shader injection, no server-side state.
    vec3 result = vpfx_applyHeldLightGlow(texCoord, color.rgb);

    fragColor = vec4(clamp(result, 0.0, 1.0), color.a);
}
