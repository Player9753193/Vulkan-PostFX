# VPFX NR-1: Native Fullscreen Passthrough Pass Design

> **Status**: Implemented (NR-1A through NR-1E-B complete).  
> **Parent**: VPFX Native Runtime v0 skeleton (NR-0).  
> **Target**: Minecraft 26.2 snapshots (Fabric).  

---

## 1. Goal

NR-1 implements the **first real GPU render pass** in the VPFX Native Runtime: a single fullscreen quad that reads `minecraft:scene_color`, optionally applies a user-provided fragment shader, and writes the result to `minecraft:main`.

This is the smallest possible native rendering unit that proves the entire Native Runtime stack — resource import, pipeline creation, shader binding, render pass execution, and output — works end-to-end.

**NR-1 is opt-in via `-Dvulkanpostfx.vpfx.nativeRuntime=true` and always has PostChain fallback.**

---

## 2. Scope Boundary

### ✅ NR-1 Scope

| Aspect | Scope |
|--------|-------|
| Pass type | `FULLSCREEN` only |
| Graph depth | Single pass only |
| Input resource | `minecraft:scene_color` only |
| Output resource | `minecraft:main` only |
| Custom targets | None |
| Custom textures | None |
| User shader | Optional (passthrough or pack-provided) |
| Fallback | PostChain backend always available |
| Feature flag | `-Dvulkanpostfx.vpfx.nativeRuntime.execute=true` (separate from NR-0 dry-run) |
| Pipeline lifecycle | Created once, cached, destroyed on resource reload / resize |

### ❌ NR-1 Explicitly Does NOT

| What NR-1 does NOT do | Deferred to |
|-----------------------|-------------|
| Multi-pass graphs | NR-3 (Multi-pass graph) |
| Custom render targets (`targets` block non-empty) | NR-3 |
| `minecraft:scene_depth` as input | NR-4+ (World pass) |
| `minecraft:shadow_depth` as input | NR-5+ (Shadow pass) |
| Compute passes / compute shaders | NR-9+ |
| Volumetric lighting | NR-8+ (Atmosphere) |
| Cloud rendering | NR-8+ |
| Ray tracing (RT-lite) | NR-9+ |
| Default-enabled native backend | NR-10+ |
| History buffers / TAA / motion vectors | NR-7+ |
| Custom target scaling (half-res, quarter-res) | NR-3 |
| Texture (non-target) inputs | Not scoped |
| Read/write to the same `RenderTarget` without transient copy | Explicitly prevented by design (see §7) |
| Bypass or replace PostChain backend | PostChain is L2 fallback (see §8) |
| Run without `-Dvulkanpostfx.vpfx.nativeRuntime.execute=true` | Always required (see §9) |
| Modify VPFX v1 pack format | Schema unchanged |

---

## 3. End-to-End Flow

```
VPFX Pack (.zip)
  │
  ├── pack.json          →  VpfxPackManifest
  ├── post_effect/xxx.json  →  VpfxGraphDefinition
  └── shaders/          →  VPFX native shader resources (.vsh / .fsh)
  │
  ▼
VpfxNativeRuntimeSupport
  ├── runDryRunCheck(manifest, graph)   ← NR-0: always runs
  └── isExecuteEnabled()               ← NR-1: -Dvulkanpostfx.vpfx.nativeRuntime.execute=true
  │
  ▼
VpfxRuntimeGraph
  └── passes[0]: VpfxRuntimePass
      ├── passType: FULLSCREEN
      ├── inputs: [minecraft:scene_color]
      └── output: minecraft:main
  │
  ▼
Native Fullscreen Executor  (new: NR-1C)
  │
  ├── Step 1: copy mainTarget → transientColor   (read/write isolation, §7.2)
  │
  ├── Step 2: create/bind RenderPipeline
  │     ├── .withBindGroupLayout(BindGroupLayouts.GLOBALS)
  │     ├── .withBindGroupLayout(InSampler layout)
  │     ├── .withVertexBinding(...)            ← quad fallback only
  │     ├── .withPrimitiveTopology(TRIANGLES)
  │     └── .withVertexShader / .withFragmentShader
  │
  ├── Step 3: bind sampler
  │     ├── "InSampler"  ← transientColor.colorTextureView
  │     └── linear clamp sampler
  │
  ├── Step 4: draw
  │     ├── fullscreen triangle (3 vertices, no index buffer)
  │     └── → writes mainTarget.colorTextureView
  │
  └── Step 5: destroy transientColor
  │
  ▼
minecraft:main  (written, ready for swapchain present)
```

### Data Flow Diagram

```
┌──────────────────────┐
│ minecraft:scene_color │  ← external, imported by FrameGraphBuilder
│   (read-only)         │
└──────────┬───────────┘
           │
           ▼
   ┌───────────────┐
   │ FULLSCREEN     │
   │ pass           │
   │ (user shader   │
   │  or passthru)  │
   └───────┬───────┘
           │
           ▼
┌──────────────────────┐
│    minecraft:main     │  ← written by this pass
│   (output to screen)  │
└──────────────────────┘
```

---

## 4. Fullscreen Primitive

### Fullscreen Triangle (Recommended)

Replace the traditional fullscreen quad with a **fullscreen triangle**:

**Advantages over quad:**
- No diagonal seam artifacts from mismatched barycentric interpolation
- No vertex attribute upload needed for UV coordinates
- Vertex shader can compute UV from `gl_VertexIndex` (0, 1, 2)
- Vertices cover screen-space `[-1, -1]` to `[3, 3]` such that clip-space transforms produce correct UV
- No index buffer needed

**Vertex positions (NDC):**
```
Vertex 0: (-1.0, -1.0, 0.0, 1.0)
Vertex 1: ( 3.0, -1.0, 0.0, 1.0)
Vertex 2: (-1.0,  3.0, 0.0, 1.0)
```

**Vertex shader UV derivation:**
```glsl
vec2 uv = (gl_Position.xy * 0.5 + 0.5);
```

**Draw call:**
```
draw(3)  // non-indexed, 3 vertices
```

**Pipeline topology:**
```
PrimitiveTopology.TRIANGLES
```

### Fallback: Fullscreen Quad

If triangle proves problematic with Snap7's vertex binding conventions, fall back to a quad with index buffer and explicit UV vertex buffer. This doubles the vertex count and requires an attribute binding but is easier to debug.

---

## 5. Shader Model

### Passthrough Shader (Builtin Default)

When no user pack is active, NR-1 ships a builtin passthrough shader that reads `scene_color` and writes it unmodified to `main`. This pure identity pass validates the entire pipeline without visual change.

**Vertex shader (`fullscreen_passthru.vsh`):**
```glsl
#version 450
// NR-1 passthrough vertex shader — fullscreen triangle
const vec2 POSITIONS[3] = vec2[](
    vec2(-1.0, -1.0),
    vec2( 3.0, -1.0),
    vec2(-1.0,  3.0)
);
out vec2 v_texCoord;
void main() {
    vec2 pos = POSITIONS[gl_VertexIndex];
    gl_Position = vec4(pos, 0.0, 1.0);
    v_texCoord = pos * 0.5 + 0.5;
}
```

**Fragment shader (`fullscreen_passthru.fsh`):**
```glsl
#version 450
uniform sampler2D InSampler;
in vec2 v_texCoord;
out vec4 fragColor;
void main() {
    fragColor = texture(InSampler, v_texCoord);
}
```

### User Shader (Pack-Provided)

When a VPFX pack is active and its graph defines a `FULLSCREEN` pass with vertex/fragment shader references, NR-1 loads and compiles those shaders instead of the builtin passthrough.

**Shader loading path:** Pack ZIP → `shaders/composite/xxx.vsh` / `shaders/composite/xxx.fsh` (mapped from `<pack_id>:<path>`).

**Builtin passthrough remains available** as a fallback if the user shader fails to compile.

### Sampler Convention

NR-1 must follow the VPFX v1 sampler convention: `sampler_name = "In"` → GLSL uniform `InSampler`. This is the same convention used by the PostChain backend, ensuring pack compatibility.

---

## 6. Pipeline Design

### Pipeline Type
- **Type**: Vulkan graphics pipeline (not compute)
- **Topology**: `PrimitiveTopology.TRIANGLES`
- **Vertex binding**: None for fullscreen triangle (positions derived from `gl_VertexIndex`); minimal attribute binding for fullscreen quad fallback
- **Depth/Stencil**: Disabled (fullscreen pass does not use depth)
- **Culling**: Disabled
- **Blending**: Disabled (overwrite mode)

### Pipeline Caching

**Key constraint**: Do NOT create a new pipeline per frame.

| Caching level | Strategy |
|---------------|----------|
| Per-frame | Pipeline object created once at activation time |
| Per-resize | Pipeline recreated when main framebuffer size changes |
| Per-shader-change | Pipeline recreated when user pack shaders change |
| Resource reload | Pipeline destroyed and recreated on `F3+T` |

**Cache key**: `(fragmentShaderSource, renderTargetFormat)` — the vertex shader is always the builtin fullscreen triangle/program.

### Bind Group Layout

Follow Snap7 `RenderPipeline.Builder` convention using `BindGroupLayout`:

```
RenderPipeline.builder()
    .withBindGroupLayout(BindGroupLayouts.GLOBALS)
    .withBindGroupLayout(VPFX_FULLSCREEN_INPUT_BIND_GROUP)
    .withVertexBinding(0, DefaultVertexFormat.POSITION)  // only for quad fallback
    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
    .withVertexShader("vpfx_native/fullscreen_passthru")
    .withFragmentShader("vpfx_native/fullscreen_passthru")
    .build()
```

The `VPFX_FULLSCREEN_INPUT_BIND_GROUP` is built once:
```
BindGroupLayout.builder()
    .withSampler("InSampler")
    .build()
```

For user shaders with additional uniform requirements, NR-1 may add `BindGroupLayouts.PROJECTION` or a custom uniform block as needed. NR-1 defers complex uniform injection to NR-1D+.

---

## 7. Resource Model — Read/Write Hazard Analysis

### 7.1 The Core Problem: scene_color and main Are the Same Resource

**Critical Finding**: In the current PostChain execution path ([PostFxExternalTargetRunner.java](file:///Users/thor/Desktop/vulkan-postfx-template-26.2-snapshot-2/src/client/java/com/ionhex975/vulkanpostfx/client/postfx/PostFxExternalTargetRunner.java#L38-L41)), `minecraft:scene_color` is bound to the **same `RenderTarget` object** as `minecraft:main`:

```java
// PostChain path: scene_color = mainTarget = same resource
frame.importExternal("scene_color", mainTarget);   // <-- same mainTarget!
bundle.put(PostChain.MAIN_TARGET_ID, frame.importExternal("main", mainTarget));
```

This works in PostChain because each `PostPass` manages internal framebuffer targets — the PostChain pipeline implicitly clones/copies resources between passes so no single pass reads and writes the same attachment.

**For NR-1 native, a single pass reading `scene_color` and writing `main` would read and write the same framebuffer attachment simultaneously → undefined Vulkan behavior, potential visual corruption, or device loss.**

### 7.2 Solution: Transient Intermediate Target (Recommended)

NR-1 must avoid reading from and writing to the same `RenderTarget`. The recommended approach:

```
Step 1: Before the render pass
  transientColor = new TextureTarget("vpfx_nr1_transient", width, height, false, GpuFormat.RGBA8_UNORM)
  transientColor.copyColorFrom(mainTarget)    // copy mainTarget color → transient

Step 2: During the render pass
  input  = transientColor.colorTextureView    // read from transient COPY
  output = mainTarget.colorTextureView         // write to mainTarget directly
  draw(fullscreenTriangle, 3)

Step 3: After the render pass
  transientColor.destroyBuffers()              // transient is per-frame
```

**Diagram:**

```
┌──────────────────┐     copyColorFrom     ┌──────────────────────┐
│   mainTarget      │ ────────────────────→ │  transientColor       │
│  (same for both   │                       │  (NR-1 owned,         │
│   scene_color     │                       │   per-frame lifetime) │
│   and main)       │                       └──────────┬───────────┘
└────────┬─────────┘                                   │
         │                                             ▼ (read)
         │ (write)                            ┌───────────────┐
         │                                    │ FULLSCREEN     │
         │                                    │ pass draws     │
         └───────────────────────────────────→│ scene_color    │
                                              │ → main         │
                                              └───────────────┘
```

**Resource lifetime:**

| Resource | Lifetime | Allocator |
|----------|----------|-----------|
| `mainTarget` | Frame lifetime (Minecraft-managed) | `GameRenderer.mainRenderTarget()` |
| `transientColor` | Per-frame (created before pass, destroyed after) | NR-1 via `GraphicsResourceAllocator` |
| Pipeline | Session lifetime (resize-invalidated) | NR-1 cache |

### 7.3 Alternative: FrameGraphBuilder Read/Write Annotations

If Snap7 `FrameGraphBuilder` supports declaring a resource as "sampled read" vs "color attachment write" within the same frame graph, and it can insert appropriate pipeline barriers when both refer to the same underlying `RenderTarget`:

```
FrameGraphBuilder.addPass("vpfx_native_fullscreen")
    .reads("minecraft:scene_color")       // sampled, read-only
    .writes("minecraft:main")             // color attachment, write-only
    .execution(fullscreenRenderFunc)
```

**Risk**: Snap7 `FrameGraphBuilder` may not have explicit read/write mode annotations. If it treats both `importExternal` handles as separate resources even when they point to the same `RenderTarget`, the transient-copy approach (7.2) is the safer fallback.

**Decision for NR-1**: Start with transient-copy (7.2). If profiling shows the copy is too expensive and Snap7 FrameGraphBuilder barrier semantics can be validated, switch to annotation-based (7.3) in NR-1D+.

### 7.4 Resize Handling

| Event | Transient Target | Pipeline |
|-------|-----------------|----------|
| Window resize | Recreate transient target (new width/height) | Recreate pipeline (format key may change) |
| Fullscreen toggle | Recreate transient target | Recreate pipeline |
| Resource reload (`F3+T`) | No transient change (destroyed per-frame) | Destroy + recreate pipeline |
| First frame after world load | Create transient target | Create pipeline |

**Transient target is destroyed every frame** — there is no persistent cache for it. Only the pipeline and shader module caches persist across frames.

### 7.5 Fallback Path Resource Behavior

When NR-1 falls back to PostChain (L2):
- The transient target is **not created** — PostChain manages its own internal targets
- The pipeline is **not used** — PostChain PostPass manages its own pipeline
- `mainTarget` passes through PostChain's `addToFrame` → `process` pipeline unchanged

### 7.6 GPU Memory Budget

| Resource | Size (1920×1080 RGBA8) | Lifetime |
|----------|------------------------|----------|
| transientColor | ~8 MB | Per-frame (alloc/destroy) |
| Fullscreen pipeline | <1 MB | Session |
| Shader module cache | <100 KB | Session |

Total NR-1 GPU memory overhead: **<10 MB**, well within budget for modern GPUs.

---

## 8. Error Isolation & Fallback

### Three-Layer Error Containment

```
┌──────────────────────────────────────────────┐
│ Layer 1: Native pass attempt                  │
│   try { renderFullscreenPass(); }             │
├──────────────────────────────────────────────┤
│ Layer 2: Fallback to PostChain                │
│   catch { PostChainBackend.execute(); }       │
├──────────────────────────────────────────────┤
│ Layer 3: Vanilla Minecraft rendering          │
│   (if both native + PostChain fail)           │
└──────────────────────────────────────────────┘
```

### Failure Scenarios

| Scenario | Response |
|----------|----------|
| Shader compilation failure | Log error → fallback to PostChain |
| Pipeline creation failure | Log error → fallback to PostChain |
| `scene_color` not ready | Log warning → skip frame → fallback to PostChain |
| Render pass submission failure | Log error → mark native unavailable for session → fallback to PostChain |
| Resource exhaustion | Log error → fallback to PostChain |
| User shader parse error | Log error → use builtin passthrough shader instead |

### Diagnostics

NR-1 reuses the existing `VpfxFailureDiagnostics.write("native-execute", exception, packName)` infrastructure to write errors to `run/vulkanpostfx_runtime/diagnostics/latest-vpfx-error.txt`.

### Marking Native Unavailable

If the native pass fails during execution (post-pipeline-creation), the runtime should:
1. Log the error.
2. Write a diagnostic file.
3. Set a flag: `VpfxNativeRuntimeState.markNativeExecutionFailed()`.
4. Fallback to PostChain for the remainder of the session.
5. On next pack activation / `F3+T`, retry native.

---

## 9. Feature Flag Design

NR-0 uses `-Dvulkanpostfx.vpfx.nativeRuntime=true` for the dry-run check.

NR-1 adds a **separate** flag for actual execution:

```
-Dvulkanpostfx.vpfx.nativeRuntime.execute=true
```

This avoids accidentally enabling real rendering during NR-0 testing.

### Flag Hierarchy

| Flags active | Behavior |
|--------------|----------|
| (none) | PostChain backend, no native logs |
| `nativeRuntime=true` | NR-0 dry-run check, logs, PostChain fallback |
| `nativeRuntime=true` + `nativeRuntime.execute=true` | NR-1 fullscreen pass attempt, PostChain fallback |

**`nativeRuntime.execute=true` requires `nativeRuntime=true`** — execution is a subset of the dry-run check.

---

## 10. Verification Strategy

### Visual Verification

1. **Passthrough mode** (no pack): Activate NR-1 with native execute flag. The game should look **visually identical** to PostChain/vanilla — no color shift, no artifacts.
2. **Minimal Showcase** (warm tone + vignette): Activate NR-1. The warm tone and vignette effect should match the PostChain path visually.
3. **F8 toggle**: Toggling between vanilla ↔ VPFX effects should work without visual glitch.

### Instrumentation

- Log every pass execution: pack id, shader source, pipeline cache hit/miss, frame time.
- If visual difference is suspected, add a debug mode that writes the native pass output to a temporary render target for side-by-side comparison.

### Performance Baseline

| Metric | Target |
|--------|--------|
| Single-frame GPU time | ≤ PostChain path + 10% |
| Pipeline creation | ≤ 100ms (once, cached) |
| Memory overhead | ≤ 2MB texture + pipeline objects |

---

## 11. Minecraft Snap7 API Surface

NR-1 interacts with the following Snap7 types:

| API Type | Purpose |
|----------|---------|
| `FrameGraphBuilder` | Declare the native pass, import `scene_color`, declare `main` output |
| `RenderPass` | Execute draw commands within the frame graph pass |
| `RenderPipeline` / `RenderPipeline.Builder` | Create and cache the fullscreen pipeline |
| `RenderTarget` / `TextureTarget` | Reference `scene_color` and `main` targets |
| `GraphicsResourceAllocator` | Resource pool for transient allocations |
| `GpuTextureView` | Bind `scene_color` as a sampled texture |
| `GpuSampler` / `RenderSystem.getSamplerCache()` | Linear clamp sampler for scene_color |
| `BindGroupLayout` / `BindGroupLayouts` | Pipeline layout definition |
| `PrimitiveTopology` | `TRIANGLES` for fullscreen triangle/quad |
| `DefaultVertexFormat` | `POSITION` for fullscreen quad fallback |
| `GameRenderer.mainRenderTarget()` | Get the main framebuffer reference |
| `ShaderManager` / `ShaderInstance` | Load pack-provided shaders |
| `RenderSystem.getDevice()` | GPU device for buffer/pipeline creation |

**Key design constraint**: NR-1 runs in the **same render thread** as Minecraft's existing rendering. It does not spawn threads or use async pipeline compilation in the initial NR-1A–NR-1C scope.

---

## 12. Implementation Breakdown

### NR-1A: Logging and Feature Flag ✅ (DONE — 1.10.1)

**Files affected:**
- `VpfxNativeRuntimeSupport.java` — added `SYSTEM_PROPERTY_EXECUTE`, `isExecuteEnabled()`, NR-1A logging in `runDryRunCheck()`
- `docs/vpfx-native-runtime-v0.md` — updated flag reference

**Acceptance criteria:**
- `-Dvulkanpostfx.vpfx.nativeRuntime.execute=true` is recognized at launch
- Log confirms: "Native Runtime v0 execute mode enabled" or "execute mode disabled"

### NR-1B: Native Pass Resource Import (Dry-Run) ✅ (DONE — 1.10.2)

**Files affected:**
- New: `VpfxNativeFullscreenDryRun.java` — logs-only dry-run (obtains mainRenderTarget, logs dimensions/class, scene_color mapping, same-target hazard, transientColor mitigation plan)
- `ActivePostEffectBridge.java` — calls `VpfxNativeFullscreenDryRun.run()` after NR-0 support check

**Acceptance criteria:**
- `scene_color` and `main` resource mappings are logged
- No draw calls — only import/create inspection
- Falls back to PostChain on any resource error

### NR-1C: Resolved Planning Skeleton ✅ (DONE — 1.10.3)

**Files affected:**
- New: `VpfxNativeResolvedResource.java` — resolved resource reference with Role enum, RenderTarget class, dimensions
- New: `VpfxNativeResolvedPass.java` — resolved pass: identity, pass type, resolved inputs/output, reads-scene-color, writes-main, same-target hazard, transient-temp-required flags
- New: `VpfxNativeResolvedGraph.java` — ordered resolved passes, aggregated temp resource count
- New: `VpfxNativeExecutionPlan.java` — top-level plan: pack info, resolved graph, main target resource, hazard flags, would-execute flag, fallback backend
- `VpfxNativeFullscreenDryRun.java` — rewritten to build `VpfxNativeExecutionPlan` before logging (plan-first, not print-first)

**Acceptance criteria:**
- Execution plan built from VPFX graph + Minecraft state
- Plan logged: resolved pass count, temp resource count, hazard, transientColor planned, would-execute
- Dry-run only; no real GPU rendering for v1 pass planning

### NR-1D: Passthrough Shader and Visual Verification ✅ (DONE — 1.10.4+)

**Files affected:**
- `VpfxNativeFullscreenPipeline.java` — render pass + draw call implemented
- `VpfxNativeTransientTargetDryRun.java` — calls `VpfxNativeFullscreenExecutor.execute()` after copy
- `VpfxNativeFullscreenExecutor.java` — builtin passthrough fullscreen draw via `RenderPipeline` + `RenderPass`

**Acceptance criteria:**
- Game renders without visual change in passthrough mode ✅
- No crash, no black screen, no corruption ✅
- F8 toggle works ✅

### NR-1E: Pack Shader Loading (NR-1E-A ✅, NR-1E-B ✅, NR-1E-B.1 ✅, NR-1E-C+ not yet)

**NR-1E-A** (Done — 1.10.4+): `VpfxNativeFullscreenExecutor` — builtin passthrough fullscreen draw via `RenderPipeline` + `RenderPass`, shader `core/vpfx_native_fullscreen_passthru`, fullscreen triangle 3-vertex draw. Returns `VpfxNativeExecutionResult`.

**NR-1E-B** (Done — 1.10.5+): Synchronous pre-PostChain diagnostic draw. Native passthrough draw is attempted immediately before `external.process()` in `PostFxHookBridge.onWorldPostEffectBeforeHand()` via `VpfxNativeTransientTargetDryRun.attemptDiagnosticDraw()`. If native draw succeeds → skip that exact PostChain invocation. If native draw fails → PostChain executes normally. No cross-phase pending skip — draw and skip happen in the same call flow. `PostChainProcessMixin` guard uses `isSkipPostChainThisFrame()` as secondary safety net for all `PostChain.process()` calls.

**NR-1E-B.1** (Done — 1.10.6+): Log throttling and diagnostic counters. Success path logs throttled to first success + every 60 frames. Failure/fallback logs always output immediately. Counters: `nativeDiagnosticDrawSuccessCount`, `postChainSkippedFrameCount`, `nativeDiagnosticDrawFailureCount` in `PostFxRuntimeState` with `nativeDiagnosticSummary()` getter. Summary format: single condensed log line with all key fields + counter totals.

### NR-1F-A (Done — 1.11.0): User Shader Resolve/Preprocess Dry-Run

`VpfxNativeUserShaderDryRun` — dry-run only: resolves user shader references from the active VPFX native pack's graph, reads and preprocesses shader source, logs results. Does NOT execute user shaders. Builtin passthrough remains active native draw shader. Reuses existing `VpfxShaderSourcePreprocessor` for include expansion and uniform injection. Called from `VpfxNativeFullscreenPipeline.runDryRun()`.

### NR-1F-B (Done — 1.11.0): User Shader Pipeline Planning Dry-Run

`VpfxNativeUserShaderDryRun.runPipelinePlanning()` — computes SHA-256 hashes of preprocessed shader source, generates `VpfxNativePipelineKey` with InSampler convention. Logs would-create=true but dry-run does not execute draw. `VpfxNativePipelineKey` extended with vertexSourceHash / fragmentSourceHash / samplerConvention fields.

### NR-1F-C (Done — 1.11.0): User Shader RenderPipeline Create Dry-Run

`VpfxNativeUserShaderDryRun.runCreatePipelineDryRun()` — attempts `RenderPipeline.builder().build()` with materialized user shaders, `BindGroupLayouts.GLOBALS` + InSampler bind group, fullscreen triangle topology. Pipeline object created but NOT used for draw in dry-run path. All errors caught → fallback.

### NR-1F-C.1 (Done — 1.11.0): Deferred RenderPipeline Creation to Render Thread

`VpfxNativeUserShaderDryRun.runCreatePipelineDryRun()` now stores shader data to static fields and marks `pendingUserShaderPipelineCreate` when not on the Render thread. `PostFxHookBridge.onWorldRenderTail()` consumes the flag and calls `runPendingPipelineCreateOnRenderThread()` which performs `RenderPipeline.builder().build()` on the Render thread.

### NR-1F-C.2 (Done — 1.11.0): User Shader RenderPipeline Cache

`VpfxNativeUserPipelineCache` — static cache mapping `VpfxNativePipelineKey` → `RenderPipeline` (successes) and key → reason (failures). Both sync and deferred creation paths are cache-aware: hit → skip creation; miss → build → cache. `PostFxReloadHooks` calls `clear()` on F3+T.

**Remaining:**
- `VpfxNativeFullscreenPipeline.java` — load pack shaders from VPFX graph
- Shader compile error → fallback to builtin passthrough

**Acceptance criteria:**
- Minimal Showcase warm tone + vignette renders correctly
- Shader compile failure falls back to passthrough (visible as no-effect, logged)

### NR-1F: Fallback and Diagnostics (NR-1F-A ✅, NR-1F-B+ not yet)

**NR-1F-A** (Done — 1.11.0): `VpfxNativeUserShaderDryRun` — dry-run user shader resolve + preprocess. Does not execute user shaders.

**Remaining:**
- `VpfxNativeRuntimeBackend.java` — wrap execute in try/catch with PostChain fallback
- `VpfxFailureDiagnostics.java` — add `"native-execute"` stage
- `PostFxRuntimeState.java` — add native execution failed flag

**Acceptance criteria:**
- Any NR-1 failure falls back to PostChain (no black screen)
- Diagnostic file written on failure
- `F3+T` retries native after failure

---

## 13. Key Risks

| Risk | Mitigation |
|------|------------|
| Snap7 API drift between snapshot versions | Pin to 26.2-snapshot-7; defer API migration to port cycles |
| `FrameGraphBuilder` resource import semantics differ from PostChain | Test with passthrough first (no visual change) |
| Fullscreen triangle unsupported by Snap7 vertex binding | Fallback to fullscreen quad with explicit vertex buffer |
| Pipeline cache invalidation on resize not triggering | Add explicit `mainRenderTarget` size change detection |
| User shader compile errors crash the entire pass | Wrap shader compile in try/catch → fallback to builtin passthrough |
| NR-1 GPU time exceeds PostChain path | Profile with passthrough (should be ≤ PostChain overhead) |
| Parallel pipeline compilation stalls render thread | Defer async compilation to NR-1D+; NR-1A–NR-1C use synchronous |

---

## 14. Relationship to PostChain Backend

| Aspect | NR-1 Native | PostChain |
|--------|-------------|-----------|
| Execution | FrameGraphBuilder + RenderPass | PostChain.addToFrame + process |
| Shader loading | `VpfxNativeZipPackLoader` graph → direct GLSL compile | PostChain JSON → PostChainConfig |
| Sampler convention | `sampler_name="In"` → `InSampler` (same) | Same |
| External targets | Import via FrameGraphBuilder | MutableTargetBundle |
| Pipeline | Vulkan pipeline (explicit) | PostPass pipeline (PostChain-managed) |
| Fallback | Falls back to PostChain on error | Always available as L2 fallback |

**NR-1 does NOT remove or bypass the PostChain backend.** PostChain remains L2 fallback and the default for all other pass types.

---

## 15. Compliance

| Constraint | Status |
|------------|--------|
| No compute shaders | ✅ |
| No volume / cloud / RT | ✅ |
| No multi-pass | ✅ |
| No custom targets | ✅ |
| No scene_depth / shadow_depth | ✅ |
| Default backend unchanged | ✅ |
| VPFX v1 schema unchanged | ✅ |
| PostChain backend intact | ✅ |
| PostFxHookBridge unchanged | ✅ |
| Shadow renderer unchanged | ✅ |
