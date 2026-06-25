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

## v2 visibility pass

This template now uses a stronger screen-space brightness lift instead of a very subtle color tint.
The effect is still not true Minecraft world lighting: it does not modify block light, entity lighting, chunk lighting, or server state.
It reads `vpfx_HeldLightColor` / `vpfx_HeldLightIntensity` from `VpfxBuiltins` and applies a local lower-right glow plus dark-area exposure lift.

