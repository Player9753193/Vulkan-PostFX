package com.ionhex975.vulkanpostfx.client.shader.include;

public final class VpfxShaderIncludeException extends Exception {
    private final String code;
    private final String path;

    public VpfxShaderIncludeException(String code, String path, String message) {
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