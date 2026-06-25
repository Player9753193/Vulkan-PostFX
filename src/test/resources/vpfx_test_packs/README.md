# VPFX Test Packs

This directory contains test packs for VPFX v1 pack validation.

## Overview

| Pack | Type | Expected Result |
|------|------|-----------------|
| `positive_minimal` | ✅ Positive | Loads successfully (code=OK) |
| `positive_depth_debug` | ✅ Positive | Samples `vulkanpostfx:scene_depth` and displays raw depth |
| `positive_depth_fog` | ✅ Positive | Samples scene color + scene depth and applies distance fog |
| `negative_future_dependency` | ❌ Negative | G017 — reads unwritten future target |
| `negative_invalid_pack_id` | ❌ Negative | F003 — invalid pack_id pattern |
| `negative_missing_entry` | ❌ Negative | F005 — entry_post_effect not found |
| `negative_missing_pack_json` | ❌ Negative | NOT_VPFX — no pack.json (not a VPFX pack) |
| `negative_missing_shader` | ❌ Negative | S001 — vertex shader file missing |
| `negative_no_main_output` | ❌ Negative | G016 — no pass writes to minecraft:main |
| `negative_non_existent_target` | ❌ Negative | G005 — input target not declared |
| `negative_self_read_write` | ❌ Negative | G015 — pass reads its own output |
| `negative_shader_path_escape` | ❌ Negative | S003 — shader path contains `..` |
| `negative_undeclared_output` | ❌ Negative | G004 — output target not declared |
| `negative_undeclared_texture` | ❌ Negative | G013 — texture not declared in pack.json |

## Running the Tests

```bash
./gradlew validateTestPacks
```

## Naming Convention

- **`positive_*`** — Packs expected to load successfully through `VpfxNativeZipPackLoader`
- **`negative_*`** — Packs expected to be rejected with a specific error code

## Adding a New Test Pack

### Positive pack
1. Create `positive_your_name/` under this directory
2. Include a valid `pack.json`, `post_effect/` graph, and `shaders/`
3. Run `./gradlew validateTestPacks` — verify PASS

### Negative pack
1. Create `negative_your_name/` under this directory
2. Build the intentionally broken pack files
3. Refer to [docs/vpfx-pack-format-v1.md](../../../../docs/vpfx-pack-format-v1.md) for valid schema rules
4. Know which error code should be triggered
5. Run `./gradlew validateTestPacks` — verify PASS with the expected error code

### Special: missing pack.json
1. Create the directory but do **not** add `pack.json`
2. Add at least one placeholder file (e.g. `README.txt`)
3. Name it following the pattern (the smoke test has special handling for `negative_missing_pack_json`)

## Validation Chain

Each test pack goes through:

```
test pack directory
  → temporary zip file
    → VpfxNativeZipPackLoader.tryLoad()
      → VpfxPackManifestParser (parses pack.json)
      → VpfxGraphParser (parses post_effect graph)
      → VpfxGraphValidator (validates graph rules)
      → Shader file existence check
      → Shader preprocessing check
    → result (success / exception)
```

If any step fails, the pack is rejected with a specific error code, path, and message.
