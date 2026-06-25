package com.ionhex975.vulkanpostfx.client.diagnostics.doctor;

import com.ionhex975.vulkanpostfx.client.pack.ActiveShaderPackManager;
import com.ionhex975.vulkanpostfx.client.pack.ShaderPackContainer;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxGraphDefinition;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxPassDefinition;
import com.ionhex975.vulkanpostfx.client.runtime.zip.ActiveZipRuntimeNamespace;
import com.ionhex975.vulkanpostfx.client.runtime.zip.RuntimeZipPackState;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class VpfxRuntimeResourceProbe {
    public List<VpfxDoctorCheck> checkRuntimePack() {
        List<VpfxDoctorCheck> checks = new ArrayList<>();
        boolean active = RuntimeZipPackState.isActive();
        Path runtimeRoot = RuntimeZipPackState.getRuntimeRoot();
        String runtimeNamespace = RuntimeZipPackState.getRuntimeNamespace();

        checks.add(VpfxDoctorCheck.info("runtime_pack_active", "Runtime pack active", active));
        checks.add(VpfxDoctorCheck.info("runtime_pack_id", "Runtime pack profile id", ActiveZipRuntimeNamespace.runtimePackId()));
        checks.add(statusForNamespace(runtimeNamespace));
        checks.add(runtimeRootCheck(runtimeRoot));

        if (runtimeRoot != null) {
            checks.add(fileExistsCheck(
                    "runtime_pack_mcmeta",
                    "pack.mcmeta exists",
                    runtimeRoot.resolve("pack.mcmeta"),
                    true
            ));
            if (runtimeNamespace != null && !runtimeNamespace.isBlank()) {
                checks.add(fileExistsCheck(
                        "runtime_assets_namespace_dir",
                        "Runtime assets namespace directory",
                        runtimeRoot.resolve("assets").resolve(runtimeNamespace),
                        true
                ));
            }
        }

        boolean resourceReloaded = RuntimeZipPackState.isMinecraftResourceReloadedWithRuntimePack();
        if (active && !resourceReloaded) {
            checks.add(VpfxDoctorCheck.warn(
                    "runtime_resource_reload",
                    "Minecraft resource reload with runtime pack",
                    false,
                    "Runtime pack exists, but Minecraft ResourceManager may not see newly materialized shader sources yet."
            ));
        } else if (active) {
            checks.add(VpfxDoctorCheck.ok("runtime_resource_reload", "Minecraft resource reload with runtime pack", true));
        } else {
            checks.add(VpfxDoctorCheck.skipped("runtime_resource_reload", "Minecraft resource reload with runtime pack", "No active runtime ZIP pack."));
        }

        return checks;
    }

    public List<VpfxDoctorCheck> checkActiveShaderSources() {
        List<VpfxDoctorCheck> checks = new ArrayList<>();
        ShaderPackContainer activePack = ActiveShaderPackManager.getActivePack();

        if (activePack == null) {
            checks.add(VpfxDoctorCheck.skipped("shader_sources", "Shader sources", "No active shader pack."));
            return checks;
        }
        if (!activePack.isVpfxNativePack() || activePack.vpfxDefinition() == null) {
            checks.add(VpfxDoctorCheck.skipped("shader_sources", "Shader sources", "Active pack is not an external VPFX native graph pack."));
            return checks;
        }

        VpfxGraphDefinition graph = activePack.vpfxDefinition().getGraph();
        if (graph == null || graph.getPasses().isEmpty()) {
            checks.add(VpfxDoctorCheck.skipped("shader_sources", "Shader sources", "Active VPFX graph has no passes."));
            return checks;
        }

        String runtimeNamespace = RuntimeZipPackState.getRuntimeNamespace();
        Path runtimeRoot = RuntimeZipPackState.getRuntimeRoot();
        if (runtimeNamespace == null || runtimeNamespace.isBlank() || runtimeRoot == null) {
            checks.add(VpfxDoctorCheck.warn(
                    "shader_sources_runtime_state",
                    "Shader source runtime state",
                    "unavailable",
                    "Runtime namespace or root is not active yet."
            ));
            return checks;
        }

        int index = 0;
        for (VpfxPassDefinition pass : graph.getPasses()) {
            String passId = pass.identityOrIndex(index++);
            checks.addAll(checkShaderPair(runtimeRoot, runtimeNamespace, passId, pass.getVertexShader(), pass.getFragmentShader()));
        }

        return checks;
    }

    private static List<VpfxDoctorCheck> checkShaderPair(
            Path runtimeRoot,
            String runtimeNamespace,
            String passId,
            String vertexRef,
            String fragmentRef
    ) {
        List<VpfxDoctorCheck> checks = new ArrayList<>();
        checks.addAll(checkOneShader(runtimeRoot, runtimeNamespace, passId, "vertex", vertexRef, true));
        checks.addAll(checkOneShader(runtimeRoot, runtimeNamespace, passId, "fragment", fragmentRef, false));
        return checks;
    }

    private static List<VpfxDoctorCheck> checkOneShader(
            Path runtimeRoot,
            String runtimeNamespace,
            String passId,
            String kind,
            String shaderRef,
            boolean vertex
    ) {
        List<VpfxDoctorCheck> checks = new ArrayList<>();
        String path = extractShaderPath(shaderRef);
        String prefix = "shader_" + sanitize(passId) + "_" + kind;

        if (path == null || path.isBlank()) {
            checks.add(VpfxDoctorCheck.error(
                    prefix + "_ref",
                    passId + " " + kind + " shader ref",
                    String.valueOf(shaderRef),
                    "Shader ref must be namespace:path."
            ));
            return checks;
        }

        String extension = vertex ? ".vsh" : ".fsh";
        Path diskPath = runtimeRoot
                .resolve("assets")
                .resolve(runtimeNamespace)
                .resolve("shaders")
                .resolve(path + extension);
        boolean diskExists = Files.isRegularFile(diskPath);
        checks.add(diskExists
                ? VpfxDoctorCheck.ok(prefix + "_disk", passId + " " + kind + " shader on disk", diskPath)
                : VpfxDoctorCheck.error(prefix + "_disk", passId + " " + kind + " shader on disk", diskPath, "Materialized runtime shader file is missing."));

        Identifier shaderId;
        Identifier sourceId;
        try {
            shaderId = Identifier.fromNamespaceAndPath(runtimeNamespace, path);
            sourceId = Identifier.fromNamespaceAndPath(runtimeNamespace, "shaders/" + path + extension);
        } catch (Throwable t) {
            checks.add(VpfxDoctorCheck.error(
                    prefix + "_resource_id",
                    passId + " " + kind + " shader resource id",
                    runtimeNamespace + ":" + path,
                    t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage())
            ));
            return checks;
        }

        boolean visible = resourceVisible(sourceId);
        if (visible) {
            checks.add(VpfxDoctorCheck.ok(prefix + "_resource", passId + " " + kind + " shader ResourceManager", shaderId));
        } else {
            VpfxDoctorSeverity severity = RuntimeZipPackState.isMinecraftResourceReloadedWithRuntimePack()
                    ? VpfxDoctorSeverity.ERROR
                    : VpfxDoctorSeverity.WARN;
            checks.add(new VpfxDoctorCheck(
                    severity,
                    prefix + "_resource",
                    passId + " " + kind + " shader ResourceManager",
                    shaderId.toString(),
                    "ResourceManager cannot see " + sourceId + ". A Minecraft resource reload may be required."
            ));
        }

        return checks;
    }

    private static boolean resourceVisible(Identifier sourceId) {
        if (sourceId == null) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getResourceManager() == null) {
            return false;
        }
        return minecraft.getResourceManager().getResource(sourceId).isPresent();
    }

    private static VpfxDoctorCheck statusForNamespace(String runtimeNamespace) {
        if (runtimeNamespace == null || runtimeNamespace.isBlank()) {
            return VpfxDoctorCheck.warn("runtime_namespace", "Runtime namespace", "none", "No runtime namespace has been applied yet.");
        }
        if (!ActiveZipRuntimeNamespace.runtimeNamespace().equals(runtimeNamespace)) {
            return VpfxDoctorCheck.warn(
                    "runtime_namespace",
                    "Runtime namespace",
                    runtimeNamespace,
                    "Expected stable runtime namespace " + ActiveZipRuntimeNamespace.runtimeNamespace()
            );
        }
        return VpfxDoctorCheck.ok("runtime_namespace", "Runtime namespace", runtimeNamespace);
    }

    private static VpfxDoctorCheck runtimeRootCheck(Path runtimeRoot) {
        if (runtimeRoot == null) {
            return VpfxDoctorCheck.warn("runtime_root", "Runtime root", "none", "Runtime pack has not been materialized yet.");
        }
        boolean exists = Files.isDirectory(runtimeRoot);
        return exists
                ? VpfxDoctorCheck.ok("runtime_root", "Runtime root", runtimeRoot)
                : VpfxDoctorCheck.error("runtime_root", "Runtime root", runtimeRoot, "Runtime root directory is missing.");
    }

    private static VpfxDoctorCheck fileExistsCheck(String code, String label, Path path, boolean directoryOk) {
        boolean exists = directoryOk ? Files.exists(path) : Files.isRegularFile(path);
        return exists
                ? VpfxDoctorCheck.ok(code, label, path)
                : VpfxDoctorCheck.error(code, label, path, "Expected runtime resource does not exist on disk.");
    }

    private static String extractShaderPath(String ref) {
        if (ref == null) {
            return null;
        }
        int colon = ref.indexOf(':');
        if (colon < 0 || colon == ref.length() - 1) {
            return null;
        }
        return ref.substring(colon + 1);
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "anonymous";
        }
        return value.replaceAll("[^A-Za-z0-9_]+", "_");
    }
}
