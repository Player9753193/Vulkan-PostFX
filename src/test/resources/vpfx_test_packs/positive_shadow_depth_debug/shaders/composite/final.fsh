#version 150

uniform sampler2D ShadowDepthSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float rawDepth = texture(ShadowDepthSampler, texCoord).r;

    // Shadow targets are cleared and populated from light space, so the raw range
    // can be hard to inspect. Apply a contrast curve for diagnostics only.
    float visual = pow(clamp(rawDepth, 0.0, 1.0), 0.45);
    fragColor = vec4(vec3(visual), 1.0);
}
