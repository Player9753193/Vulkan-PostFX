# positive_minimal — VPFX v1 Minimal Valid Pack

This is the canonical minimal VPFX v1 test pack. It validates the smallest possible valid graph: a single pass that reads from `minecraft:main` and writes back to `minecraft:main`.

## Purpose

This pack is used by:
- `VpfxPackValidationSmokeTest` — verifies the pack loads through `VpfxNativeZipPackLoader`
- `VpfxRuntimeMaterializationSmokeTest` — verifies the pack materializes to a runtime resource pack

## Pack Structure

```
positive_minimal/
├── pack.json                        # Pack manifest
├── post_effect/
│   └── main.json                    # Graph definition (1 pass)
└── shaders/
    └── composite/                   # Non-post/ shader path (intentional)
        ├── final.vsh                # Fullscreen quad vertex shader
        └── final.fsh                # Passthrough fragment shader
```

## pack.json

```json
{
  "format_version": 1,
  "pack_id": "official_test_minimal",
  "name": "VPFX v1 Official Minimal Test",
  "version": "1.0.0",
  "author": "VPFX Test Suite",
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

- `format_version`: Must be `1` (only supported version).
- `pack_id`: `official_test_minimal` — used as the shader namespace (`official_test_minimal:composite/final`).
- `scene_color`: `true` because the pass reads from `minecraft:main`.
- `compute`: `false` — compute shaders are unsupported in VPFX v1 alpha.

## post_effect/main.json

```json
{
  "targets": {},
  "passes": [
    {
      "id": "final_composite",
      "debug_label": "Minimal composite to main",
      "vertex_shader": "official_test_minimal:composite/final",
      "fragment_shader": "official_test_minimal:composite/final",
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

- **1 pass**: read `minecraft:main` → fullscreen quad → write `minecraft:main`
- **Pass id**: `final_composite` (used in validator error messages)
- **Shader path**: `composite/final` — intentionally NOT under `post/` to verify non-post/ shader path mapping
- Shader reference `official_test_minimal:composite/final` maps to:
  - `shaders/composite/final.vsh` (vertex)
  - `shaders/composite/final.fsh` (fragment)
- **Sampler convention**: VPFX graph uses `sampler_name: "In"`. The Minecraft PostChain backend automatically appends `Sampler` to the sampler name, so the actual GLSL uniform is `uniform sampler2D InSampler;`. This convention follows the builtin `debug_invert`/`debug_grayscale` post effects.

## Why `composite/final` (non-post/ path)?

VPFX v1 **no longer requires** shader paths to start with `post/`. Any valid subdirectory under `shaders/` is accepted. This pack intentionally uses `composite/final` to verify that:

1. Non-post/ paths pass the validator
2. `VpfxNativeZipPackLoader` maps them correctly (`shaders/composite/final.vsh/.fsh`)
3. `ZipPackMaterializer` materializes them correctly
4. The runtime resource pack contains them at the expected paths

## Shaders

Both vertex and fragment shaders are minimal GLSL 1.50:

**final.vsh**: Fullscreen triangle covering the entire viewport. Uses `gl_VertexID` to generate positions without vertex buffers.

**final.fsh**: Passthrough — reads from `InSampler` and outputs the color unchanged. This is the simplest possible post effect (a blit/no-op).

## How to Verify

```bash
# Validate that this pack loads
./gradlew validateTestPacks

# Validate that this pack materializes to runtime resources
./gradlew validateMaterialization
```
