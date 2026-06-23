This pack intentionally has no pack.json and must not be recognized as a VPFX native pack.

VPFX format requires pack.json at the zip root. A missing pack.json means the zip is not a VPFX native pack at all.

Expected behavior from VpfxNativeZipPackLoader.tryLoad():
- Returns null — not recognized as VPFX pack
- Does NOT throw VpfxPackLoadException
