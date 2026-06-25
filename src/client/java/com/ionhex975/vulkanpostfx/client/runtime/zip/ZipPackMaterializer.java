package com.ionhex975.vulkanpostfx.client.runtime.zip;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.pack.ShaderPackContainer;
import com.ionhex975.vulkanpostfx.client.pack.ZipShaderPackReader;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxTargetDefinition;
import com.ionhex975.vulkanpostfx.client.runtime.texture.VpfxRuntimeTextureDescriptor;
import com.ionhex975.vulkanpostfx.client.runtime.texture.VpfxRuntimeTextureManifest;
import com.ionhex975.vulkanpostfx.client.runtime.texture.VpfxRuntimeTextureManifestWriter;
import com.ionhex975.vulkanpostfx.client.shader.VpfxShaderSourcePreprocessor;
import com.ionhex975.vulkanpostfx.client.shader.include.VpfxShaderIncludeException;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ZipPackMaterializer {
    private static final int RESOURCE_PACK_FORMAT = 85;

    private ZipPackMaterializer() {
    }

    public static RuntimeZipPackMaterializationResult materialize(
            ShaderPackContainer activePack,
            Path gameDirectory
    ) throws IOException {
        if (activePack == null || activePack.sourcePath() == null) {
            throw new IOException("active pack or source path is null");
        }

        if (!activePack.isVpfxNativePack()) {
            throw new IOException("active pack is not a VPFX native pack");
        }

        String packId = activePack.manifest().id();
        String runtimePackId = ActiveZipRuntimeNamespace.runtimePackId();
        String runtimeNamespace = ActiveZipRuntimeNamespace.fromPackId(packId);

        Path runtimeBaseDir = gameDirectory.resolve("vulkanpostfx_runtime");
        Path runtimeRoot = runtimeBaseDir.resolve(runtimeNamespace);

        recreateDirectory(runtimeRoot);
        writePackMcmeta(runtimeRoot);

        VpfxRuntimeTextureManifest runtimeTextureManifest = VpfxRuntimeTextureManifestWriter.build(
                runtimeNamespace,
                activePack.vpfxDefinition().getManifest().getTextures(),
                activePack.sourcePath()
        );

        String entryPostEffectRaw = ZipShaderPackReader.readText(
                activePack.sourcePath(),
                activePack.manifest().entryPostEffect()
        );

        String rewrittenMainJson = ZipPostEffectNamespaceRewriter.rewrite(
                entryPostEffectRaw,
                packId,
                runtimeNamespace,
                runtimeTextureManifest
        );

        Path mainJsonPath = runtimeRoot
                .resolve("assets")
                .resolve(runtimeNamespace)
                .resolve("post_effect")
                .resolve("main.json");

        Files.createDirectories(mainJsonPath.getParent());
        Files.writeString(mainJsonPath, rewrittenMainJson, StandardCharsets.UTF_8);

        preprocessAndCopyZipShaderTree(activePack.sourcePath(), runtimeRoot, runtimeNamespace);
        materializeDeclaredTextures(activePack, runtimeRoot, runtimeTextureManifest);

        VpfxRuntimeTextureManifestWriter.write(runtimeTextureManifest, runtimeRoot);

        Path runtimeTextureManifestPath = runtimeRoot
                .resolve("assets")
                .resolve(runtimeNamespace)
                .resolve("vpfx")
                .resolve("textures.json");

        Map<String, VpfxTargetDefinition> runtimeTargetDefinitions =
                buildRuntimeTargetDefinitions(activePack, runtimeNamespace);

        VulkanPostFX.LOGGER.info(
                "[{}] Generated runtime texture manifest: sourcePackId={}, runtimePackId={}, namespace={}, textureCount={}, path={}",
                VulkanPostFX.MOD_ID,
                packId,
                runtimePackId,
                runtimeNamespace,
                runtimeTextureManifest.getTextures().size(),
                runtimeTextureManifestPath
        );

        return new RuntimeZipPackMaterializationResult(
                runtimePackId,
                runtimeNamespace,
                runtimeRoot,
                Identifier.fromNamespaceAndPath(runtimeNamespace, "main"),
                runtimeTextureManifestPath,
                runtimeTargetDefinitions
        );
    }

    private static Map<String, VpfxTargetDefinition> buildRuntimeTargetDefinitions(
            ShaderPackContainer activePack,
            String runtimeNamespace
    ) {
        Map<String, VpfxTargetDefinition> sourceTargets =
                activePack.vpfxDefinition().getGraph().getTargets();

        String originalNamespace = activePack.manifest().id();
        Map<String, VpfxTargetDefinition> result = new LinkedHashMap<>();

        for (VpfxTargetDefinition source : sourceTargets.values()) {
            String rewrittenId = rewriteNamespacedTargetId(
                    source.getId(),
                    originalNamespace,
                    runtimeNamespace
            );

            Double scale = source.getScale().orElse(null);
            float[] clearColor = source.getClearColor().orElse(null);

            result.put(
                    rewrittenId,
                    new VpfxTargetDefinition(
                            rewrittenId,
                            scale,
                            source.isUseDepth(),
                            clearColor,
                            source.isPersistent(),
                            source.isHistory(),
                            source.isPingPong()
                    )
            );
        }

        return result;
    }

    private static String rewriteNamespacedTargetId(
            String value,
            String originalNamespace,
            String runtimeNamespace
    ) {
        String prefix = originalNamespace + ":";
        if (value.startsWith(prefix)) {
            return runtimeNamespace + ":" + value.substring(prefix.length());
        }
        return value;
    }

    private static void recreateDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (var walk = Files.walk(dir)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            } catch (RuntimeException e) {
                if (e.getCause() instanceof IOException io) {
                    throw io;
                }
                throw e;
            }
        }

        Files.createDirectories(dir);
    }

    private static void writePackMcmeta(Path runtimeRoot) throws IOException {
        String mcmeta = """
        {
          "pack": {
            "pack_format": %d,
            "min_format": %d,
            "max_format": %d,
            "description": "VulkanPostFX runtime zip shader pack"
          }
        }
        """.formatted(RESOURCE_PACK_FORMAT, RESOURCE_PACK_FORMAT, RESOURCE_PACK_FORMAT);

        Files.writeString(runtimeRoot.resolve("pack.mcmeta"), mcmeta, StandardCharsets.UTF_8);
    }

    private static void preprocessAndCopyZipShaderTree(
            Path zipPath,
            Path runtimeRoot,
            String runtimeNamespace
    ) throws IOException {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            VpfxShaderSourcePreprocessor preprocessor = new VpfxShaderSourcePreprocessor(zipFile);

            var entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }

                String name = entry.getName().replace('\\', '/');

                if (!name.startsWith("shaders/")) {
                    continue;
                }

                if (name.endsWith(".DS_Store") || name.contains("/.DS_Store")) {
                    VulkanPostFX.LOGGER.info(
                            "[{}] Skipped ZIP junk file: {}",
                            VulkanPostFX.MOD_ID,
                            name
                    );
                    continue;
                }

                Path outPath = runtimeRoot
                        .resolve("assets")
                        .resolve(runtimeNamespace)
                        .resolve(name);

                Files.createDirectories(outPath.getParent());

                if (isShaderSource(name)) {
                    try {
                        String processed = preprocessor.preprocess(name);
                        Files.writeString(outPath, processed, StandardCharsets.UTF_8);

                        VulkanPostFX.LOGGER.info(
                                "[{}] Materialized preprocessed shader asset: {} -> {}",
                                VulkanPostFX.MOD_ID,
                                name,
                                outPath
                        );
                    } catch (VpfxShaderIncludeException e) {
                        throw new IOException(
                                "Failed to preprocess shader [" + e.getCode() + "][" + e.getPath() + "]: " + e.getMessage(),
                                e
                        );
                    }
                } else {
                    try (InputStream in = zipFile.getInputStream(entry)) {
                        Files.copy(in, outPath);
                    }

                    VulkanPostFX.LOGGER.info(
                            "[{}] Materialized raw shader asset: {} -> {}",
                            VulkanPostFX.MOD_ID,
                            name,
                            outPath
                    );
                }
            }
        }
    }

    private static void materializeDeclaredTextures(
            ShaderPackContainer activePack,
            Path runtimeRoot,
            VpfxRuntimeTextureManifest runtimeTextureManifest
    ) throws IOException {
        if (runtimeTextureManifest.getTextures().isEmpty()) {
            return;
        }

        try (ZipFile zipFile = new ZipFile(activePack.sourcePath().toFile())) {
            for (VpfxRuntimeTextureDescriptor descriptor : runtimeTextureManifest.getTextures().values()) {
                if (descriptor.getSourceZipPath() != null
                        && descriptor.getSourceZipPath().startsWith("runtime-bus:")) {
                    VulkanPostFX.LOGGER.info(
                            "[{}] Skipped runtime-bus texture materialization: logical={}, location={}, size={}x{}",
                            VulkanPostFX.MOD_ID,
                            descriptor.getLogicalName(),
                            descriptor.getLocationId(),
                            descriptor.getWidth(),
                            descriptor.getHeight()
                    );
                    continue;
                }

                Path outPath = runtimeRoot
                        .resolve("assets")
                        .resolve(runtimeTextureManifest.getRuntimeNamespace())
                        .resolve("textures")
                        .resolve("effect")
                        .resolve(descriptor.getEffectPath() + ".png");

                Files.createDirectories(outPath.getParent());

                ZipEntry entry = zipFile.getEntry(descriptor.getSourceZipPath());
                if (entry == null || entry.isDirectory()) {
                    throw new IOException("Declared texture missing from zip: " + descriptor.getSourceZipPath());
                }

                try (InputStream in = zipFile.getInputStream(entry)) {
                    Files.copy(in, outPath);
                }

                VulkanPostFX.LOGGER.info(
                        "[{}] Materialized declared texture asset: {} -> {} (location={}, size={}x{}, bilinear={})",
                        VulkanPostFX.MOD_ID,
                        descriptor.getSourceZipPath(),
                        outPath,
                        descriptor.getLocationId(),
                        descriptor.getWidth(),
                        descriptor.getHeight(),
                        descriptor.isBilinear()
                );
            }
        }
    }

    private static boolean isShaderSource(String zipEntryPath) {
        return zipEntryPath.endsWith(".vsh")
                || zipEntryPath.endsWith(".fsh")
                || zipEntryPath.endsWith(".glsl");
    }
}