# VPFX v1 Alpha — Authoring a Minimal Pack

This guide walks through creating the smallest possible VPFX v1 pack that:
- Passes the validator
- Loads through `VpfxNativeZipPackLoader`
- Materializes to a runtime resource pack

---

## 1. Directory Structure

```
my_minimal_pack/
├── pack.json                      # Pack manifest
├── post_effect/
│   └── main.json                  # Graph definition
└── shaders/
    └── composite/
        ├── final.vsh              # Vertex shader
        └── final.fsh              # Fragment shader
```

> **Important**: The `composite/` path is intentional. VPFX v1 does **not** require shaders to be under `post/`. Any valid directory under `shaders/` is accepted. Using a non-`post/` path like `composite/` verifies that your pack is following the v1 path rules correctly.

---

## 2. pack.json

```json
{
  "format_version": 1,
  "pack_id": "my_minimal_pack",
  "name": "My Minimal VPFX Pack",
  "version": "1.0.0",
  "author": "Your Name",
  "description": "A minimal VPFX v1 alpha pack",
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

**Key points**:
- `pack_id` must match `^[a-z0-9_.-]{3,64}$` — lowercase, dots/hyphens/underscores only
- `scene_color: true` because the pass reads from `minecraft:main`
- `compute: false` — compute shaders are **unsupported** in alpha
- `shadow_depth: false` — experimental, leave disabled unless you need shadow sampling
- `custom_targets: true` — internal targets (like the `swap` buffer) require this

---

## 3. post_effect/main.json (Graph)

```json
{
  "targets": {
    "my_minimal_pack:swap": {}
  },
  "passes": [
    {
      "id": "apply_effect",
      "debug_label": "Apply minimal effect",
      "vertex_shader": "my_minimal_pack:composite/final",
      "fragment_shader": "my_minimal_pack:composite/final",
      "inputs": [
        {
          "sampler_name": "In",
          "target": "minecraft:main"
        }
      ],
      "output": "my_minimal_pack:swap"
    },
    {
      "id": "blit_to_main",
      "debug_label": "Copy swap result to main",
      "vertex_shader": "my_minimal_pack:composite/final",
      "fragment_shader": "my_minimal_pack:composite/final",
      "inputs": [
        {
          "sampler_name": "In",
          "target": "my_minimal_pack:swap"
        }
      ],
      "output": "minecraft:main"
    }
  ]
}
```

**Key points**:
- **Two passes**: Process → Blit. The first writes to a swap target, the second writes to `minecraft:main`.
- **G016 satisfied**: At least one pass (the blit) outputs to `minecraft:main`.
- **G017 satisfied**: Pass 0 writes to `swap`, Pass 1 reads from `swap` — `swap` was written before it was read.
- **G015 NOT violated**: Reading from `minecraft:main` (builtin target) is allowed because the validator exempts builtin targets from self-read/write checks.
- **`id` + `debug_label`** are optional but recommended — they appear in validator error messages.
- **Shader reference**: `my_minimal_pack:composite/final` → maps to `shaders/composite/final.vsh` and `shaders/composite/final.fsh` inside the zip.

### Simpler (1-pass) Alternative

You can also use just one pass that reads and writes `minecraft:main`:

```json
{
  "targets": {},
  "passes": [
    {
      "id": "final_composite",
      "vertex_shader": "my_minimal_pack:composite/final",
      "fragment_shader": "my_minimal_pack:composite/final",
      "inputs": [
        {
          "sampler_name": "In",
          "target": "minecraft:main"
        }
      ],
      "output": "minecraft:main"
    }
  ]
}
```

The validator exempts builtin targets from the self-read/write rule (G015), so reading and writing `minecraft:main` in the same pass is allowed.

---

## 4. Shaders

### composite/final.vsh

```glsl
#version 150

out vec2 texCoord;

void main() {
    vec2 pos;
    if (gl_VertexID == 0) {
        pos = vec2(-1.0, -1.0);
    } else if (gl_VertexID == 1) {
        pos = vec2(3.0, -1.0);
    } else {
        pos = vec2(-1.0, 3.0);
    }
    texCoord = pos * 0.5 + 0.5;
    gl_Position = vec4(pos, 0.0, 1.0);
}
```

### composite/final.fsh

```glsl
#version 150

uniform sampler2D InSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    fragColor = texture(InSampler, texCoord);
}
```

This is a passthrough (blit) shader — reads from `InSampler` and writes unchanged.

---

## 5. Zipping

Zip the directory contents (not the directory itself):

```bash
cd my_minimal_pack
zip -r ../my_minimal_pack.zip ./*
```

The zip root should contain `pack.json`, not `my_minimal_pack/pack.json`.

---

## 6. Testing

### Local validation
```bash
# Copy your pack into the test packs directory
cp my_minimal_pack.zip src/test/resources/vpfx_test_packs/positive_my_minimal/
# ... or more commonly, after extracting:
# mv positive_minimal <your-pack-dir> and replace the files

# Run validation
./gradlew validateTestPacks
```

### In-game testing
```bash
# Copy to shaderpacks
cp my_minimal_pack.zip run/shaderpacks/

# Edit config
echo '{"active_pack_id": "my_minimal_pack"}' > run/config/vulkanpostfx.json

# Launch
./gradlew runClient
```

Press `F8` to toggle VPFX effects on/off.

---

## 7. Common Validator Errors

| Error | Code | Fix |
|-------|------|-----|
| "Input target not found" | G005 | Make sure your target IDs match the `pack_id`. If `pack_id` is `my_pack`, targets must be `my_pack:something`. |
| "Output target not declared" | G004 | Declare all non-builtin output targets in the `targets` block. Builtin targets (`minecraft:main`) don't need declaration. |
| "Graph does not write to minecraft:main" | G016 | At least one pass must have `"output": "minecraft:main"`. |
| "Pass reads from target … that has not been written yet" | G017 | Reorder your passes so each target is written by a pass before any other pass reads it. |
| "Invalid pack_id" | F003 | Use lowercase letters, digits, dots, hyphens, or underscores. 3-64 characters. No spaces. |
| "Vertex shader file not found" | S001 | Check the shader reference format: `<pack_id>:<path>` maps to `shaders/<path>.vsh`. Make sure the file exists in the zip. |
| "Invalid shader path" | S003 | Remove `..` and leading `/` from shader paths. |
| "Texture input not declared" | G013 | If using `texture` instead of `target` in an input, declare it in `pack.json`→`textures`. |

---

## 8. Path Rules Summary

| Rule | Example (valid) | Example (rejected) |
|------|----------------|---------------------|
| Any subdirectory under `shaders/` | `composite/final` | — |
| No `post/` prefix required | `effects/bloom` | — (old packs: `post/` still works but is not required) |
| No `..` path escape | — | `../secret/bad` |
| No absolute paths (leading `/`) | — | `/shaders/bad` |
| No backslashes | — | `post\\bad` |

---

## 9. Next Steps

1. Modify `final.fsh` with a simple effect (e.g., invert colors, grayscale, color tint)
2. Add multiple passes with intermediate targets
3. Use `use_depth_buffer: true` to sample scene depth (requires `scene_depth: true` in capabilities)
4. Add `#include` directives to share helper functions
5. Declare custom textures in `pack.json`→`textures` and reference them via `texture` inputs
