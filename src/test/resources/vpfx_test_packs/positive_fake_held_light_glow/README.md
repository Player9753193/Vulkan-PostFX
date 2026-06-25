# VPFX Fake Held-Light Glow

This is an official VPFX example pack for the held-light builtin uniforms.

It demonstrates:

- `vpfx_HeldLightColor`
- `vpfx_HeldLightIntensity`
- `vpfx_HeldLightRadius`
- `vpfx_hasHeldLight()`
- `vpfx_applyHeldLightGlow(...)`

This is a screen-space post effect. It does **not** modify Minecraft world lighting,
chunk light propagation, entity lighting, or server state.

Suggested manual tests:

- Hold a torch: warm orange glow.
- Hold a soul torch: cool blue glow.
- Hold a lava bucket: orange-red glow.
- Empty both hands: original image, no glow.
