# VPFX Native Runtime v0

> **Status**: Skeleton / Experimental — Not for production use.  
> **Target**: Minecraft 26.2 snapshots (Fabric).  
> **Default Backend**: `minecraft_postchain` (VPFX Native Runtime is opt-in).

---

## Overview

VPFX Native Runtime is a lightweight **Render Graph Runtime** for VPFX shader packs. It is **not just a post-processing system** — it is designed to eventually execute full-frame rendering passes (world, shadow, composite, final) in a unified graph model.

**VPFX Native Runtime v0** is the first skeleton running in the application process. Native GPU draw exists only under explicit execute flag (`-PvpfxNativeRuntimeExecute`). Its purpose is to:

1. Define the runtime architecture (types, graph IR, pass model).
2. Provide a `VpfxNativeRuntimeSupport` checker that validates whether a VPFX graph is compatible with the native runtime.
3. Provide a backend selector that can probe native runtime support and decide on fallback.

**The default backend for all VPFX execution remains `minecraft_postchain`** (`VpfxPostChainBackend`). Native Runtime v0 is **opt-in via system property**. Under `-PvpfxNativeRuntimeExecute`, NR-1 can perform GPU draw for supported VPFX packs.

---

## Architecture

```
VPFX Pack (.zip)
    │
    ├── pack.json  (VPFX v1 alpha schema, parsed by VpfxNativeZipPackLoader)
    ├── post_effect/*.json  (graph definition → VpfxGraphDefinition)
    └── shaders/
    │
    ▼
[ShaderPackContainer.vpfxDefinition()]  ← VpfxNativePackDefinition
    │
    ▼
[ActivePostEffectBridge.refreshFromActivePack()]
    │
    ├── (zip path: shader validation + PostChain backend)
    │
    └── (native pack: VpfxNativeRuntimeSupport.runDryRunCheck())
        │
        ├── no -D flag → silent (no log output)
        │
        └── -Dvulkanpostfx.vpfx.nativeRuntime=true
            ├── supported → log
            └── unsupported → log + reason
            │
            └── ALWAYS fallback to VpfxPostChainBackend
```

---

## v0 Scope

### ✅ Supported (dry-run, NR-1F-D execute)
| Feature | Status |
|---------|--------|
| Single pass execution check | ✅ (detected) |
| `minecraft:scene_color` input | ✅ (detected) |
| `minecraft:main` output | ✅ (detected) |
| `FULLSCREEN` pass type | ✅ (detected) |
| Support check result (supported/unsupported + reason) | ✅ |
| Backend selector with system property flag | ✅ |

### ❌ Not yet supported (v0)
| Feature | Status |
|---------|--------|
| Actual GPU rendering | ❌ |
| Multi-pass graphs | ❌ |
| Custom targets | ❌ |
| `minecraft:scene_depth` | ❌ |
| `minecraft:shadow_depth` | ❌ |
| Textures (non-target inputs) | ❌ |
| Compute passes | ❌ |
| History buffers | ❌ |
| World pass | ❌ |
| Shadow pass | ❌ |
| Composite pass | ❌ |
| Final pass | ❌ |
| Pipeline creation | ❌ |
| Sampler/buffer binding | ❌ |

---

## v0 Support Check Rules

A VPFX graph is **supported** by Native Runtime v0 if and only if **all** of the following conditions are met:

| # | Condition | Check |
|---|-----------|-------|
| 1 | `targets` is empty | No custom targets declared |
| 2 | Exactly 1 pass | Single pass only |
| 3 | Exactly 1 input | Single input only |
| 4 | Input target is `minecraft:scene_color` | Only scene color input |
| 5 | Output is `minecraft:main` | Only main output |
| 6 | No texture inputs | No `texture` field in inputs |
| 7 | No `scene_depth` capability | `scene_depth` is not requested |
| 8 | No `shadow_depth` capability | `shadow_depth` is not requested |
| 9 | No custom targets capability | `custom_targets` is not requested |
| 10 | `compute` is `false` | Compute not requested |

An **unsupported** graph returns a descriptive `reason` string (e.g. "native v0 unsupported: multi-pass graph (2 passes)").

---

## Backend Selector Behavior

### Default (no system property)
Backend is always `VpfxPostChainBackend` (`minecraft_postchain`).

### With `-Dvulkanpostfx.vpfx.nativeRuntime=true`
1. `VpfxNativeRuntimeSupport` loads the pack's graph.
2. Runs the v0 support check.
3. Logs the result: supported or unsupported + reason.
4. **Falls back to `minecraft_postchain`** regardless of result.
5. Native Runtime v0 performs GPU draw only under explicit execute flag.

### With `-Dvulkanpostfx.vpfx.nativeRuntime.execute=true` (NR-1A)
- **Requires** `nativeRuntime=true`, otherwise the execute flag is ignored with a warning.
- NR-1 execute path can perform transient copy, RenderPipeline creation/cache, texture binding, and fullscreen draw for supported VPFX packs.
- **PostChain remains fallback**, not always-used path under execute success.
- **Default backend remains `minecraft_postchain`.**

### System Properties
```
-Dvulkanpostfx.vpfx.nativeRuntime=true          # NR-0 dry-run support check
-Dvulkanpostfx.vpfx.nativeRuntime.execute=true   # NR-1A execute flag (requires nativeRuntime=true)
```

### Development — Recommended Gradle Commands

Use Gradle project properties (not `-Dorg.gradle.jvmargs`) to pass native runtime flags to the Minecraft `runClient` JVM:

```bash
# NR-0 dry-run only
./gradlew runClient -PvpfxNativeRuntime

# NR-1A execute flag (enables NR-1B resource dry-run, NR-1F-D user shader draw)
./gradlew runClient -PvpfxNativeRuntimeExecute
```

These are **dev-only** Gradle properties — they do not affect `build`, `jar`, or release artifacts. The `build.gradle` `loom.runConfigs.client` block conditionally adds JVM `-D` flags when these properties are present.

### Fallback Guarantee
VFXP Native Runtime v0 **default backend is `minecraft_postchain`**. Under explicit `nativeRuntime.execute=true`, NR-1 can perform GPU draw for supported VPFX packs. If user shader pipeline is unavailable, draw fails, or execute flag is not set, the native path falls back to builtin passthrough or `minecraft_postchain`. PostChain fallback is never removed.

---

## Type Model

### VpfxPassType (enum)
Reserved pass types for the full VPFX render pipeline:
- `FULLSCREEN` — v0 only
- `WORLD` — future
- `SHADOW` — future
- `COMPOSITE` — future
- `FINAL` — future
- `CLOUD` — future
- `COMPUTE_FUTURE` — future

### VpfxResourceRef
Represents a resource in the render graph. Can model:
- `minecraft:scene_color`
- `minecraft:scene_depth`
- `minecraft:shadow_depth`
- `minecraft:main`
- Pack custom target
- Future history buffer target

### VpfxRuntimePass
A single render pass in the native runtime graph. Contains:
- Pass type (FULLSCREEN, WORLD, etc.)
- Input resource references
- Output resource reference
- Vertex/fragment shader references

### VpfxRuntimeGraph
Ordered list of `VpfxRuntimePass` instances.

---

## Relationship to PostChain Backend

| Aspect | PostChain Backend | Native Runtime v0 |
|--------|-------------------|-------------------|
| Backend ID | `minecraft_postchain` | `vpfx_native_v0` |
| Execution | Minecraft PostChain | Skeleton (no real render) |
| nativeRuntime capability | `false` | `true` |
| Fallback target | — | Falls back to PostChain |
| Pack format | VPFX v1 | VPFX v1 (no change) |
| Graph v2 support | N/A | Roadmap only |

VPFX pack authors do **not** need to change anything to support Native Runtime. The graph format is the same — only the execution backend changes.

---

## Future Versions

See [docs/vpfx-v2-runtime-roadmap.md](vpfx-v2-runtime-roadmap.md) for the long-term plan.

### Next Step: NR-1 — Native Fullscreen Passthrough Pass

The next milestone beyond v0 skeleton is NR-1: the first real GPU render pass in the VPFX Native Runtime. A single fullscreen pass that reads `minecraft:scene_color`, applies a user-provided or builtin passthrough fragment shader, and writes to `minecraft:main`.

**Design document**: [docs/vpfx-native-fullscreen-pass-design.md](vpfx-native-fullscreen-pass-design.md)

**Scope**: Fullscreen passthrough only. No multi-pass, no custom targets, no scene_depth, no shadow_depth, no compute. Always has PostChain fallback.

**Feature flags**: NR-0 uses `-Dvulkanpostfx.vpfx.nativeRuntime=true` (dry-run). NR-1 adds `-Dvulkanpostfx.vpfx.nativeRuntime.execute=true` (real rendering, still opt-in).

### NR-1A (Done): Feature Flag + Logging

Execute flag plumbing: `SYSTEM_PROPERTY_EXECUTE`, `isExecuteRequested()`, `isExecuteEnabled()`, warning on isolated `execute=true`.

### NR-1B (Done): Resource Import Dry-Run

`VpfxNativeFullscreenDryRun` — logs-only inspection of `mainRenderTarget` availability, dimensions, `scene_color` mapping, same-target read/write hazard detection, and planned `transientColor` copy mitigation. No GPU resources created.

### NR-1C (Done): Resolved Planning Skeleton

Native runtime resolved model types for execution planning:
- `VpfxNativeResolvedResource` — resolved resource reference with role (SCENE_COLOR_INPUT, MAIN_OUTPUT, TRANSIENT_TEMP), underlying `RenderTarget` class, dimensions
- `VpfxNativeResolvedPass` — resolved pass with identity, pass type, resolved inputs/output, reads-scene-color flag, writes-main flag, same-target hazard flag, transient-temp-required flag
- `VpfxNativeResolvedGraph` — ordered resolved passes, aggregated temp resource count
- `VpfxNativeExecutionPlan` — top-level plan: pack id/name, resolved graph, main target resource, hazard flags, would-execute flag, fallback backend

`VpfxNativeFullscreenDryRun` now builds an `VpfxNativeExecutionPlan` from the VPFX graph + Minecraft state before logging. At this historical dry-run stage, no real rendering occurred.

### NR-1C (Done): Pipeline Skeleton

`VpfxNativeFullscreenPipeline` — pure dry-run pipeline skeleton:
- `VpfxNativePipelineKey` — cache key: pack id, pass id, pass type, vertex/fragment shader refs, output format placeholder
- Builtin passthrough shader files: `vpfx_native/fullscreen_passthru.vsh` / `.fsh` (fullscreen triangle, `InSampler` convention)
- Logs pipeline plan: topology, primitive, depth/blend/cull state, sampler name, output target, cache key
- Wire-in: `VpfxNativeFullscreenDryRun` calls `VpfxNativeFullscreenPipeline.runDryRun()` after plan build
- No `RenderPipeline` created, no `RenderPass`, no draw call

### NR-1C.1 (Fix): Same-Target Hazard Detection

`VpfxNativeResolvedResource.isSameTargetAs()` now compares `renderTargetClass` + `width` + `height` (underlying `RenderTarget` object identity) instead of semantic `reference` strings. `scene_color` and `main` now correctly report `hazard=true` when they share the same `RenderTarget`. `transientRequired=true` and `tempResourceCount=1` when hazard is detected.

### NR-1D-A (Done): TransientColor Allocation Lifecycle Check

`VpfxNativeTransientTargetDryRun` — creates a real `TextureTarget` (`RGBA8_UNORM`, same size as `mainRenderTarget`), logs class/format, immediately destroys it via `destroyBuffers()`. Verifies lifecycle without copy or draw.

**NR-1D-A.1 fix**: Allocation now runs on the Render thread via deferred state:
- `PostFxRuntimeState.markPendingTransientAllocationCheck()` set during worker-thread planning (in `VpfxNativeFullscreenDryRun`)
- `PostFxRuntimeState.consumePendingTransientAllocationCheck()` consumed in `PostFxHookBridge.onWorldRenderTail()` (Render thread)
- `VpfxNativeTransientTargetDryRun.runOnRenderThread()` validates `RenderSystem.isOnRenderThread()` before GPU resource creation.
- On successful consume, the flag is cleared to avoid per-frame log spam.

### NR-1D-B (Done): Copy/Blit Lifecycle Check

`VpfxNativeTransientTargetDryRun.runOnRenderThread()` now copies `mainTarget.getColorTexture()` → `transientTarget.getColorTexture()` via `CommandEncoder.copyTextureToTexture()` between create and destroy. Logs copy method (`CommandEncoder.copyTextureToTexture`), copy attempted, and copy succeeded. All exceptions caught — fallback to `minecraft_postchain`. Still no `RenderPipeline`, `RenderPass`, or draw call.

### NR-1E-A (Done): Builtin Passthrough Native Draw

First real native render pass using builtin passthrough shaders:
- `VpfxNativeFullscreenExecutor` — lazy `RenderPipeline` from `core/vpfx_native_fullscreen_passthru` shaders (fullscreen triangle, `Sampler0`), cached for session
- `RenderPass` on `mainTarget` (color+depth), binds GLOBALS + `Sampler0` → transientColor, draws 3 vertices non-indexed
- Wired after successful NR-1D-B copy in `VpfxNativeTransientTargetDryRun.runOnRenderThread()`
- Pipeline creation failure → permanent skip for session; draw failure → per-frame fallback
- `minecraft_postchain` remains active backend; no user shaders loaded, no multi-pass
- Builtin shaders: `assets/minecraft/shaders/core/vpfx_native_fullscreen_passthru.vsh` / `.fsh`

### NR-1E-A.1 (Done): Execution Result + Diagnostic Status

`VpfxNativeExecutionResult` — structured result type with Builder pattern tracking all execution stages. `VpfxNativeFullscreenExecutor.logDiagnosticSummaryOnce()` outputs clear diagnostic status: native draw succeeded, postchain fallback remains active, native backend replacement=false, user shader native execution=false, builtin passthrough only=true.

### NR-1E-B (Done, Revised 1.10.5): Synchronous Pre-PostChain Diagnostic Draw

NR-1E-B uses a synchronous approach — the native diagnostic draw is attempted immediately before `external.process()` in the same call flow:

- `PostFxHookBridge.onWorldPostEffectBeforeHand()` calls `VpfxNativeTransientTargetDryRun.attemptDiagnosticDraw()` before `external.process()`
- `attemptDiagnosticDraw()` does: create transient `TextureTarget` → `CommandEncoder.copyTextureToTexture` copy → `VpfxNativeFullscreenExecutor.execute()` → destroy transient
- If native draw succeeds: logs NR-1E-B success, sets `skipPostChainThisFrame=true`, returns — `external.process()` never called
- If native draw fails: logs NR-1E-B fallback, sets `skipPostChainThisFrame=false`, continues to `external.process()`
- `PostChainProcessMixin` guard uses `isSkipPostChainThisFrame()` as secondary safety net for all `PostChain.process()` calls (e.g., Minecraft's builtin post-chain)
- Per-frame state reset (`resetPerFrameState()`) at `onWorldRenderHead` clears the flag for the next frame
- No cross-phase pending — skip decision and action are in the same synchronous flow

### NR-1F-A (Done): User Shader Resolve/Preprocess Dry-Run

`VpfxNativeUserShaderDryRun` — reads the active VPFX native pack's graph, resolves pass shader references:

- Gets active pack via `ActiveShaderPackManager.getActivePack()` → `VpfxNativePackDefinition`
- Parses `vertex_shader` / `fragment_shader` from the first/only pass
- Maps shader references (e.g. `vpfx_minimal_showcase:composite/final`) to ZIP paths (`shaders/composite/final.vsh` / `.fsh`)
- Opens ZIP, reads source, preprocesses with `VpfxShaderSourcePreprocessor` (include expansion + builtin uniform injection)
- Logs: pack id, pass id, shader references, resolved paths, source loaded/not loaded, preprocess applied, source lengths
- **Does not execute user shaders.** Builtin passthrough remains the active native draw shader.
- Called from `VpfxNativeFullscreenPipeline.runDryRun()` alongside NR-1C pipeline skeleton dry-run.
- Shader resolve failures are caught and logged as warnings; no crash, no pack marked failed.

### NR-1F-B (Done): User Shader Pipeline Planning Dry-Run (historical, superseded by NR-1F-D)

`VpfxNativeUserShaderDryRun.runPipelinePlanning()` — computes SHA-256 hashes of preprocessed shader source, generates `VpfxNativePipelineKey` with InSampler convention. Historical dry-run stage: pipeline planning does not execute draws. Actual frame draw uses NR-1F-D path.

### NR-1F-C (Done): User Shader RenderPipeline Create Dry-Run (historical, superseded by NR-1F-D)

`VpfxNativeUserShaderDryRun.runCreatePipelineDryRun()` — historical dry-run stage: creates RenderPipeline with InSampler bind group to validate shader compilation. NR-1F-D performs actual frame draw with resolved/cached user pipeline.

### NR-1F-C.1 (Done): Deferred Pipeline Creation

`PostFxRuntimeState.pendingUserShaderPipelineCreate` flag marks that user shader pipeline creation is pending for the Render thread. `PostFxHookBridge.onWorldRenderTail()` consumes it and calls `VpfxNativeUserShaderDryRun.runPendingPipelineCreateOnRenderThread()` which performs the actual `RenderPipeline.builder().build()`. Logs NR-1F-C.1 prefix. Same no-draw guarantee.

### NR-1F-C.2 (Done): User Shader RenderPipeline Cache

`VpfxNativeUserPipelineCache` — dual-map static cache: `LinkedHashMap<VpfxNativePipelineKey, RenderPipeline>` for successes and `LinkedHashMap<VpfxNativePipelineKey, String>` for failure reasons. Both sync (`runCreatePipelineDryRun()` direct Render thread) and deferred (`runPendingPipelineCreateOnRenderThread()`) creation paths check cache first: success hit → skip creation; failure hit → skip + log reason; miss → build → write cache. `PostFxReloadHooks` PREPARE phase calls `clear()` for F3+T invalidation. Logs NR-1F-C.2 prefix.

### NR-1F-D (Done): User Shader Native Draw Path

`VpfxNativeUserPipelineResolveResult` — result class carrying resolved user `RenderPipeline`, cache status, and shader IDs. `VpfxNativeUserShaderDryRun.resolveOrCreateForActivePackOnRenderThread()` — unified entry point that returns a `VpfxNativeUserPipelineResolveResult` for the active VPFX pack's first pass. Checks cache first; creates + caches on miss.

`VpfxNativeFullscreenExecutor.execute()` now accepts an optional `RenderPipeline` parameter: if provided (user shader available), draws with it (`userShaderNativeExecution=true`, `builtinPassthroughOnly=false`); if not provided, falls back to builtin passthrough. On user shader draw failure, automatically falls back to builtin passthrough draw within the same render pass lifecycle.

`VpfxNativeTransientTargetDryRun.attemptDiagnosticDraw()` now calls `resolveOrCreateForActivePackOnRenderThread()` before drawing. Logs reflect actual pipeline status (`actualPipeline`, `userShaderNativeExecution`, `builtinPassthroughOnly`). Cache is mutual-exclusive: `putSuccess` clears failure, `putFailure` clears success.

Native Runtime still not default backend. User shader native draw is opt-in via `-PvpfxNativeRuntimeExecute`.

---

## NR-1G-B.1 (Done): Complete Failure Stage Propagation

Extended `VpfxNativeUserPipelineResolveResult` with `failureStage`/`failureMessage`. `resolveOrCreateForActivePackOnRenderThread()` sets stage at every unavailable branch (EXECUTE_FLAG_DISABLED, NOT_RENDER_THREAD, USER_PIPELINE_RESOLVE, USER_PIPELINE_CREATE). `VpfxNativeFullscreenExecutor.execute()` accepts `upstreamFailureStage`/`upstreamFailureMessage`, preserves BUILTIN_PIPELINE_CREATE when fallback pipeline is null, fixes texture-unavailable → COPY_MAIN_TO_TRANSIENT, adds postChainFallbackExpected to all early-return paths. PostFxHookBridge success summary includes failure diagnostics. Does not change execution path.

---

## Compliance

| Constraint | Status |
|------------|--------|
| Default backend remains minecraft_postchain | ✅ |
| Native GPU draw exists only under explicit execute flag | ✅ |
| Pipeline creation / texture binding / draw present in NR-1 execute path | ✅ |
| PostChain fallback remains intact | ✅ |
| VPFX v1 schema unchanged | ✅ |
| Pack format unchanged | ✅ |
