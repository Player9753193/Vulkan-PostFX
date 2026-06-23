# VPFX Minimal Showcase — Official Example Pack

> **VPFX v1 alpha** official minimal showcase pack.  
> Demonstrates the simplest possible VPFX v1 pack with a subtle visual effect.

---

## What This Is

A minimal VPFX v1 alpha shader pack that applies:
- Gentle contrast boost
- Warm tone shift
- Subtle vignette

using **only** `scene_color` — no depth, no shadow, no compute, no custom targets.

---

## Pack Structure

```
vpfx-minimal-showcase/
├── README.md
├── pack.json                      # VPFX v1 alpha manifest
├── post_effect/
│   └── main.json                  # 1-pass graph
└── shaders/
    └── composite/                 # Non-post/ shader path
        ├── final.vsh              # Fullscreen quad vertex shader
        └── final.fsh              # Warm contrast + vignette
```

### Why `composite/final`?

This pack uses `composite/final` — **not** `post/final`.  
VPFX v1 does **not** require shaders to be under `post/`. Any valid subdirectory under `shaders/` is accepted.  
This pack serves as a demonstration of non-post/ shader path mapping.

The shader reference `vpfx_minimal_showcase:composite/final` maps to:
- `shaders/composite/final.vsh` (vertex)
- `shaders/composite/final.fsh` (fragment)

---

## Capabilities

| Capability | Value |
|------------|-------|
| scene_color | ✅ Used |
| scene_depth | ❌ Not used |
| shadow_depth | ❌ Not used |
| custom_targets | ❌ Not used |
| compute | ❌ Not used |

The pack only reads from `minecraft:scene_color` and writes back to `minecraft:main`.

---

## How to Use

### 1. Package the zip

```bash
cd examples/vpfx-minimal-showcase
zip -r ../../dist/VPFX-Minimal-Showcase-v1.0.0.zip ./*
```

Or from project root:

```bash
mkdir -p dist
cd examples/vpfx-minimal-showcase && zip -r ../../dist/VPFX-Minimal-Showcase-v1.0.0.zip ./* && cd ../..
```

The zip should contain `pack.json` at the root (not `vpfx-minimal-showcase/pack.json`).

### 2. Install

```bash
cp dist/VPFX-Minimal-Showcase-v1.0.0.zip run/shaderpacks/
```

### 3. Activate

Edit `run/config/vulkanpostfx.json`:

```json
{
  "active_pack_id": "vpfx_minimal_showcase"
}
```

### 4. Launch

```bash
./gradlew runClient
```

Press `F8` to toggle the effect on/off.

---

## Validation

### Before installing, validate the pack is correct:

```bash
# Copy the pack into test packs (optional — for CI-style validation)
cp -r examples/vpfx-minimal-showcase src/test/resources/vpfx_test_packs/positive_showcase

# Run validation
./gradlew validateTestPacks
./gradlew validateMaterialization
```

### If the pack fails to load:

1. Run `./gradlew validateTestPacks` to check for schema errors
2. Run `./gradlew validateMaterialization` to check for materialization errors
3. Check `run/logs/latest.log` for VPFX error messages

---

## Effect Details

### Contrast

```glsl
float contrast = 1.06;
vec3 contrasted = (color.rgb - 0.5) * contrast + 0.5;
```

A mild S-curve centered at 0.5. Dark areas become slightly darker, bright areas slightly brighter.

### Warm Tone

```glsl
vec3 warm = contrasted * vec3(1.04, 0.98, 0.92);
```

Slightly boosts red, slightly reduces blue. Produces a gentle warmth without looking like a color filter.

### Vignette

```glsl
float vignette = 1.0 - smoothstep(0.48, 1.32, length(texCoord - 0.5) * 1.38);
```

Radial darkening from the edges inward. The `smoothstep` with a wide range ensures it's subtle and never fully black at the edges.

All three effects combine to produce a pleasant, subtle enhancement that is clearly visible when toggled with F8 but doesn't overwhelm the original scene.

---

## VPFX v1 Alpha Notes

- This pack targets **VPFX v1 alpha**. The schema may have minor changes before v1 stable.
- The execution backend is **Minecraft PostChain** (`minecraft_postchain`).
- Compute shaders are **not supported** in alpha.
- For full format details, see [docs/vpfx-pack-format-v1.md](../../docs/vpfx-pack-format-v1.md).
- For authoring guidance, see [docs/vpfx-authoring-minimal-pack.md](../../docs/vpfx-authoring-minimal-pack.md).
