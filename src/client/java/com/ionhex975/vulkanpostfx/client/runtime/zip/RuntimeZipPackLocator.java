package com.ionhex975.vulkanpostfx.client.runtime.zip;

import java.nio.file.Files;
import java.nio.file.Path;

public final class RuntimeZipPackLocator {
    private RuntimeZipPackLocator() {
    }

    /**
     * True when the runtime pack is materialized on disk and visible to Minecraft's
     * resource pack discovery. In-world safety is enforced by blocking forced hard
     * reloads, not by hiding the active pack from the resource repository.
     */
    public static boolean isReady() {
        return RuntimeZipPackState.isResourcePackInjectionAllowed()
                && isMaterializedPackReady();
    }

    /**
     * True when a generated runtime pack exists on disk, regardless of whether it is
     * currently allowed to be injected into a Minecraft resource reload. Hot reload
     * code uses this before opening a scoped injection window.
     */
    public static boolean isMaterializedPackReady() {
        return RuntimeZipPackState.isActive()
                && RuntimeZipPackState.getRuntimeRoot() != null
                && Files.exists(RuntimeZipPackState.getRuntimeRoot().resolve("pack.mcmeta"));
    }

    public static Path getRuntimeRootOrThrow() {
        Path root = RuntimeZipPackState.getRuntimeRoot();
        if (root == null) {
            throw new IllegalStateException("runtime zip pack root is null");
        }
        return root;
    }
}