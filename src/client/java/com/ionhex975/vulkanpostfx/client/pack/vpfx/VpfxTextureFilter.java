package com.ionhex975.vulkanpostfx.client.pack.vpfx;

public enum VpfxTextureFilter {
    LINEAR("linear"),
    NEAREST("nearest");

    private final String jsonName;

    VpfxTextureFilter(String jsonName) {
        this.jsonName = jsonName;
    }

    public String getJsonName() {
        return jsonName;
    }

    public static VpfxTextureFilter fromJson(String value) {
        if (value == null) {
            return LINEAR;
        }

        for (VpfxTextureFilter filter : values()) {
            if (filter.jsonName.equalsIgnoreCase(value)) {
                return filter;
            }
        }

        throw new IllegalArgumentException("Unsupported texture filter: " + value);
    }
}