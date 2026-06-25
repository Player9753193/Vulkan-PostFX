#version 150

uniform sampler2D ColoredLightVolumeSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 atlas = texture(ColoredLightVolumeSampler, texCoord);

    // Make low-energy atlas cells easier to see without hiding the alpha channel.
    vec3 color = pow(clamp(atlas.rgb, 0.0, 1.0), vec3(0.55));
    float gridX = step(0.985, fract(texCoord.x * 4.0));
    float gridY = step(0.985, fract(texCoord.y * 4.0));
    float grid = max(gridX, gridY) * 0.18;

    fragColor = vec4(max(color, vec3(grid)), 1.0);
}
