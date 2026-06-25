package com.ionhex975.vulkanpostfx.client.shader.uniform;

/**
 * Single source of truth for the VPFX builtin UBO layout.
 *
 * Keep this in sync with VpfxBuiltinUniformBuffer and
 * VpfxBuiltinUniformSourceInjector. Held-light data intentionally reuses spare
 * .w lanes inside the legacy layout so native pipelines do not break when the
 * feature is enabled.
 */
public final class VpfxBuiltinUniformLayout {
    public static final int VEC4_SLOT_COUNT = 13;
    public static final int MAT4_SLOT_COUNT = 15;
    public static final int TOTAL_VEC4_SLOTS = VEC4_SLOT_COUNT + MAT4_SLOT_COUNT * 4;
    public static final int STD140_BYTE_SIZE = 1168;
    public static final String HELD_LIGHT_STORAGE = "spare .w components";

    private VpfxBuiltinUniformLayout() {
    }
}
