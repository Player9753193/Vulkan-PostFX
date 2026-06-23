package com.ionhex975.vulkanpostfx.client.pack.vpfx;

public final class VpfxPackLoadException extends Exception {
    private final String code;
    private final String path;

    public VpfxPackLoadException(String code, String path, String message) {
        super(message);
        this.code = code;
        this.path = path;
    }

    public String getCode() {
        return code;
    }

    public String getPath() {
        return path;
    }
}