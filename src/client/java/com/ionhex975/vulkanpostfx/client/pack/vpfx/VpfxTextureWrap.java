package com.ionhex975.vulkanpostfx.client.pack.vpfx;

public enum VpfxTextureWrap {
    CLAMP("clamp"),
    REPEAT("repeat");

    private final String jsonName;

    VpfxTextureWrap(String jsonName) {
        this.jsonName = jsonName;
    }

    public String getJsonName() {
        return jsonName;
    }

    public static VpfxTextureWrap fromJson(String value) {
        if (value == null) {
            return CLAMP;
        }

        for (VpfxTextureWrap wrap : values()) {
            if (wrap.jsonName.equalsIgnoreCase(value)) {
                return wrap;
            }
        }

        throw new IllegalArgumentException("Unsupported texture wrap: " + value);
    }
}