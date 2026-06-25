# VPFX Colored Light Volume Debug

This pack validates the VPFX colored-light runtime atlas input.

It samples the runtime texture bus entry `colored_light_volume` and displays the 2D atlas directly on screen.

Expected behavior:

- If nearby registered colored lights exist, the atlas should show colored blobs.
- If there are no colored lights nearby, the atlas should be black.
- If this pack is invalid, the runtime texture bus / validator path is not accepting VPFX runtime data textures.

This is not the final colored-light raymarch effect. It is only a data-path debug pack.
