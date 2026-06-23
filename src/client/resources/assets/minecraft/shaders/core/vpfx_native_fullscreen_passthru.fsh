#version 330

uniform sampler2D Sampler0;

in vec2 v_texCoord;

out vec4 fragColor;

void main() {
    fragColor = texture(Sampler0, v_texCoord);
}
