# VulkanPostFX

> ⚠️ **EXPERIMENTAL — ALPHA** — Not yet stable for production use.

An experimental **external shader pack loader** and **post-processing/lighting experiment framework** for modern Minecraft Vulkan rendering path (Minecraft 26.2 snapshots / Fabric).

**Current execution backend**: `minecraft_postchain` — Minecraft PostChain backend (transitional).  
**VPFX native runtime**: Not yet complete.  
**VPFX v1 pack format**: Alpha spec.  

---

## Current Status

| Feature | Status |
|---------|--------|
| VPFX native pack loading | ✅ v1 alpha |
| Pack validation (manifest + graph) | ✅ v1 alpha |
| Runtime resource pack materialization | ✅ v1 alpha |
| Runtime backend abstraction (`VpfxRuntimeBackend`) | ✅ v1 alpha |
| External post chain execution | ✅ |
| World shadow pipeline (depth pass) | ⚠️ Experimental |
| Scene depth capture | ✅ |
| Debug HUD | ✅ |
| Compute shaders | ❌ Unsupported |
| Native VPFX runtime (non-PostChain) | ⚠️ v0 skeleton (experimental) |
| VPFX v2 runtime roadmap | [docs/vpfx-v2-runtime-roadmap.md](docs/vpfx-v2-runtime-roadmap.md) |

---

## Documentation Index

| Document | Description |
|----------|-------------|
| [docs/vpfx-pack-format-v1.md](docs/vpfx-pack-format-v1.md) | Complete VPFX v1 alpha format specification |
| [docs/vpfx-testing.md](docs/vpfx-testing.md) | Testing guide: smoke tests, error codes, adding test packs |
| [docs/vpfx-authoring-minimal-pack.md](docs/vpfx-authoring-minimal-pack.md) | Step-by-step guide to creating a minimal VPFX pack |
| [docs/known-issues.md](docs/known-issues.md) | Known limitations and gotchas in VPFX v1 alpha |
| [docs/vpfx-troubleshooting.md](docs/vpfx-troubleshooting.md) | Troubleshooting common VPFX pack load failures |
| [docs/vpfx-native-runtime-v0.md](docs/vpfx-native-runtime-v0.md) | VPFX Native Runtime v0 — architecture & skeleton (experimental) |
| [docs/vpfx-native-fullscreen-pass-design.md](docs/vpfx-native-fullscreen-pass-design.md) | NR-1 — Native fullscreen passthrough pass design (single pass, scene_color→main) |
| [docs/vpfx-v2-runtime-roadmap.md](docs/vpfx-v2-runtime-roadmap.md) | VPFX v2 runtime long-term roadmap (Iris-like capability goals) |
| [examples/vpfx-minimal-showcase/](examples/vpfx-minimal-showcase/) | Official minimal showcase example pack |

---

## VPFX v1 Alpha

VPFX v1 defines a **shader pack format** that is backend-agnostic. The current alpha execution backend is `minecraft_postchain` (`VpfxPostChainBackend`). When the native VPFX runtime arrives, pack authors will **not** need to update their graph JSON — only the execution backend will change.

### Key v1 Rules
- Use `format_version: 1` in pack.json
- Shader references: `<pack_id>:<path>` — maps to `shaders/<path>.vsh` and `shaders/<path>.fsh`
- **No `post/` prefix required** for shader paths
- `compute` capability must be `false` (unsupported in alpha)
- `shadow_depth` is experimental — visual issues possible
- Always output to `minecraft:main` from at least one pass
- Do not use `..` in shader paths

---

## Quick Start

### 1. Place a shader pack
```
run/shaderpacks/
```

### 2. Select the active pack
Edit `run/config/vulkanpostfx.json`:
```json
{ "active_pack_id": "vpfx_showcase" }
```

### 3. Launch
```bash
./gradlew runClient
```

### 4. Toggle effects
- `F8` — Toggle vanilla / shader effect
- `F9` — Toggle shadow depth debug view

---

## Architecture

```
[shaderpacks/*.zip]
    │
    ▼
[VpfxNativeZipPackLoader]  ← VPFX native pack parser
    │
    ▼
[VpfxPackManifestParser + VpfxGraphParser + VpfxGraphValidator]
    │
    ▼
[ShaderPackContainer]
    │
    ▼
[VpfxRuntimeBackend]  ← Backend abstraction layer
    │
    ├── [VpfxPostChainBackend]  ← Default backend: delegates to ZipPackMaterializer
    │
    └── [VpfxNativeRuntimeBackend]  ← v0 skeleton (experimental, opt-in via -D flag)
    │
    ▼
[Minecraft PostChain execution]
```

---

## Backend

### Default (minecraft_postchain)

| Property | Value |
|----------|-------|
| Backend ID | `minecraft_postchain` |
| Display Name | Minecraft PostChain Backend |
| Uses PostChain | ✅ |
| Native Runtime | ❌ Not yet implemented |
| Compute Support | ❌ |
| Shadow Depth | ⚠️ Experimental |
| Custom Targets | ✅ |

### Experimental (vpfx_native_v0)

| Property | Value |
|----------|-------|
| Backend ID | `vpfx_native_v0` |
| Display Name | VPFX Native Runtime v0 (skeleton) |
| Uses PostChain | ❌ (skeleton only) |
| Native Runtime | ✅ (experimental, dry-run) |
| Compute Support | ❌ |
| Shadow Depth | ❌ |
| Custom Targets | ❌ |
| Opt-in (dry-run) | `-Dvulkanpostfx.vpfx.nativeRuntime=true` |
| Opt-in (execute candidate) | `-Dvulkanpostfx.vpfx.nativeRuntime.execute=true` (logging-only, no real render) |
| Fallback | Always falls back to `minecraft_postchain` |

See [docs/vpfx-native-runtime-v0.md](docs/vpfx-native-runtime-v0.md) for details.

---

## Known Limitations

- **Minecraft 26.2 snapshot only** — no support for 1.21.x or earlier
- **Execution backend**: Minecraft PostChain (transitional). Native VPFX runtime not yet available.
- **Compute shaders**: Unsupported. Packs must set `compute: false`.
- **Shadow depth**: Experimental. May exhibit visual artifacts.
- **Not Iris/Sodium-compatible**: VPFX uses its own pack format.
- **Alpha spec**: Schema may change between alpha releases.
- **No in-game error display**: Pack failures are logged but not shown on-screen.

Full list: [docs/known-issues.md](docs/known-issues.md)

---

## Development

### Test Suite
```bash
./gradlew build                      # Compile
./gradlew validateTestPacks          # Pack validation (12 cases)
./gradlew validateMaterialization    # Materialization check (8 checks)
```

### Test Coverage
- 11 negative test packs covering manifest, graph, shader, and texture errors
- 1 positive minimal pack verifying the full load → materialize pipeline
