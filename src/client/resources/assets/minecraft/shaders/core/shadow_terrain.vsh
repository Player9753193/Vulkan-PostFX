#version 330

#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:chunksection.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

layout(std140) uniform VpfxTerrainShadow {
    vec4 ShadowInfo;
    vec4 ShadowLightInfo;
    vec4 ShadowOriginInfo;
    mat4 ShadowViewProjMat;
};

out vec2 texCoord0;

void main() {
    // Shadow caster coordinates are anchored to VPFX's own stable shadow origin.
    // Do not use main-render CameraBlockPos/CameraOffset here: those are player-view globals.
    vec3 worldPos = Position + ChunkPosition;
    vec3 pos = worldPos - ShadowOriginInfo.xyz;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    texCoord0 = UV0;
}
