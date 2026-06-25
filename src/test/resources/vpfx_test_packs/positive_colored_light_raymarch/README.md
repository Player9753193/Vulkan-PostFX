# VPFX Colored Light Raymarch

Official VPFX prototype pack for screen-space colored-light raymarching.

This pack samples:

- `minecraft:scene_color`
- `vulkanpostfx:scene_depth`
- `colored_light_volume`, the CPU-built VPFX runtime texture atlas

It raymarches from the camera to the visible surface and accumulates nearby RGB
light density from the low-resolution colored-light volume atlas.

This is not RTX/path tracing and it does not implement true voxel GI. It is the
first VPFX colored-light transport prototype built on top of:

```text
ColoredLightCollector -> ColoredLightVolumeAtlas -> RuntimeTextureBus -> Raymarch Composite
```

Best tested in a dark area with several colored light sources nearby, such as
normal torches, soul lanterns, redstone torches, sea lanterns, and lava.
