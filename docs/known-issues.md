# VPFX v1 Alpha — Known Issues

> Last updated: 2026-05-11
> VPFX version: 1.7.0-alpha

---

## Platform & Compatibility

### Minecraft 26.2 Snapshot Only
VPFX v1 targets **Minecraft 26.2-alpha.2 (snapshot)** exclusively. There is no support for Minecraft 1.21.x, 1.20.x, or earlier versions. The mod relies on the modern Vulkan rendering backend introduced in 26.2 snapshots.

### Not Iris-Compatible
VPFX v1 is **not** compatible with Iris or OptiFine shader packs. The VPFX pack format is its own schema (`pack.json` + VPFX graph JSON). Traditional shader pack structures (`shaders/gbuffers_*.fsh`, `composite*.fsh`, etc.) are not directly usable.

### Not Sodium-Compatible
VPFX v1 has not been tested with Sodium. Sodium compatibility is not a goal during alpha.

---

## Backend

### PostChain Backend (Transitional)
The current execution backend is **Minecraft's built-in PostChain** (`minecraft_postchain`). This is a transitional architecture:
- Graph passes are converted to PostChain JSON format at runtime
- Render targets are managed through Minecraft's `RenderTarget` / `FrameGraphBuilder`
- The native VPFX runtime (bypassing PostChain entirely) is not yet implemented

**Impact**: Performance is limited by PostChain overhead. Pass graph optimizations are not available. The `ZipPackMaterializer` must recreate the entire runtime resource pack directory on every reload.

### Native VPFX Runtime Not Complete
The `VpfxRuntimeBackend` interface exists to allow switching backends in the future, but only one implementation exists today: `VpfxPostChainBackend`. A native runtime would replace `ZipPackMaterializer` with a direct GPU pass graph executor.

---

## Capabilities

### Compute Shaders — Unsupported
`compute: false` is **required** in all VPFX v1 packs. The PostChain backend has no compute shader dispatch mechanism. Packs that set `compute: true` will be rejected by the validator (C005).

### Shadow Depth — Experimental
`shadow_depth: true` is available but experimental:
- The shadow depth pass (`ShadowDepthPassLite`) renders terrain into a dedicated shadow map target
- World shadow sampling (`ChunkSectionsToRenderWorldShadowMixin`) hooks into the OPAQUE terrain render path with a custom pipeline
- Visual artifacts may occur: shadow acne, peter-panning, edge bleeding, light leakage
- Shadow maps use a fixed 8192×8192 resolution with no cascaded shadow maps (CSM)

---

## Schema

### Alpha Spec — Breaking Changes Possible
VPFX v1 is an **alpha spec**. While the core schema (`pack.json`, graph JSON, shader path mapping) is stable enough for pack authoring, the following may change before the v1 stable release:
- New required fields in `pack.json`
- New capability flags
- Validation rule additions or refinements
- Backend-specific metadata fields

Pack authors should expect to update their packs when upgrading between VPFX alpha releases.

---

## Validation

### No Warning-Only Messages
All validator rules are **fatal** — a pack that triggers any validator error will not load at all. There is no "warning" severity in the current validator. This is intentional during alpha: errors should be clear and actionable, not buried in warnings.

### Pass Order Dependency
The validator enforces pass order dependency (G017: no reading unwritten targets), but only within the declared pass order. If a pack's logical execution order does not match the declared array order, the validator will not detect it.

---

## Performance

### No Pass Graph Optimization
Passes execute in declared order with no dependency-driven reordering, dead pass elimination, or read/write coalescing. Every pass creates its own render pass with full target bind/clear cycles.

### Materialization Recreates on Every Reload
`ZipPackMaterializer` rebuilds the entire runtime resource pack from scratch on every resource reload (`PostFxReloadHooks`). This includes re-preprocessing all shaders and rewriting namespace references. There is no incremental update or caching.

---

## Diagnostics

### No In-Game Error Display
Validator errors are logged to `latest.log` but are not displayed in-game. If a pack fails to load, the only indication is that the game renders without post effects (fallback to vanilla rendering). There is no toast, chat message, or on-screen error display for pack load failures.

### Debug HUD Shows Runtime State Only
The Debug HUD (F8/F9 toggle when VPFX is active) shows runtime state (ON/OFF, backend, effect, shadow, target status) but does not show validator errors or pack load failures.
