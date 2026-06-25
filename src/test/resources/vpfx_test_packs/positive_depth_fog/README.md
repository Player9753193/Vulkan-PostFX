# VPFX Depth Fog

This pack demonstrates VPFX scene-depth post-processing.

It reads:

- `minecraft:scene_color` as `InSampler`
- `vulkanpostfx:scene_depth` as `DepthSampler` with `use_depth_buffer=true`

It then reconstructs view-space distance through VPFX builtin matrices and blends distant pixels toward a soft fog color.

Recommended validation order:

1. Test `VPFX Depth Debug` first.
2. If depth debug shows near/far variation, test this fog pack.
3. Use `/vpfx doctor` to check the `Scene Depth` section if the effect is missing.
