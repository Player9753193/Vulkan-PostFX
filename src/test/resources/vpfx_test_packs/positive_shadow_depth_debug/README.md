# VPFX Shadow Depth Debug

This pack validates the VPFX shadow-depth input path.

It samples `vulkanpostfx:shadow_depth` with `use_depth_buffer=true` and displays the shadow map as grayscale.

Use it to verify:

- The VPFX shadow-depth target exists.
- The shadow pass ran this frame.
- The shadow map is visible to PostChain/native samplers.
- `/vpfx doctor` reports `Shadow depth available this frame: true`.

This is a diagnostic pack only. It does not perform final shadow compositing.
