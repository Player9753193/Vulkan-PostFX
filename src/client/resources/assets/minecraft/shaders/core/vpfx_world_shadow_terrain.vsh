#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:chunksection.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler2;
layout(std140) uniform VpfxTerrainShadow {
    vec4 ShadowInfo;
    vec4 ShadowLightInfo;
    vec4 ShadowOriginInfo;
    mat4 ShadowViewProjMat;
};

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec4 shadowClipPos;
out float shadowSkyLight;
out float shadowBlockLight;
out float shadowReceiverFaceMask;

void main() {
    vec3 pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
    vec3 worldPos = Position + ChunkPosition;
    vec3 shadowRelativePos = worldPos - ShadowOriginInfo.xyz;
    shadowClipPos = ShadowViewProjMat * vec4(shadowRelativePos, 1.0);
    shadowSkyLight = clamp(float(UV2.y) / 240.0, 0.0, 1.0);
    shadowBlockLight = clamp(float(UV2.x) / 240.0, 0.0, 1.0);

    // Receiver classification must not be derived from player-screen derivatives.
    // Vanilla chunk vertex Color already contains face shading, which is stable in world/block space:
    // upward faces are bright, side/bottom faces are darker. Use it as a conservative top-face mask.
    float rawFaceShade = max(max(Color.r, Color.g), Color.b);
    shadowReceiverFaceMask = smoothstep(0.84, 0.98, rawFaceShade);
}
