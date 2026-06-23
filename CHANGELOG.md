# Changelog

## Unreleased (NR-0 / 1.9.2+)

### Added
- **VPFX Native Runtime v0 skeleton** — architecture foundation for the VPFX native (non-PostChain) render graph runtime
  - `VpfxNativeRuntimeBackend` — implements `VpfxRuntimeBackend` with backend ID `vpfx_native_v0`
  - `VpfxNativeRuntimeCapabilities` — capability record: `nativeRuntime=true`, all others `false`
  - `VpfxNativeRuntimeSupport` — v0 graph compatibility checker + backend selector
  - `VpfxNativeRuntimeSupportResult` — supported/unsupported result with descriptive reason string
  - Render graph IR types: `VpfxRuntimeGraph`, `VpfxRuntimePass`, `VpfxResourceRef`, `VpfxPassType`
  - `VpfxPassType` enum reserves: `FULLSCREEN`, `WORLD`, `SHADOW`, `COMPOSITE`, `FINAL`, `CLOUD`, `COMPUTE_FUTURE`
  - `VpfxResourceRef` models builtin targets (`minecraft:scene_color`, `scene_depth`, `shadow_depth`, `main`), pack custom targets, and future history buffers
- **v0 support checker rules**: supported only if single pass, single input=`minecraft:scene_color`, output=`minecraft:main`, no custom targets/textures/scene_depth/shadow_depth/compute
- **Backend selector**: default is always `minecraft_postchain`; opt-in via `-Dvulkanpostfx.vpfx.nativeRuntime=true` (dry-run only, always falls back)
- **Documentation**: `docs/vpfx-native-runtime-v0.md` (architecture, scope, support check rules, fallback behavior)
- **Roadmap**: `docs/vpfx-v2-runtime-roadmap.md` (10-phase plan from skeleton to default backend, Iris-like capability goals without Iris format cloning)
- **NR-1 Design**: `docs/vpfx-native-fullscreen-pass-design.md` — fullscreen passthrough pass design (single pass, scene_color→main, fullscreen triangle, pipeline cache, error isolation, PostChain fallback, 6-step implementation breakdown NR-1A–NR-1F)
- **NR-1A**: Implemented native runtime execute feature flag (`-Dvulkanpostfx.vpfx.nativeRuntime.execute=true`) with logging only:
  - `isExecuteEnabled()` validates execute flag requires `nativeRuntime=true`; warns and disables if not
  - `runDryRunCheck()` logs NR-1A status after NR-0 support check: "execute flag enabled, real native rendering is not implemented yet, fallback backend = minecraft_postchain"
  - Default backend unchanged; no GPU rendering implemented
- **NR-1B**: Implemented `VpfxNativeFullscreenDryRun` — resource import dry-run (logs-only):
  - Inspects `mainRenderTarget` availability, class, dimensions (width/height)
  - Logs `scene_color` source mapping (same as main target) and `minecraft:main` output mapping
  - Reports read/write same-target hazard (`scene_color` and `main` are the same `RenderTarget`)
  - Reports planned mitigation (copy `mainTarget` color → `transientColor` before native pass)
  - Creates no `RenderPipeline`, `RenderPass`, or GPU textures; no draw calls executed
  - Wired into `ActivePostEffectBridge` after NR-0 support check; falls back to `minecraft_postchain`
- **Dev tooling**: Gradle dev properties for native runtime flag testing (`-PvpfxNativeRuntime`, `-PvpfxNativeRuntimeExecute`) via `loom.runConfigs.client.vmArgs`; does not affect `build`/`jar`
- **Flag visibility log**: `VpfxNativeRuntimeSupport.logFlagVisibilityOnce()` outputs raw `nativeRuntime` / `nativeRuntime.execute` values once per session when any native runtime flag is set
- **NR-1C**: Resolved planning skeleton — new data model types for native runtime execution planning:
  - `VpfxNativeResolvedResource` — resolved resource reference (Role: SCENE_COLOR_INPUT / MAIN_OUTPUT / SAME_TARGET_ALIAS / TRANSIENT_TEMP), `RenderTarget` class, width/height
  - `VpfxNativeResolvedPass` — resolved pass with identity, pass type, resolved inputs/output, reads-scene-color, writes-main, same-target hazard, transient-temp-required flags
  - `VpfxNativeResolvedGraph` — ordered resolved passes with aggregated temp resource count
  - `VpfxNativeExecutionPlan` — top-level plan: pack id/name, resolved graph, main target resource, hazard detection, would-execute flag, fallback backend
  - `VpfxNativeFullscreenDryRun` rewritten to build `VpfxNativeExecutionPlan` before logging (plan-first architecture)
  - `VpfxNativeFullscreenPipeline` — pipeline skeleton with dry-run logging (primitive=TRIANGLES fullscreen triangle, depth/blend/cull=disabled, sampler=InSampler)
  - `VpfxNativePipelineKey` — pipeline cache key (pack id, pass id, pass type, shader refs, output format)
  - Builtin passthrough shader resources: `assets/vulkanpostfx/shaders/vpfx_native/fullscreen_passthru.vsh` / `.fsh`
  - Wire-in: `VpfxNativeFullscreenDryRun` calls `VpfxNativeFullscreenPipeline.runDryRun()` after plan build
- **NR-1C.1 (fix)**: Fixed same-target hazard detection in `VpfxNativeResolvedResource.isSameTargetAs()` — compares `renderTargetClass` + `width` + `height` (underlying `RenderTarget` identity) instead of semantic `reference` strings. `scene_color` and `main` now correctly report hazard=true.
- **NR-1D-A**: TransientColor allocation lifecycle check — `VpfxNativeTransientTargetDryRun` creates a real `TextureTarget` (`RGBA8_UNORM`, same size as `mainRenderTarget`), logs class/format, immediately destroys it. No copy, no draw, no `RenderPipeline`/`RenderPass`.
- **NR-1D-A.1 (fix)**: Moved transient allocation from worker thread to Render thread — `PostFxRuntimeState.pendingTransientAllocationCheck` flag set during worker-thread planning, consumed in `PostFxHookBridge.onWorldRenderTail()` (Render thread). `VpfxNativeTransientTargetDryRun.runOnRenderThread()` validates `RenderSystem.isOnRenderThread()` before creating/destroying `TextureTarget`.
- **NR-1D-B**: Copy/blit lifecycle check — `VpfxNativeTransientTargetDryRun.runOnRenderThread()` now copies `mainTarget.getColorTexture()` → `transientTarget.getColorTexture()` via `CommandEncoder.copyTextureToTexture()` between create and destroy. Logs copy method, attempted, and succeeded. All exceptions caught — fallback to `minecraft_postchain`.
- **NR-1E-A**: First real native render pass — `VpfxNativeFullscreenExecutor` implements builtin passthrough fullscreen draw:
  - `RenderPipeline` created lazily from `core/vpfx_native_fullscreen_passthru` shaders (fullscreen triangle, `Sampler0` convention), cached for session, invalidated on resource reload
  - `RenderPass` writes to `mainTarget` (color + depth), binds `BindGroupLayouts.GLOBALS` + custom `Sampler0` bind group, draws 3 vertices non-indexed
  - Executed after successful transient copy in `VpfxNativeTransientTargetDryRun.runOnRenderThread()`, gated behind `isExecuteEnabled()`
  - All errors caught → `pipelineCreationFailed` permanent flag or per-frame fallback; `minecraft_postchain` remains active backend
  - Builtin shaders moved to `assets/minecraft/shaders/core/vpfx_native_fullscreen_passthru.vsh` / `.fsh` for proper `ShaderManager` compilation
- **NR-1E-A.1**: `VpfxNativeExecutionResult` — structured execution result with Builder pattern (attempted, copySucceeded, pipelineCreated, renderPassCreated, drawExecuted, nativeSucceeded, fallbackStillActive, diagnosticMode, builtinPassthroughOnly, userShaderNativeExecution). `VpfxNativeFullscreenExecutor.execute()` returns result instead of boolean. `logDiagnosticSummaryOnce()` outputs clear diagnostic status.
- **NR-1E-B** (Revised): Synchronous pre-PostChain diagnostic draw. `VpfxNativeTransientTargetDryRun.attemptDiagnosticDraw()` — new method that performs transient copy + native passthrough draw inline, returns `VpfxNativeExecutionResult`. Called synchronously in `PostFxHookBridge.onWorldPostEffectBeforeHand()` immediately before `external.process()`. If native draw succeeds → skip that exact PostChain invocation. If fails → PostChain executes. No cross-phase pending skip. `PostChainProcessMixin` uses `isSkipPostChainThisFrame()` as secondary safety net for all `PostChain.process()` calls. Success logs: native draw succeeded=true, external PostChain skipped=true. Failure logs: native draw succeeded=false, fallback used=true, external PostChain executed=true.
- **NR-1E-B.1**: Log throttling and diagnostic counters. Success path logs throttled to first success + every 60 frames via `nativeDiagnosticFrameCounter` / `nativeDiagnosticLastSummaryFrame`. Failure/fallback logs always output immediately. New counters in `PostFxRuntimeState`: `nativeDiagnosticDrawSuccessCount`, `postChainSkippedFrameCount`, `nativeDiagnosticDrawFailureCount` with `nativeDiagnosticSummary()` formatted getter. Summary log format: single condensed line with all key fields + counter totals.
- **NR-1F-A**: User shader resolve/preprocess dry-run. `VpfxNativeUserShaderDryRun` — resolves vertex/fragment shader references from the active VPFX native pack's graph, maps to ZIP paths, reads source, preprocesses with `VpfxShaderSourcePreprocessor`. Logs: pack id, pass id, shader references, resolved paths, source loaded status, preprocess applied, source lengths. User shader native execution=false. Builtin passthrough remains actual draw shader. Called from `VpfxNativeFullscreenPipeline.runDryRun()`. Shader resolve failures logged as warnings; no crash, no pack marked failed.
- **NR-1F-B**: User shader pipeline planning dry-run. `VpfxNativeUserShaderDryRun.runPipelinePlanning()` — computes SHA-256 hashes of preprocessed vertex/fragment shader source, generates `VpfxNativePipelineKey` with hash fields (vertexSourceHash, fragmentSourceHash) and sampler convention (Sampler0). Logs: source hashes, output format, sampler convention, would create user shader pipeline=true, user shader RenderPipeline created=false, user shader native draw executed=false, actual pipeline used=builtin passthrough. `VpfxNativePipelineKey` extended with optional hash + sampler fields. No RenderPipeline created; no user shader executed.
- **NR-1F-C**: User shader RenderPipeline create dry-run. `VpfxNativeUserShaderDryRun.runCreatePipelineDryRun()` — attempts to create a `RenderPipeline` from materialized user shaders using ShaderManager via runtime namespace locator (e.g. `vpfxr_xxx:composite/final`). Pipeline uses `BindGroupLayouts.GLOBALS` + `Sampler0` bind group, fullscreen triangle topology, no cull. On Render thread, `RenderPipeline.builder().build()` is called and the result is logged but NOT used for drawing — actual draw remains builtin passthrough. All errors caught → `user shader RenderPipeline created=false`, `fallback used=true`. No user shader draw executed. Pipeline object not cached.
- **NR-1F-C.1**: Deferred RenderPipeline creation to Render thread. `VpfxNativeUserShaderDryRun.runCreatePipelineDryRun()` now stores shader locator + pipeline key + source hashes to static fields and marks `pendingUserShaderPipelineCreate` in `PostFxRuntimeState` when not on Render thread. `PostFxHookBridge.onWorldRenderTail()` consumes the flag and calls `runPendingPipelineCreateOnRenderThread()` which performs the actual `RenderPipeline.builder().build()` on the Render thread. Logs NR-1F-C.1 prefix. Same no-draw guarantee.
- **NR-1F-C.2**: User shader RenderPipeline cache. `VpfxNativeUserPipelineCache` — static `LinkedHashMap` caches successful `RenderPipeline` objects by `VpfxNativePipelineKey` and records failures with reason strings. Both `runCreatePipelineDryRun()` (sync path) and `runPendingPipelineCreateOnRenderThread()` (deferred path) are cache-aware: cache hit (success) → skip creation, log cache hit; cache hit (failure) → skip creation, log cached failure; cache miss → build pipeline → write to cache. Logs NR-1F-C.2 prefix with cache hit, attempted, created, cached, fallback fields. `PostFxReloadHooks` PREPARE phase calls `VpfxNativeUserPipelineCache.clear()` for F3+T invalidation. No user shader draw.

### Changed
- Updated `README.md` to include native runtime v0 skeleton status, architecture diagram, backend comparison table, and documentation links

---

## 1.9.2

### Added
- **VPFX failure diagnostic report** — when an external pack fails during load, compilation, or execution, a diagnostic file is written to `run/vulkanpostfx_runtime/diagnostics/latest-vpfx-error.txt`
- **Failure diagnostic captures**: timestamp, pack name/id, externalPostEffectId, backend id/capabilities, failure stage, exception class/message, stack trace
- **VpfxFailureDiagnostics** utility class with safe write (failures during diagnostic write are logged as warnings, not crashes)
- **VPFX troubleshooting guide** (`docs/vpfx-troubleshooting.md`) covering 8 common error patterns with causes and fixes
- Diagnostic file write is wired into `PostFxExternalTargetRunner`, `PostFxHookBridge`, and `ActivePostEffectBridge`

### Changed
- Expanded PostFxExternalTargetRunner try/catch to cover both `addToFrame` and `frame.execute` (was only `execute`)
- PostFxHookBridge now checks `isExternalPackMarkedFailed()` after `external.process()` returns before calling `markWorldStageExternalEffectApplied()`

---

## 1.7.0-alpha / VPFX v1 Alpha — 2026-05-11

> **VPFX v1 alpha spec freeze.** This release standardizes the VPFX native pack format with full validation, test coverage, and documentation. The execution backend remains Minecraft PostChain (transitional). Native VPFX runtime is not yet implemented.

### Added

- **VPFX native pack format v1 (alpha spec)**
  - `pack.json` manifest with `format_version`, `pack_id`, `name`, `version`, `entry_post_effect`, `capabilities`, `textures`, `metadata`
  - `post_effect/*.json` graph definition with `targets` and `passes`
  - Pack-local shader namespace (`<pack_id>:<path>`)
- **VPFX graph validation**
  - G004: Output target must be declared or builtin
  - G005: Input target must reference declared or builtin target
  - G013: Texture inputs must reference declared textures
  - G015: Self-read/write cycle detection (pass writes to target it reads from)
  - G016: Graph must write to `minecraft:main`
  - G017: Future dependency detection (pass reads unwritten target)
  - All error messages include pass identity (`id` or `debug_label` or `passes[N]`)
- **Pass `id` / `debug_label` fields** (optional, backward compatible)
- **Non-post/ shader path support** — shader paths no longer require `post/` prefix; any valid subdirectory under `shaders/` is accepted
- **Shader path escape protection** — `..`, absolute paths, and backslashes are rejected at loader and validator level
- **Shader include preprocessing** — `#include` directives with path escape protection, cycle detection, max depth limit
- **Builtin target aliases**: `minecraft:scene_color`, `minecraft:scene_depth`, `minecraft:shadow_depth`
- **VPFX validation smoke test** (`VpfxPackValidationSmokeTest`)
  - 11 negative test packs covering all graph/manifest/shader/texture error codes
  - 1 positive minimal test pack with non-post/ shader path
  - `negative_missing_pack_json` special case (null return = not a VPFX pack)
- **VPFX runtime materialization smoke test** (`VpfxRuntimeMaterializationSmokeTest`)
  - Verifies VPFX native pack → runtime resource pack conversion
  - Checks post_effect JSON, shader preprocessing, non-post/ path mapping, texture manifest
  - Runs through explicit `VpfxRuntimeBackend` interface (backend-aware)
- **Explicit VPFX runtime backend interface** (`VpfxRuntimeBackend`)
  - Separates graph format from execution engine
  - `id()`, `displayName()`, `capabilities()`, `materialize()`
- **Minecraft PostChain backend wrapper** (`VpfxPostChainBackend`)
  - Backend ID: `minecraft_postchain`
  - Capabilities: `usesPostChain=true`, `nativeRuntime=false`, `supportsCompute=false`
  - Delegates to existing `ZipPackMaterializer` — no behavior change
- **ActivePostEffectBridge now uses `VpfxRuntimeBackend`**
  - Materialization in live runtime goes through `VpfxRuntimeBackend` interface (not raw `ZipPackMaterializer`)
  - Runtime log includes backend id and capabilities on materialization
- **Gradle tasks**
  - `validateTestPacks` — run pack validation
  - `validateMaterialization` — run materialization check
- **Documentation**
  - `docs/vpfx-pack-format-v1.md` — complete VPFX v1 alpha format specification
  - `docs/vpfx-testing.md` — testing guide, error code reference, how to add test packs
  - Test pack READMEs for `positive_minimal` and the test pack directory

### Changed

- **VPFX v1 status**: Now explicitly an **alpha spec**. Fields, rules, and execution semantics may change in future alpha releases.
- **Execution backend**: Minecraft PostChain backend explicitly documented as transitional. Native VPFX runtime is not yet available.
- Shader path validation no longer requires `post/` prefix
- G015 self-read/write check now exempts builtin targets (reading `minecraft:main` while writing to it is allowed)

### Known Limitations

- **Compute shaders**: Unsupported. Packs must set `compute: false`.
- **Shadow depth**: Experimental. Visual artifacts may occur.
- **Native VPFX runtime**: Not yet implemented. The current backend is Minecraft PostChain.
- **Performance**: No explicit pass graph optimization at this stage.

---

## 1.6.0+mc26.2-snapshot2-2026-04-29 — 2026-04-29

### Added

- Added Minecraft 26.2 Snapshot 2 support and bumped the mod version from `1.5.1+mc1.21.4-2026-04-19`.
- Added a lightweight world shadow pipeline with a dedicated shadow depth pass and terrain receiver path.
- Added the `vulkanpostfx:shadow_depth` external target for VPFX post chains.
- Added the `vulkanpostfx:scene_depth` external target by capturing the main scene depth after world rendering.
- Added an F9 shadow depth debug view that directly displays the shadow depth texture.
- Expanded the `VpfxBuiltins` uniform block with projection, previous projection, view, previous view, shadow, inverse shadow, fog, sky, sun, moon, and shadow-light data.
- Added Iris-style shader aliases such as `gbufferProjection`, `gbufferModelView`, `shadowModelView`, `shadowProjection`, `sunAngle`, `moonAngle`, `shadowAngle`, `sunPosition`, `moonPosition`, and `shadowLightPosition`.

### Changed

- Reworked shadow matrix construction to follow the vanilla/Iris-style celestial transform instead of a fixed debug light direction.
- Shadow rendering now builds a shadow frustum, swaps the visible section list temporarily, and prepares terrain chunks from the shadow camera.
- Shadow projection coverage now follows the configured terrain shadow distance instead of a fixed oversized debug box.
- Shadow terrain rendering now uses normal terrain vertex transforms again instead of the earlier forced-fragment debug shader path.
- External post effects that need world data can now run at the world stage before hand rendering.
- Scene/world position reconstruction now uses the captured inverse view-projection matrix instead of relying on the previous Y-flipped depth assumption.

### Fixed

- Fixed incorrect shadow depth ordering under Minecraft 26.2 reversed-Z rendering. This prevents underground or distant cave geometry from overwriting surface depth in the shadow map and appearing as unexplained shadows on open ground.
- Fixed shadow pass depth state to match the reversed-Z depth clear/test path.
- Reduced shadow leaks from face winding and culling mismatch by disabling culling for the shadow terrain pipeline.
- Avoided applying shadow-depth-driven post chains twice when they are already handled in the world-stage path.

### Notes

- Cutout and leaf shadow casting is still intentionally limited while the solid terrain shadow depth path is being stabilized.
- The current world terrain receiver applies the new shadow sampling path only to opaque terrain; cutout receiver handling remains a follow-up item.
