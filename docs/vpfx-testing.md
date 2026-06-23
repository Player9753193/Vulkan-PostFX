# VPFX v1 Testing Guide

## Overview

VPFX v1 includes two smoke tests that validate the pack loading, validation, and materialization pipeline without requiring a full Minecraft client launch.

| Test | Gradle Task | What it verifies |
|------|------------|-----------------|
| VpfxPackValidationSmokeTest | `./gradlew validateTestPacks` | Pack loading, validation (positive + negative cases) |
| VpfxRuntimeMaterializationSmokeTest | `./gradlew validateMaterialization` | Runtime resource pack materialization |

Both tests run as standalone `JavaExec` tasks. They do **not** require a Minecraft client, OpenGL/Vulkan context, or Fabric loader environment — they only need the project's classpath.

---

## Running the Tests

```bash
# Run validation smoke test (12 test cases)
./gradlew validateTestPacks

# Run materialization smoke test
./gradlew validateMaterialization

# Run both
./gradlew validateTestPacks validateMaterialization
```

Test success means:
- `BUILD SUCCESSFUL` with exit code 0
- All test cases show `[PASS]`
- Summary line: `Results: N passed, 0 failed`

Test failure means:
- Some cases show `[FAIL]`
- Exit code 1

---

## VpfxPackValidationSmokeTest

**Location**: `src/client/java/com/ionhex975/vulkanpostfx/test/VpfxPackValidationSmokeTest.java`

**What it does**:
1. Scans `src/test/resources/vpfx_test_packs/` for test pack directories
2. Creates temporary zip files from each directory
3. Passes each zip through `VpfxNativeZipPackLoader.tryLoad()`
4. Checks results against expectations

**Judgment rules**:

| Pack prefix | Expected result | PASS if |
|------------|----------------|---------|
| `positive_*` | Load successfully | `tryLoad()` returns non-null |
| `negative_missing_pack_json` | Not a VPFX pack | `tryLoad()` returns **null** (code=NOT_VPFX) |
| Other `negative_*` | Be rejected | `tryLoad()` throws `VpfxPackLoadException` |

**Output format**: Each test case outputs `code`, `path`, and `message` for the error (or `OK`/`-` for positive cases).

---

## VpfxRuntimeMaterializationSmokeTest

**Location**: `src/client/java/com/ionhex975/vulkanpostfx/test/VpfxRuntimeMaterializationSmokeTest.java`

**What it does**:
1. Loads `positive_minimal` through `VpfxNativeZipPackLoader`
2. Constructs a `ShaderPackContainer`
3. Creates a `VpfxPostChainBackend` (via `VpfxRuntimeBackend` interface) and prints its `id`, `displayName`, and `capabilities`
4. Calls `backend.materialize()` to generate a runtime resource pack
5. Verifies the generated output

**Verification chain**:
```
positive_minimal/ directory
  → ZipFile
    → VpfxNativeZipPackLoader.tryLoad()
      → VpfxNativePackDefinition
        → ShaderPackContainer
          → VpfxRuntimeBackend.materialize()  ← explicit backend abstraction
            → VpfxPostChainBackend
              → ZipPackMaterializer.materialize()
                → RuntimeZipPackMaterializationResult
                  → runtime resource pack on disk
```

**Backend output example**:
```
VPFX runtime backend:
  id: minecraft_postchain
  display: Minecraft PostChain Backend
  capabilities: VpfxRuntimeBackendCapabilities{usesPostChain=true, nativeRuntime=false, ...}
```

**Expected generated resources**:
```
pack.mcmeta
assets/<runtime_namespace>/post_effect/main.json
assets/<runtime_namespace>/shaders/composite/final.vsh
assets/<runtime_namespace>/shaders/composite/final.fsh
assets/<runtime_namespace>/vpfx/textures.json
```

**Checks performed** (8 total):
- Post effect JSON exists
- Vertex shader exists (non-post/ path: `composite/final`)
- Vertex shader contains `#version` directive (preprocessed)
- Fragment shader exists
- Fragment shader contains `#version` directive
- Post effect JSON uses runtime namespace
- Post effect JSON contains `vertex_shader` and `fragment_shader` references
- Texture manifest generated

---

## Test Packs

### Directory Structure

```
src/test/resources/vpfx_test_packs/
├── README.md                        # Test pack overview
├── positive_minimal/                # Minimal valid VPFX v1 pack
│   ├── README.md
│   ├── pack.json
│   ├── post_effect/main.json
│   └── shaders/composite/
│       ├── final.vsh
│       └── final.fsh
└── negative_*/                      # Negative test cases
    └── ...
```

### positive_minimal

The canonical minimal VPFX v1 pack. See [positive_minimal/README.md](../src/test/resources/vpfx_test_packs/positive_minimal/README.md) for details.

Key features:
- Uses non-post/ shader path (`composite/final`)
- Single pass: read `minecraft:main`, write `minecraft:main`
- Pack ID: `official_test_minimal`
- All capabilities set to minimal required values

### Negative Test Matrix

| Pack | Expected Code | What it tests |
|------|--------------|---------------|
| `negative_future_dependency` | **G017** | Pass reads a target not yet written by any prior pass |
| `negative_invalid_pack_id` | **F003** | pack_id pattern validation (`INVALID PACK ID!!`) |
| `negative_missing_entry` | **F005** | entry_post_effect file not found |
| `negative_missing_pack_json` | **NOT_VPFX** | Zip without pack.json returns null (not a VPFX pack) |
| `negative_missing_shader` | **S001** | Vertex shader file not found in zip |
| `negative_no_main_output` | **G016** | No pass writes output to `minecraft:main` |
| `negative_non_existent_target` | **G005** | Input target references an undeclared target |
| `negative_self_read_write` | **G015** | Pass reads from the same target it writes to |
| `negative_shader_path_escape` | **S003** | Shader path contains `..` |
| `negative_undeclared_output` | **G004** | Output target not declared in targets block |
| `negative_undeclared_texture` | **G013** | Texture input not declared in pack.json textures |

### negative_missing_pack_json (special case)

This test pack intentionally has **no `pack.json`**. It contains only a `README.txt` placeholder.

The expected behavior is that `VpfxNativeZipPackLoader.tryLoad()` returns `null` — meaning the zip is not recognized as a VPFX native pack at all. This is **correct** behavior and is a PASS in the smoke test.

The error code reported for this case is `NOT_VPFX` (path: `pack.json`), which is a special code used only by the smoke test runner. The loader itself does not throw — it simply returns null.

---

## Adding New Test Packs

### Naming convention
- `positive_*` — packs expected to load successfully
- `negative_*` — packs expected to be rejected

### Adding a positive pack
1. Create `src/test/resources/vpfx_test_packs/positive_your_name/`
2. Add `pack.json`, `post_effect/main.json`, and all referenced shaders
3. Run `./gradlew validateTestPacks`
4. Verify the pack appears as PASS

### Adding a negative pack
1. Create `src/test/resources/vpfx_test_packs/negative_your_name/`
2. Add the intentionally broken pack files
3. Know which error code to expect (G004, G005, G013, G015, G016, G017, S001, S002, S003, F003, F005, etc.)
4. Run `./gradlew validateTestPacks`
5. Verify the pack appears as PASS with the expected error code

### Without pack.json (missing_pack_json variant)
1. Create the directory but do NOT add `pack.json`
2. Add a placeholder file (e.g. `README.txt`) so the directory is not empty
3. Name it `negative_missing_pack_json` or similar
4. The smoke test has special handling for this exact name

---

## Error Code Reference

### Manifest Errors (F*)
| Code | Meaning |
|------|---------|
| F001 | Missing pack.json |
| F002 | Unsupported format_version |
| F003 | Invalid pack_id |
| F005 | entry_post_effect file not found in zip |
| F006 | Invalid manifest field |
| F007 | Missing required target mapping |
| F008 | Invalid texture definition |

### Graph Errors (G*)
| Code | Meaning |
|------|---------|
| G002 | Graph has no passes |
| G003 | Pass has no inputs |
| G004 | Output target not declared |
| G005 | Input target not found |
| G006 | use_depth_buffer requires depth attachment |
| G007 | Invalid target identifier |
| G008 | Invalid sampler name |
| G009 | Duplicate sampler name in pass |
| G011 | Invalid target scale |
| G012 | Input must have target or texture (not both, not neither) |
| G013 | Texture input not declared |
| G014 | texture input with use_depth_buffer |
| G015 | Self-read/write cycle |
| G016 | Graph does not write to minecraft:main |
| G017 | Pass reads unwritten future target |

### Shader Errors (S*)
| Code | Meaning |
|------|---------|
| S001 | Vertex shader file not found |
| S002 | Fragment shader file not found |
| S003 | Invalid shader path (escape, blank, absolute) |
| S004 | Vertex shader preprocessing failed |
| S005 | Fragment shader preprocessing failed |

### Capability Errors (C*)
| Code | Meaning |
|------|---------|
| C001 | scene_color not provided by runtime |
| C002 | scene_depth not provided by runtime |
| C003 | shadow_depth not provided by runtime |
| C004 | custom_targets not provided by runtime |
| C005 | compute not supported |
