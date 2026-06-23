package com.ionhex975.vulkanpostfx.client.pack.vpfx;

public final class VpfxManifestParseException extends Exception {
    private final String code;
    private final String fieldPath;

    public VpfxManifestParseException(String code, String fieldPath, String message) {
        super(message);
        this.code = code;
        this.fieldPath = fieldPath;
    }

    public String getCode() {
        return code;
    }

    public String getFieldPath() {
        return fieldPath;
    }

    @Override
    public String toString() {
        return "VpfxManifestParseException{" +
                "code='" + code + '\'' +
                ", fieldPath='" + fieldPath + '\'' +
                ", message='" + getMessage() + '\'' +
                '}';
    }
}