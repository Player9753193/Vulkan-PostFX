# VPFX v2 Runtime Roadmap

> **Target capability level**: Iris-like shader runtime  
> **Pack format**: VPFX native format (not Iris `.properties` / `.fsh`/`.vsh` compat)  
> **Disclaimer**: This roadmap describes capability goals, not timeline commitments. Order may change based on development priorities.

---

## Goal

VPFX aims to reach **Iris-like capability level** as a Minecraft Vulkan shader runtime:

- Full multi-pass render graph execution
- World geometry pass with custom shaders
- Shadow depth pass with custom shaders
- Composite/post-processing pass
- Final output pass
- GBuffer-like intermediate resources
- History buffer support (motion vectors, temporal effects)
- Scene depth and shadow depth as first-class graph resources
- Custom render targets at arbitrary resolutions
- Volumetric lighting pass
- Cloud rendering pass
- RT-lite (hardware-accelerated ray tracing, limited scope)

**VPFX does NOT aim for direct Iris shader pack compatibility.** The goal is capability parity, not format cloning. Shader authors will need to port to VPFX's native graph format.

---

## Phase Roadmap

### Phase 1: Native Runtime Foundation (v0 — Current)

- Render graph IR (`VpfxRuntimeGraph`, `VpfxRuntimePass`, `VpfxResourceRef`, `VpfxPassType`)
- v0 support checker (single fullscreen pass only)
- Backend selector with fallback to `minecraft_postchain`
- Dry-run capability check
- Architecture documentation

### Phase 2: Fullscreen Pass Execution (v1)

- Real GPU rendering of `FULLSCREEN` passes
- Vulkan pipeline creation for fullscreen quads
- Sampler and uniform binding
- `minecraft:scene_color` as real input
- `minecraft:main` as real output
- Replace PostChain for single-pass graphs
- Performance parity with PostChain backend

### Phase 3: Multi-Pass Graph (v1.x)

- Multi-pass execution in correct dependency order
- Internal target allocation and lifetime management
- Read/write barriers between passes
- Pass dependency resolution
- Custom target scaling support

### Phase 4: World Pass (v2)

- `WORLD` pass type execution
- Custom vertex/fragment shaders for world geometry
- GBuffer-like output targets (albedo, normal, specular, depth)
- Deferred rendering model
- Matrix/uniform injection for world-space transforms

### Phase 5: Shadow Pass (v2.x)

- `SHADOW` pass type execution
- Shadow map rendering with custom depth shaders
- `minecraft:shadow_depth` as graph resource
- Cascaded shadow maps
- Shadow map sampling in composite passes

### Phase 6: Composite & Final (v2.x)

- `COMPOSITE` pass type execution
- `FINAL` pass type execution
- Deferred lighting composition
- Tone mapping
- Bloom, DOF, motion blur

### Phase 7: Advanced Resources (v3)

- History buffers: previous frame color, depth, motion vectors
- Temporal anti-aliasing (TAA)
- Screen-space reflections (SSR)
- Screen-space ambient occlusion (SSAO)

### Phase 8: Atmosphere & Effects (v3.x)

- `CLOUD` pass type execution
- Volumetric lighting pass
- God rays / crepuscular rays
- Atmospheric scattering

### Phase 9: RT-lite (v3.x)

- Hardware-accelerated ray tracing (Vulkan `VK_KHR_ray_tracing_pipeline`)
- RT shadows
- RT reflections
- RT global illumination
- Limited scope: not full path tracing

### Phase 10: Native Runtime as Default (v4+)

- `VpfxNativeRuntimeBackend` becomes the default backend
- `VpfxPostChainBackend` becomes explicit fallback only
- PostChain dependency removal (optional)
- Full pipeline parity with PostChain path

---

## Resource Model Expansion

### Current (v0)
```
minecraft:scene_color  →  FULLSCREEN pass  →  minecraft:main
```

### Future (v2+)
```
                         ┌──────────────────┐
                         │   WORLD PASS      │
                         │  (custom shaders) │
                         └───────┬──────────┘
                                 │
               ┌─────────────────┼─────────────────┐
               ▼                 ▼                  ▼
        gbuffer:albedo    gbuffer:normal    gbuffer:depth
               │                 │                  │
               └─────────────────┼──────────────────┘
                                 │
                                 ▼
                         ┌──────────────────┐
                         │  SHADOW PASS      │
                         │  (depth render)   │
                         └───────┬──────────┘
                                 │
                                 ▼
                        minecraft:shadow_depth
                                 │
               ┌─────────────────┼──────────────────┐
               ▼                 ▼                  ▼
        scene_color        gbuffer:*         shadow_depth
               │                 │                  │
               └─────────────────┼──────────────────┘
                                 │
                                 ▼
                         ┌──────────────────┐
                         │  COMPOSITE PASS   │
                         │  (deferred light) │
                         └───────┬──────────┘
                                 │
                                 ▼
                          composite:color
                                 │
                                 ▼
                         ┌──────────────────┐
                         │   FINAL PASS      │
                         │  (tonemap/bloom)  │
                         └───────┬──────────┘
                                 │
                                 ▼
                           minecraft:main
```

---

## Non-Goals

- ❌ Direct Iris shader pack compatibility (`.properties` parser, Iris uniform binding)
- ❌ OptiFine shader format support
- ❌ Sodium compatibility layer
- ❌ Shader pack marketplace / distribution
- ❌ Full path tracing (beyond RT-lite scope)
- ❌ OpenGL backend support (Vulkan-only)
- ❌ 1:1 Iris feature map

---

## Stability Policy

| Phase | Status | Breaking Changes |
|-------|--------|-----------------|
| v0 | Skeleton / Dry-run | Expected |
| v1 | Fullscreen execution | Expected during v1.x |
| v2-v3 | Multi-pass, World, Shadow | Backward-compat for graph format |
| v4+ | Default backend | VPFX v2 format stabilized |

The VPFX v1 alpha pack format will be superseded by a v2 format when native runtime capabilities (world pass, gbuffer, history) require additional schema fields. Until then, v1 format is the canonical schema.

---

## Relevant Documents

- [docs/vpfx-native-runtime-v0.md](vpfx-native-runtime-v0.md) — Native Runtime v0 architecture and support checker
- [docs/vpfx-pack-format-v1.md](vpfx-pack-format-v1.md) — VPFX v1 alpha pack format specification
- [docs/known-issues.md](known-issues.md) — Current known limitations
