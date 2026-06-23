# VPFX Pack Format v1

**This is an ALPHA spec.** Fields, validation rules, and execution semantics may change in future versions.
Pack authors should expect minor breaking changes between VPFX releases during the alpha phase.

**Current execution backend**: Minecraft PostChain backend (transitional — the native VPFX runtime is not yet complete).  
**VPFX native runtime**: Not yet available. The format is designed to be backend-agnostic so pack authors will not need to update their graphs when the backend changes.  
**Validator codes**: C001–C005 (capabilities), G002–G017 (graph), S001–S005 (shader), I001–I006 (include), F001–F008 (pack manifest), Z001 (zip)

> **Important limitations (alpha)**:
> - `compute` capability is **unsupported** — packs must set `compute: false`
> - `shadow_depth` capability is **experimental** — packs using it may see visual issues
> - The current runtime uses Minecraft's built-in PostChain as the execution backend
> - This is NOT the final VPFX native runtime architecture

---

## 1. Pack Structure

A VPFX native pack is a `.zip` file with the following minimal structure:

```
my_pack.zip
├── pack.json                  # Pack manifest (required)
├── post_effect/               # Entry post effect graph(s)
│   └── main.json              # Default entry graph
└── shaders/                   # All shader sources
    ├── post/
    │   ├── fullscreen.vsh
    │   └── my_effect.fsh
    ├── composite/
    │   ├── final.vsh          # Non-post/ path is also valid
    │   └── final.fsh
    └── include/
        └── helpers.glsl
```

> **Path flexibility**: VPFX v1 does not require shaders to be under `post/`. The `composite/final` example above demonstrates a non-post/ shader path, which maps to namespace references like `<pack_id>:composite/final`.

---

## 2. pack.json Fields

`pack.json` is the root manifest. All VPFX native packs **must** contain one.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `format_version` | int | **Yes** | Must be `1` |
| `pack_id` | string | **Yes** | Unique pack identifier. Pattern: `^[a-z0-9_.-]{3,64}$`. Used as namespace for pack-local shaders and targets. |
| `name` | string | **Yes** | Human-readable display name |
| `version` | string | **Yes** | Semantic version string (e.g. `"1.0.0"`) |
| `author` | string | No | Author name |
| `description` | string | No | Human-readable description |
| `entry_post_effect` | string | **Yes** | Path to the entry graph JSON inside the zip. e.g. `post_effect/main.json` |
| `capabilities` | object | **Yes** | See §3 |
| `targets` | object | Conditional | See §7. Required if `shadow_depth` capability is enabled. |
| `textures` | object | No | See §8 |
| `metadata` | object | No | See §9 |

### 2.1 Minimal Example

```json
{
  "format_version": 1,
  "pack_id": "my_first_pack",
  "name": "My First Pack",
  "version": "1.0.0",
  "entry_post_effect": "post_effect/main.json",
  "capabilities": {
    "scene_color": true,
    "scene_depth": false,
    "shadow_depth": false,
    "custom_targets": true,
    "compute": false
  }
}
```

---

## 3. Capabilities

Defines what runtime features the pack requires. All five fields are **required** booleans.

| Field | Type | Status | Description |
|-------|------|--------|-------------|
| `scene_color` | bool | ✅ Supported | Pack needs to read the main scene color buffer |
| `scene_depth` | bool | ✅ Supported | Pack needs to read the scene depth buffer |
| `shadow_depth` | bool | ⚠️ Experimental | Pack needs to read the shadow depth buffer |
| `custom_targets` | bool | ✅ Supported | Pack declares custom internal render targets |
| `compute` | bool | ❌ Unsupported | Pack requires compute shader support. **Must be `false`.** |

**Alpha limitations**:
- `compute`: The PostChain backend does not support compute shaders. Packs with `compute: true` will be rejected (C005). Even if you set it to `false`, you cannot declare compute passes — the validator only checks capabilities at this stage.
- `shadow_depth`: Shadow depth is available but experimental. Visual artifacts may occur. Packs should set `shadow_depth: false` unless they explicitly need shadow sampling.

The runtime will refuse to load a pack whose capabilities exceed what the runtime provides.

### Validator Codes (Capabilities)

| Code | Meaning |
|------|---------|
| C001 | scene_color not provided by runtime |
| C002 | scene_depth not provided by runtime |
| C003 | shadow_depth not provided by runtime |
| C004 | custom_targets not provided by runtime |
| C005 | compute not supported (must be false) |

---

## 4. Graph JSON Format

The entry post effect JSON (e.g. `post_effect/main.json`) has this structure:

```json
{
  "targets": { ... },
  "passes": [ ... ]
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `targets` | object | **Yes** | Map of internal render target declarations. May be `{}`. |
| `passes` | array | **Yes** | Ordered array of pass definitions. At least 1 required. |

---

## 5. Targets

### 5.1 Internal Targets (`targets` block)

Each key is a namespaced target ID (e.g. `mypack:bloom_early`). Each value is an object:

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `scale` | number | No | `1.0` | Output resolution relative to screen. Range: `(0, 1]`. e.g. `0.5` = half resolution. |
| `use_depth` | bool | No | `false` | Whether this target has a depth attachment. Required for targets read with `use_depth_buffer: true`. |
| `clear_color` | number[4] | No | `[0,0,0,0]` | RGBA clear color in `[0, 1]` range. |

### 5.2 Builtin Targets

These are always available and do NOT need to be declared:

| Identifier | Description | Runtime Source |
|------------|-------------|----------------|
| `minecraft:main` | Main framebuffer color+depth | PostChain.MAIN_TARGET_ID |
| `minecraft:scene_color` | Scene color (alias for main color) | main target color |
| `minecraft:scene_depth` | Scene depth buffer | Captured from main target |
| `minecraft:shadow_depth` | Shadow map depth | ShadowRenderTargetsLite |
| `vulkanpostfx:scene_depth` | Scene depth (mod-native namespace) | SceneDepthCaptureTargets |
| `vulkanpostfx:shadow_depth` | Shadow map depth (mod-native namespace) | ShadowRenderTargetsLite |

> **Note**: `vulkanpostfx:scene_depth` and `vulkanpostfx:shadow_depth` are the actual runtime targets.
> `minecraft:scene_color`, `minecraft:scene_depth`, and `minecraft:shadow_depth` are accepted as aliases by the validator but may map to fallback targets at runtime.

---

## 6. Passes

Each pass in the `passes` array is executed in order.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | string | No | Stable pass identifier for error messages and debugging |
| `debug_label` | string | No | Human-readable label for error messages |
| `vertex_shader` | string | **Yes** | Shader reference (see §10) for the vertex stage |
| `fragment_shader` | string | **Yes** | Shader reference (see §10) for the fragment stage |
| `inputs` | array | **Yes** | Array of input bindings. At least 1 required. |
| `output` | string | **Yes** | Namespaced target ID this pass writes to |

### 6.1 Pass Inputs

Each input object:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `sampler_name` | string | **Yes** | GLSL sampler uniform name. Pattern: `^[A-Za-z_][A-Za-z0-9_]*$`. Must be unique per pass. |
| `target` | string | Exactly one of `target`/`texture` | Namespaced target ID to bind as the sampler input |
| `texture` | string | Exactly one of `target`/`texture` | Logical texture name (declared in pack.json textures) |
| `use_depth_buffer` | bool | No | If `true`, bind the target's depth texture instead of its color. Only valid with `target` inputs, not `texture`. |

### 6.2 Example Pass

```json
{
  "id": "grayscale_convert",
  "debug_label": "Convert to grayscale",
  "vertex_shader": "mypack:post/fullscreen",
  "fragment_shader": "mypack:post/grayscale",
  "inputs": [
    {
      "sampler_name": "In",
      "target": "minecraft:main"
    }
  ],
  "output": "mypack:swap"
}
```

---

## 7. Target Mappings (`pack.json` `targets`)

When a pack references platform targets by logical names, it can declare `targets` to map logical names to concrete runtime target IDs:

```json
{
  "targets": {
    "shadow_depth": "vulkanpostfx:shadow_depth"
  }
}
```

This is **required** when `shadow_depth` capability is `true`, because your graph should map the logical `shadow_depth` name to the actual runtime target.

---

## 8. Textures (`pack.json` `textures`)

Declare custom textures that passes can bind as inputs via `texture` (instead of `target`).

```json
{
  "textures": {
    "noise_map": {
      "path": "textures/noise.png",
      "filter": "linear",
      "wrap": "repeat"
    }
  }
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `path` | string | **Yes** | Path to the texture file inside the zip |
| `filter` | string | No | `"nearest"` or `"linear"` (default: `"nearest"`) |
| `wrap` | string | No | `"clamp"` or `"repeat"` (default: `"clamp"`) |

---

## 9. Metadata (`pack.json` `metadata`)

Optional metadata block:

| Field | Type | Description |
|-------|------|-------------|
| `homepage` | string | Project homepage URL |
| `license` | string | License name |
| `tags` | string[] | Array of tags |

---

## 10. Shader Path Mapping

All shader references use the format:

```
<namespace>:<path>
```

This maps to files inside the zip as:

```
shaders/<path>.vsh    (vertex shader)
shaders/<path>.fsh    (fragment shader)
```

### 10.1 Namespace rules

| Namespace | Source | Example Path |
|-----------|--------|-------------|
| `vulkanpostfx` | Builtin shaders shipped with the mod | `vulkanpostfx:post/fullscreen` → `assets/vulkanpostfx/shaders/post/fullscreen.vsh` |
| `vulkanpostfx` (core/) | Minecraft core shader overrides | `vulkanpostfx:core/shadow_terrain` → `assets/minecraft/shaders/core/shadow_terrain.vsh` |
| `<pack_id>` | Shaders inside the zip pack | `mypack:post/bloom` → `shaders/post/bloom.vsh` (inside zip) |

### 10.2 Path rules

- Paths may use any directory structure under `shaders/`
- **No `post/` prefix requirement** — any valid subdirectory is accepted
- Path escape (`..`) is **rejected**
- Absolute paths (starting with `/`) are **rejected**
- Backslashes (`\`) are **rejected**
- `.glsl` files are treated as include libraries and are **not** entry points

---

## 11. Include System

Shaders can use C-style `#include` directives:

```glsl
#include "include/helpers.glsl"
```

### 11.1 Resolution

| Include Path Style | Resolution |
|--------------------|------------|
| `shaders/include/foo.glsl` | Absolute zip path, starting from `shaders/` |
| `include/foo.glsl` | Relative to the current shader's directory |
| `../` (any) | **Rejected** (path escape) |

### 11.2 Rules

- Maximum recursion depth: 32
- Include cycles are detected and rejected
- `.glsl` files are preprocessed (include expansion) but do **not** receive builtin uniform injection
- `.vsh`/`.fsh` files receive both include expansion **and** builtin uniform injection

### Include Validator Codes

| Code | Meaning |
|------|---------|
| I001 | Include depth exceeded |
| I002 | Include cycle detected |
| I003 | Blank include path |
| I004 | Path traversal (`..`) in include path |
| I005 | Included file not found in zip |
| I006 | Failed to read included file |

---

## 12. Graph Validation Rules

The validator checks the graph against the following rules (all **fatal** — pack will not load if violated):

| Code | Check |
|------|-------|
| G002 | Graph must contain at least 1 pass |
| G003 | Each pass must have at least 1 input |
| G004 | Output target must be a declared internal target or a builtin target |
| G005 | Input target must reference a declared internal target or a builtin target |
| G006 | `use_depth_buffer=true` requires the target to have a depth attachment |
| G007 | Target identifiers must match the expected namespaced pattern |
| G008 | Sampler names must be valid GLSL-safe identifiers |
| G009 | Sampler names must be unique within a pass |
| G011 | Target scale must be finite and in `(0, 1]` |
| G012 | Each input must have exactly one of `target` or `texture` |
| G013 | Texture inputs must reference declared textures |
| G014 | `texture` inputs do not support `use_depth_buffer` |
| G015 | **Pass must not read from its own output** (self-read/write) |
| G016 | **Graph must write to `minecraft:main`** — at least one pass must output to the main framebuffer |
| G017 | **Pass must not read from a target that has not been written yet** (future dependency). Builtin targets are exempt. |

### Shader Validator Codes

| Code | Meaning |
|------|---------|
| S001 | Vertex shader file not found in zip |
| S002 | Fragment shader file not found in zip |
| S003 | Invalid shader path (blank, escape, or absolute) |
| S004 | Vertex shader preprocessing error |
| S005 | Fragment shader preprocessing error |

### Manifest Validator Codes

| Code | Meaning |
|------|---------|
| F001 | Missing pack.json |
| F002 | Unsupported format_version |
| F003 | Invalid pack_id |
| F005 | entry_post_effect file not found |
| F006 | Invalid manifest field |
| F007 | Missing required target mapping |
| F008 | Invalid texture definition |

### 12.1 Error messages include pass identity

When a pass has an `id` or `debug_label`, error messages reference it:

```
[FATAL][G015][pass[grayscale_convert]] Pass writes to 'mypack:swap' but also reads from the same target...
```

Without an `id`, the fallback is:

```
[FATAL][G015][passes[0]] Pass writes to 'mypack:swap' but also reads from the same target...
```

---

## 13. Current Runtime Backend

VPFX v1 defines a **runtime backend abstraction** (`VpfxRuntimeBackend`) to separate the graph format from the execution engine.
The current default backend is the **Minecraft PostChain Backend** (`minecraft_postchain`, implementation: `VpfxPostChainBackend`).

This is a **transitional** alpha architecture:

- `VpfxRuntimeBackend` interface: `id()`, `displayName()`, `capabilities()`, `materialize()`
- `VpfxRuntimeBackendCapabilities`: `usesPostChain=true`, `nativeRuntime=false`, `supportsCompute=false`, `supportsShadowDepth=true`, `supportsCustomTargets=true`
- Graph passes are transformed into a PostChain JSON format
- Render targets are managed through Minecraft's `RenderTarget` / `FrameGraphBuilder` system
- Builtin external targets (`scene_depth`, `shadow_depth`) are provided by the mod's hook layer

**Alpha note**: The native VPFX runtime — which would bypass PostChain entirely — is not yet implemented.
When it arrives, **pack authors will not need to change their graphs** — the graph format is backend-agnostic, and the `VpfxRuntimeBackend` interface will accept new implementations without changing the pack schema.

---

## 14. Runtime Log Identification

When the mod starts with a VPFX native pack active, the log will show:

```
[vulkanpostfx] VPFX native pack loaded: id='mypack', name='My Pack', ...
[vulkanpostfx] Active post effect source loaded from zip: .../mypack.zip!/post_effect/main.json ...
```

This confirms that the pack was loaded through the VPFX native pack path with the PostChain backend.

---

## 15. Complete Example

### pack.json

```json
{
  "format_version": 1,
  "pack_id": "example_pack",
  "name": "Example VPFX Pack",
  "version": "1.0.0",
  "author": "VPFX User",
  "description": "A minimal full-screen grayscale pass with a bloom prep pass.",
  "entry_post_effect": "post_effect/main.json",
  "capabilities": {
    "scene_color": true,
    "scene_depth": false,
    "shadow_depth": false,
    "custom_targets": true,
    "compute": false
  },
  "textures": {
    "noise_map": {
      "path": "textures/noise.png",
      "filter": "linear",
      "wrap": "repeat"
    }
  },
  "metadata": {
    "homepage": "https://github.com/example/vpfx-pack",
    "license": "MIT",
    "tags": ["demo", "greyscale"]
  }
}
```

### post_effect/main.json

```json
{
  "targets": {
    "example_pack:bloom_prep": {
      "scale": 0.5,
      "use_depth": false
    },
    "example_pack:bloom_blur": {
      "scale": 0.5,
      "use_depth": false
    }
  },
  "passes": [
    {
      "id": "bloom_threshold",
      "debug_label": "Bloom threshold extraction",
      "vertex_shader": "example_pack:post/fullscreen",
      "fragment_shader": "example_pack:post/bloom_threshold",
      "inputs": [
        {
          "sampler_name": "In",
          "target": "minecraft:main"
        }
      ],
      "output": "example_pack:bloom_prep"
    },
    {
      "id": "bloom_blur_h",
      "debug_label": "Bloom horizontal blur",
      "vertex_shader": "example_pack:post/fullscreen",
      "fragment_shader": "example_pack:post/bloom_blur_h",
      "inputs": [
        {
          "sampler_name": "In",
          "target": "example_pack:bloom_prep"
        }
      ],
      "output": "example_pack:bloom_blur"
    },
    {
      "id": "final_composite",
      "debug_label": "Combine bloom with original",
      "vertex_shader": "example_pack:post/fullscreen",
      "fragment_shader": "example_pack:post/final_composite",
      "inputs": [
        {
          "sampler_name": "SceneSampler",
          "target": "minecraft:main"
        },
        {
          "sampler_name": "BloomSampler",
          "target": "example_pack:bloom_blur"
        },
        {
          "sampler_name": "NoiseSampler",
          "texture": "noise_map"
        }
      ],
      "output": "minecraft:main"
    }
  ]
}
```

---

## 16. Validation Summary

A valid v1 graph:

1. Has at least one pass
2. Declares all non-builtin output targets
3. Every pass references only declared or builtin input targets
4. Does not have self-read/write cycles
5. Writes to `minecraft:main` from at least one pass
6. Only reads from targets that have been written by a previous pass
7. All shader files exist and can be preprocessed (includes expanded)
8. All capability requirements match the runtime
9. `compute` capability is `false`
10. Pack uses only valid, non-escaping shader paths
