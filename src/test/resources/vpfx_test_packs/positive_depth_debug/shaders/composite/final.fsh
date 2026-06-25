#version 150

uniform sampler2D DepthSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float rawDepth = texture(DepthSampler, texCoord).r;

    // Show raw depth with a slight contrast curve. This is intentionally simple:
    // if the scene-depth chain works, nearby geometry and distant geometry should
    // produce visible grayscale differences.
    float visual = pow(clamp(rawDepth, 0.0, 1.0), 0.35);
    fragColor = vec4(vec3(visual), 1.0);
}
