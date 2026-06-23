package com.ionhex975.vulkanpostfx.client.pack.vpfx;

public final class VpfxValidationMessage {
    public enum Severity {
        FATAL,
        WARNING
    }

    private final Severity severity;
    private final String code;
    private final String path;
    private final String message;

    public VpfxValidationMessage(Severity severity, String code, String path, String message) {
        this.severity = severity;
        this.code = code;
        this.path = path;
        this.message = message;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getCode() {
        return code;
    }

    public String getPath() {
        return path;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "[" + severity + "][" + code + "][" + path + "] " + message;
    }
}