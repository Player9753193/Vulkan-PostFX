#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:chunksection.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

layout(std140) uniform VpfxTerrainShadow {
    vec4 ShadowInfo;
    vec4 ShadowLightInfo;
    vec4 ShadowOriginInfo;
    mat4 ShadowViewProjMat;
};

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec4 shadowClipPos;
in float shadowSkyLight;
in float shadowBlockLight;
in float shadowReceiverFaceMask;

out vec4 fragColor;

vec4 sampleNearest(sampler2D source, vec2 uv, vec2 pixelSize, vec2 du, vec2 dv, vec2 texelScreenSize) {
    vec2 uvTexelCoords = uv / pixelSize;
    vec2 texelCenter = round(uvTexelCoords) - 0.5f;
    vec2 texelOffset = uvTexelCoords - texelCenter;
    texelOffset = (texelOffset - 0.5f) * pixelSize / texelScreenSize + 0.5f;
    texelOffset = clamp(texelOffset, 0.0f, 1.0f);
    uv = (texelCenter + texelOffset) * pixelSize;
    return textureGrad(source, uv, du, dv);
}

vec4 sampleNearest(sampler2D source, vec2 uv, vec2 pixelSize) {
    vec2 du = dFdx(uv);
    vec2 dv = dFdy(uv);
    vec2 texelScreenSize = sqrt(du * du + dv * dv);
    return sampleNearest(source, uv, pixelSize, du, dv, texelScreenSize);
}

vec4 sampleRGSS(sampler2D source, vec2 uv, vec2 pixelSize) {
    vec2 du = dFdx(uv);
    vec2 dv = dFdy(uv);
    vec2 texelScreenSize = sqrt(du * du + dv * dv);
    float maxTexelSize = max(texelScreenSize.x, texelScreenSize.y);
    float minPixelSize = min(pixelSize.x, pixelSize.y);
    float transitionStart = minPixelSize * 1.0;
    float transitionEnd = minPixelSize * 2.0;
    float blendFactor = smoothstep(transitionStart, transitionEnd, maxTexelSize);
    float duLength = length(du);
    float dvLength = length(dv);
    float minDerivative = min(duLength, dvLength);
    float maxDerivative = max(duLength, dvLength);
    float effectiveDerivative = sqrt(minDerivative * maxDerivative);
    float mipLevelExact = max(0.0, log2(effectiveDerivative / minPixelSize));
    float mipLevelLow = floor(mipLevelExact);
    float mipLevelHigh = mipLevelLow + 1.0;
    float mipBlend = fract(mipLevelExact);

    const vec2 offsets[4] = vec2[](
        vec2(0.125, 0.375),
        vec2(-0.125, -0.375),
        vec2(0.375, -0.125),
        vec2(-0.375, 0.125)
    );

    vec4 rgssColorLow = vec4(0.0);
    vec4 rgssColorHigh = vec4(0.0);
    for (int i = 0; i < 4; ++i) {
        vec2 sampleUV = uv + offsets[i] * pixelSize;
        rgssColorLow += textureLod(source, sampleUV, mipLevelLow);
        rgssColorHigh += textureLod(source, sampleUV, mipLevelHigh);
    }

    rgssColorLow *= 0.25;
    rgssColorHigh *= 0.25;

    vec4 rgssColor = mix(rgssColorLow, rgssColorHigh, mipBlend);
    vec4 nearestColor = sampleNearest(source, uv, pixelSize, du, dv, texelScreenSize);
    return mix(nearestColor, rgssColor, blendFactor);
}

float safeDenominator(float value) {
    if (abs(value) > 1e-6) {
        return value;
    }
    return value < 0.0 ? -1e-6 : 1e-6;
}

vec3 safeNormalize(vec3 value, vec3 fallback) {
    float lenSq = dot(value, value);
    if (lenSq > 1e-8) {
        return value * inversesqrt(lenSq);
    }
    return fallback;
}

float shadowEdgeFade(vec2 shadowUv) {
    vec2 edge = min(shadowUv, 1.0 - shadowUv);
    return smoothstep(0.0, 0.04, min(edge.x, edge.y));
}

vec3 resolveLightDirection() {
    return safeNormalize(ShadowLightInfo.xyz, vec3(0.0, -1.0, 0.0));
}

float resolveReceiverPlaneMask() {
    // Do not use dFdx/dFdy or any player-screen derivative to decide where terrain can
    // receive VPFX shadows. The mask comes from vanilla's block-face vertex shading,
    // which is stable in block/world space and removes the diagonal side-face artifacts.
    return clamp(shadowReceiverFaceMask, 0.0, 1.0);
}

float resolveReceiverBias() {
    float baseBias = max(ShadowInfo.x, 0.00075);

    // Keep the bias independent from the player's view. Screen-space depth derivatives caused
    // the shadow amount to change with view angle and amplified side-face triangle bands.
    vec3 lightDirection = resolveLightDirection();
    float grazingLight = 1.0 - smoothstep(0.08, 0.30, abs(lightDirection.y));
    return clamp(baseBias + grazingLight * 0.00035, 0.00075, 0.0028);
}

float resolveSolarMaskStability() {
    vec3 lightDirection = resolveLightDirection();
    float sunHeight = abs(lightDirection.y);
    return smoothstep(0.035, 0.16, sunHeight);
}

float sampleShadowBlocker(vec2 uv, float receiverDepth, float bias) {
    float storedDepth = texture(Sampler1, uv).r;

    // Reversed-Z shadow map: clear depth is 0.0, nearer casters write larger depth values.
    // A small dead zone is critical: without it, terrain receiving its own shadow depth produces
    // acne and diagonal triangle bands on block faces.
    float rayDepth = storedDepth - receiverDepth - bias;
    return smoothstep(0.00018, 0.00155, rayDepth);
}

float sampleSealedPcfShadow(vec2 shadowUv, float receiverDepth, float bias, vec2 texel) {
    // A compact Poisson PCF kernel gives smoother edges than the previous 3x3 grid. The additional
    // blockerMax term is intentional: Minecraft terrain is composed from many adjacent quads, and
    // a pure average PCF can leave bright 1-texel seams where two block faces meet in light space.
    const vec2 offsets[16] = vec2[](
        vec2( 0.00,  0.00),
        vec2( 0.55,  0.15),
        vec2(-0.48,  0.28),
        vec2( 0.22, -0.58),
        vec2(-0.32, -0.44),
        vec2( 1.05,  0.42),
        vec2(-0.92,  0.56),
        vec2( 0.70, -0.88),
        vec2(-0.76, -0.82),
        vec2( 1.48,  0.00),
        vec2(-1.48,  0.00),
        vec2( 0.00,  1.48),
        vec2( 0.00, -1.48),
        vec2( 1.18,  1.10),
        vec2(-1.18,  1.10),
        vec2( 1.18, -1.10)
    );

    float weighted = 0.0;
    float weightSum = 0.0;
    float blockerMax = 0.0;

    for (int i = 0; i < 16; ++i) {
        float radius = i < 5 ? 1.15 : 1.85;
        vec2 sampleUv = shadowUv + offsets[i] * texel * radius;
        float b = sampleShadowBlocker(sampleUv, receiverDepth, bias);
        float w = i == 0 ? 2.20 : (i < 5 ? 1.30 : 0.82);
        weighted += b * w;
        weightSum += w;
        blockerMax = max(blockerMax, b);
    }

    float pcf = weighted / max(weightSum, 1e-4);

    // Do not let a single noisy blocker sample dominate the final result. The old sealing factor
    // was useful for cracks, but it also amplified self-shadow noise into visible dark stripes.
    float contactConfidence = smoothstep(0.34, 0.78, pcf);
    float sealed = max(pcf, blockerMax * 0.20);
    return clamp(mix(pcf, sealed, contactConfidence * 0.32), 0.0, 1.0);
}

float sampleSolarRayMask() {
    if (ShadowInfo.w < 0.5) {
        return 0.0;
    }

    if (shadowSkyLight < 0.20) {
        return 0.0;
    }

    vec3 shadowNdc = shadowClipPos.xyz / safeDenominator(shadowClipPos.w);
    if (shadowNdc.z < 0.0 || shadowNdc.z > 1.0) {
        return 0.0;
    }

    vec2 shadowUv = shadowNdc.xy * 0.5 + 0.5;
    if (shadowUv.x < 0.0 || shadowUv.x > 1.0 || shadowUv.y < 0.0 || shadowUv.y > 1.0) {
        return 0.0;
    }

    vec2 texel = vec2(1.0 / max(ShadowInfo.y, 1.0));
    if (shadowUv.x < texel.x * 2.0 || shadowUv.x > 1.0 - texel.x * 2.0 || shadowUv.y < texel.y * 2.0 || shadowUv.y > 1.0 - texel.y * 2.0) {
        return 0.0;
    }

    float receiverPlaneMask = resolveReceiverPlaneMask();
    if (receiverPlaneMask <= 0.002) {
        return 0.0;
    }

    float bias = resolveReceiverBias();
    float mask = sampleSealedPcfShadow(shadowUv, shadowNdc.z, bias, texel);

    float skyReach = smoothstep(0.20, 0.86, shadowSkyLight);
    return clamp(mask * receiverPlaneMask * shadowEdgeFade(shadowUv) * skyReach * resolveSolarMaskStability(), 0.0, 1.0);
}

float resolveLocalLightCoverage() {
    float blockLight = clamp(shadowBlockLight, 0.0, 1.0);
    float spread = smoothstep(0.04, 0.82, blockLight);
    float sourceCore = smoothstep(0.58, 1.0, blockLight);
    return clamp(max(spread * 0.76, sourceCore), 0.0, 1.0);
}

float resolveIrisStyleAmbientFloor(float localLightCoverage) {
    float skyAmbient = mix(0.34, 0.68, clamp(shadowSkyLight, 0.0, 1.0));
    float blockAmbient = localLightCoverage * 0.90;
    return clamp(max(max(ShadowInfo.z, skyAmbient), blockAmbient), 0.0, 0.88);
}

void main() {
    vec4 color = (UseRgss == 1 ? sampleRGSS(Sampler0, texCoord0, 1.0f / TextureSize) : sampleNearest(Sampler0, texCoord0, 1.0f / TextureSize)) * vertexColor;
    color = mix(FogColor * vec4(1, 1, 1, color.a), color, ChunkVisibility);
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

    float solarMask = sampleSolarRayMask();
    float localLightCoverage = resolveLocalLightCoverage();
    float maskedAmount = solarMask * (1.0 - localLightCoverage * 0.92);
    float ambientFloor = resolveIrisStyleAmbientFloor(localLightCoverage);
    float blackAmount = clamp(maskedAmount * mix(0.30, 0.58, clamp(ShadowInfo.z, 0.0, 1.0)), 0.0, 0.82);
    vec3 unmaskedColor = color.rgb;
    color.rgb = max(mix(color.rgb, vec3(0.0), blackAmount), unmaskedColor * ambientFloor);
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
