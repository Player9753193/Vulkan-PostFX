#version 330

uniform sampler2D Sampler0;

in vec2 texCoord0;
out vec4 fragColor;

void main() {
#ifdef ALPHA_CUTOUT
    if (texture(Sampler0, texCoord0).a < ALPHA_CUTOUT) {
        discard;
    }
#endif
    fragColor = vec4(1.0);
}
