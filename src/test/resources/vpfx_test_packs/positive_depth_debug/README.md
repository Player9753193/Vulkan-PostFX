# VPFX Depth Debug

This pack validates the VPFX scene-depth input path.

It samples `vulkanpostfx:scene_depth` with `use_depth_buffer=true` and displays the raw depth texture as grayscale.

Use this before debugging `depth_fog`:

- If this shows visible near/far variation, scene depth is reaching VPFX shaders.
- If this looks flat or broken, fix scene-depth capture/binding before tuning fog.
