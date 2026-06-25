package com.ionhex975.vulkanpostfx.client.runtime.zip;

/**
 * Runtime ZIP packs must use a stable resource-pack id, root and namespace.
 *
 * Minecraft's resource manager only sees namespaces that were present during the
 * last resource reload. If every VPFX pack gets its own runtime namespace, then
 * switching packs in-world without a hard reload leaves native RenderPipeline
 * shader lookup pointing at a namespace that Minecraft has never loaded.
 *
 * Keep the namespace stable and rewrite every active VPFX pack into this same
 * namespace. The files can be overwritten by hot reload while the resource-pack
 * profile remains the same.
 */
public final class ActiveZipRuntimeNamespace {
    public static final String RUNTIME_PACK_ID = "vulkanpostfx_runtime_zip_pack";
    public static final String RUNTIME_NAMESPACE = "vpfxzip_runtime";

    private ActiveZipRuntimeNamespace() {
    }

    public static String runtimePackId() {
        return RUNTIME_PACK_ID;
    }

    public static String runtimeNamespace() {
        return RUNTIME_NAMESPACE;
    }

    public static String fromPackId(String packId) {
        return RUNTIME_NAMESPACE;
    }
}
