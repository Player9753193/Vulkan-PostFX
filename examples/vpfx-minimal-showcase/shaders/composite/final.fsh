#version 150

uniform sampler2D InSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 color = texture(InSampler, texCoord);

    float contrast = 1.06;
    vec3 contrasted = (color.rgb - 0.5) * contrast + 0.5;

    vec3 warm = contrasted * vec3(1.04, 0.98, 0.92);

    float vignette = 1.0 - smoothstep(0.48, 1.32, length(texCoord - 0.5) * 1.38);

    fragColor = vec4(warm * vignette, color.a);
}
