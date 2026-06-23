# VPFX v1 Alpha — Troubleshooting Guide

## If a VPFX pack fails to load

First, collect these three items:
1. `run/logs/latest.log`
2. `run/vulkanpostfx_runtime/diagnostics/latest-vpfx-error.txt` (if it exists)
3. The shader pack `.zip` file itself

---

## Error A: "Referenced external targets are not available"

```
Referenced external targets are not available in this context: [minecraft:scene_color]
```

### Cause

The Minecraft PostChain backend does not recognize the target name. VPFX requires specific external target IDs to be registered in `PostFxExternalTargetIds.allowedTargets()`.

### How to check

Search `latest.log` for:
```
providedExternalTargets=
```

Expected output:
```
providedExternalTargets=[minecraft:main, minecraft:scene_color, vulkanpostfx:scene_depth, vulkanpostfx:shadow_depth]
```

If `minecraft:scene_color` is missing from this list, your VPFX version may be older than 1.9.0.

### Fix

- Update to VPFX 1.9.0 or later.
- If using `minecraft:scene_depth` or `minecraft:shadow_depth`, ensure your VPFX version supports them.
- Check `docs/vpfx-pack-format-v1.md` for the list of builtin targets.

---

## Error B: "Unable to find shader defined uniform"

```
Unable to find shader defined uniform (InSamplerSampler)
```

### Cause

The PostChain JSON `sampler_name` does not match the shader uniform declaration. Minecraft's PostChain backend automatically appends `Sampler` to the sampler name from the JSON.

### PostChain Sampler Convention

| VPFX Graph `sampler_name` | PostChain lookup | Shader GLSL |
|---|---|---|
| `"In"` | `InSampler` | `uniform sampler2D InSampler;` ✅ |
| `"InSampler"` | `InSamplerSampler` ❌ | `uniform sampler2D InSampler;` |

### Fix

Change the VPFX graph `sampler_name` to the base name without `Sampler`:

```json
// WRONG
{ "sampler_name": "InSampler" }

// CORRECT (PostChain convention)
{ "sampler_name": "In" }
```

The shader itself keeps `uniform sampler2D InSampler;`.

This convention follows Minecraft's builtin post effects (e.g. `debug_invert.json` uses `"sampler_name": "In"` with `uniform sampler2D InSampler;` in the shader).

---

## Error C: "Pipeline is not valid"

```
java.lang.IllegalStateException: Pipeline is not valid (may contain invalid shaders?)
```

### Cause

This is a secondary error — a shader or pipeline creation failure occurred earlier, and the system reports that the resulting pipeline cannot be used.

### How to debug

Look **above** this error in `latest.log` for the root cause:
- `Unable to find shader defined uniform` → see Error B
- `shader compilation error` → check GLSL syntax
- `VK_ERROR_*` → Vulkan/MoltenVK backend issue

The `latest-vpfx-error.txt` diagnostic file (if generated) will also contain the original exception.

---

## Error D: "Graph does not write to minecraft:main"

```
[FATAL][G016][passes] Graph does not write to minecraft:main. At least one pass must output to minecraft:main to produce a visible result.
```

### Cause

No pass in the VPFX graph writes its output to `minecraft:main`.

### Fix

Add a pass that outputs to `minecraft:main`:

```json
{
  "passes": [
    {
      "inputs": [
        { "sampler_name": "In", "target": "my_pack:processed" }
      ],
      "output": "minecraft:main"
    }
  ]
}
```

The validator will reject the pack without this (G016).

---

## Error E: "Shader path contains .."

```
[FATAL][S003] Invalid shader path: must not be blank, escape, or absolute
```

### Cause

A shader reference or `#include` directive contains `..` (parent directory traversal).

### Fix

Use only forward paths within the `shaders/` directory:

```
// WRONG
"vertex_shader": "my_pack:../secret/bad"
#include "../helpers.glsl"

// CORRECT
"vertex_shader": "my_pack:effects/bloom"
#include "include/helpers.glsl"
```

---

## Error F: "Texture input not declared"

```
[FATAL][G013] Input texture not declared: my_texture
```

### Cause

A pass input uses `"texture": "my_texture"` but `my_texture` is not declared in `pack.json` → `textures`.

### Fix

Declare the texture in `pack.json`:

```json
{
  "textures": {
    "my_texture": {
      "path": "textures/effect/noise.png",
      "filter": "linear",
      "wrap": "repeat"
    }
  }
}
```

---

## Error G: "Pass reads from target … that has not been written yet"

```
[FATAL][G017] Pass reads from target 'my_pack:bloom' that has not been written yet (pass index 0).
```

### Cause

A pass reads from an internal target that has not been written by any prior pass.

### Fix

Reorder your passes so that each target is written by a pass **before** any other pass reads from it.

---

## Error H: "Self-read/write cycle"

```
[FATAL][G015] Pass writes to 'my_pack:swap' but also reads from the same target (self-read/write).
```

### Cause

A pass both reads from and writes to the same internal target. The VPFX PostChain backend does not support self-read/write.

### Fix

Use a separate intermediate target. Write to `swap_a` in pass 0, then read from `swap_a` and write to `minecraft:main` in pass 1.

**Note**: Reading from `minecraft:main` (a builtin target) while writing to it is allowed — the validator exempts builtin targets from the self-read/write check.

---

## General Debugging Steps

### 1. Run the smoke tests

```bash
./gradlew validateTestPacks
./gradlew validateMaterialization
```

This validates the pack format without needing to launch Minecraft.

### 2. Check the diagnostic file

```
run/vulkanpostfx_runtime/diagnostics/latest-vpfx-error.txt
```

This file contains:
- Timestamp
- Pack name and externalPostEffectId
- Backend ID and capabilities
- Failure stage (load / compile / addToFrame/execute / process)
- Exception class and message
- Whether the pack was marked unavailable
- Full stack trace

### 3. Check the game log

```bash
grep "vulkanpostfx.*ERROR" run/logs/latest.log
grep "vulkanpostfx.*FATAL" run/logs/latest.log
```

### 4. Verify your pack schema

See [docs/vpfx-pack-format-v1.md](vpfx-pack-format-v1.md) for the complete VPFX v1 alpha format spec.

### 5. Compare with the working example

Study [examples/vpfx-minimal-showcase/](../examples/vpfx-minimal-showcase/) for a known-working minimal VPFX pack.

---

## What to Provide When Reporting Bugs

When reporting a VPFX pack loading failure, include:
1. **latest.log** — `run/logs/latest.log`
2. **Diagnostic file** — `run/vulkanpostfx_runtime/diagnostics/latest-vpfx-error.txt` (if it exists)
3. **Shader pack zip** — the `.zip` file you're trying to load
4. **Versions** — Minecraft version, Fabric Loader version, VPFX version
5. **Steps to reproduce** — what you did before the error occurred
