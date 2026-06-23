package com.ionhex975.vulkanpostfx.client.runtime.zip;

public final class ActiveZipRuntimeNamespace {
    private static final String PREFIX = "vpfxzip_";

    private ActiveZipRuntimeNamespace() {
    }

    public static String fromPackId(String packId) {
        String safe = sanitize(packId);
        if (safe.isBlank()) {
            safe = "pack";
        }
        return PREFIX + safe;
    }

    private static String sanitize(String input) {
        return input
                .toLowerCase()
                .replaceAll("[^a-z0-9_.-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+", "")
                .replaceAll("_+$", "");
    }
}