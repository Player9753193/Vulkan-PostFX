package com.ionhex975.vulkanpostfx.client.pack;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxNativePackDefinition;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxNativeZipPackLoader;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxPackLoadException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * ZIP 光影包来源。
 *
 * 现在的正式入口策略：
 * - ZIP 根必须存在 pack.json
 * - pack.json 必须符合 VPFX native pack format v1
 * - 解析入口走 VpfxNativeZipPackLoader
 *
 * 注意：
 * - 这里只替换“发现/识别/校验”入口
 * - 后续 runtime materialization / ActivePostEffectBridge 仍复用现有成熟链
 */
public final class ZipShaderPackSource implements ShaderPackSource {
    public static final String SOURCE_ID = "zip";
    private static final String ZIP_SUFFIX = ".zip";
    private static final String README_FILE_NAME = "README_VulkanPostFX.txt";

    private final Path shaderPackDirectory;
    private final VpfxNativeZipPackLoader vpfxLoader = new VpfxNativeZipPackLoader();

    public ZipShaderPackSource(Path shaderPackDirectory) {
        this.shaderPackDirectory = shaderPackDirectory;
    }

    @Override
    public String id() {
        return SOURCE_ID;
    }

    @Override
    public List<ShaderPackContainer> discoverPacks() {
        ensureDirectoryExists();

        List<ShaderPackContainer> discovered = new ArrayList<>();

        try (var stream = Files.list(shaderPackDirectory)) {
            stream
                    .filter(Files::isRegularFile)
                    .filter(this::isCandidateZip)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(path -> {
                        ShaderPackContainer container = createContainerFromZip(path);
                        if (container != null) {
                            discovered.add(container);
                        }
                    });
        } catch (IOException e) {
            VulkanPostFX.LOGGER.error(
                    "[{}] Failed to scan shader pack directory: {}",
                    VulkanPostFX.MOD_ID,
                    shaderPackDirectory,
                    e
            );
        }

        VulkanPostFX.LOGGER.info(
                "[{}] Zip shader pack source scanned '{}', found {} valid VPFX zip pack(s)",
                VulkanPostFX.MOD_ID,
                shaderPackDirectory,
                discovered.size()
        );

        return discovered;
    }

    private void ensureDirectoryExists() {
        try {
            Files.createDirectories(shaderPackDirectory);
            writeReadmeIfMissing();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create shader pack directory: " + shaderPackDirectory, e);
        }
    }

    private void writeReadmeIfMissing() {
        Path readme = shaderPackDirectory.resolve(README_FILE_NAME);
        if (Files.exists(readme)) {
            return;
        }

        String content = """
                VulkanPostFX shaderpacks directory
                ==================================

                Drop VPFX native shader pack ZIP files directly into this folder.

                Default behavior:
                - Press F7 in game to open the VPFX shader pack menu.
                - Press F10 in game to hot-reload the current VPFX pack after replacing or editing a ZIP.
                - If config/vulkanpostfx.json uses active_pack_id = "auto", the first valid ZIP here is selected automatically.
                - To force the built-in debug pack, set active_pack_id = "builtin".
                - To pin a specific pack, set active_pack_id to that pack's id from pack.json.

                Expected ZIP layout:
                - pack.json must be at the ZIP root.
                - entry_post_effect in pack.json must point to a JSON file inside the ZIP.
                - shader sources should be inside shaders/.
                """;

        try {
            Files.writeString(readme, content, StandardCharsets.UTF_8);
            VulkanPostFX.LOGGER.info(
                    "[{}] Created shaderpacks helper README at {}",
                    VulkanPostFX.MOD_ID,
                    readme
            );
        } catch (IOException e) {
            VulkanPostFX.LOGGER.warn(
                    "[{}] Failed to write shaderpacks helper README at {}",
                    VulkanPostFX.MOD_ID,
                    readme,
                    e
            );
        }
    }

    private boolean isCandidateZip(Path path) {
        String fileName = path.getFileName().toString();
        return !fileName.startsWith(".")
                && fileName.toLowerCase(Locale.ROOT).endsWith(ZIP_SUFFIX);
    }

    private ShaderPackContainer createContainerFromZip(Path zipPath) {
        try {
            VpfxNativePackDefinition vpfxPack = vpfxLoader.tryLoad(zipPath);
            if (vpfxPack == null) {
                VulkanPostFX.LOGGER.warn(
                        "[{}] Skipping zip that is not a VPFX native pack: {}",
                        VulkanPostFX.MOD_ID,
                        zipPath.getFileName()
                );
                return null;
            }

            ShaderPackResourceIndex resourceIndex = buildResourceIndex(zipPath);

            // 这里先做“适配壳”：
            // - 继续把 VPFX manifest 投影成旧 ShaderPackManifest
            // - 这样后半段 ActiveShaderPackManager / ActivePostEffectBridge / ZipPackMaterializer
            //   都可以先不动
            ShaderPackManifest bridgedManifest = new ShaderPackManifest(
                    vpfxPack.getManifest().getPackId(),
                    vpfxPack.getManifest().getName(),
                    vpfxPack.getManifest().getFormatVersion(),
                    "", // VPFX v1 不再使用 entry_effect_key
                    ShaderPackResourceIndex.normalize(vpfxPack.getManifest().getEntryPostEffect())
            );

            VulkanPostFX.LOGGER.info(
                    "[{}] VPFX native pack parsed from zip '{}': id='{}', name='{}', formatVersion={}, entryPostEffect={}, targets={}, passes={}, resourceCount={}",
                    VulkanPostFX.MOD_ID,
                    zipPath.getFileName(),
                    vpfxPack.getManifest().getPackId(),
                    vpfxPack.getManifest().getName(),
                    vpfxPack.getManifest().getFormatVersion(),
                    vpfxPack.getManifest().getEntryPostEffect(),
                    vpfxPack.getGraph().getTargets().size(),
                    vpfxPack.getGraph().getPasses().size(),
                    resourceIndex.size()
            );

            return new ShaderPackContainer(
                    bridgedManifest,
                    SOURCE_ID,
                    zipPath,
                    resourceIndex,
                    vpfxPack
            );
        } catch (VpfxPackLoadException e) {
            VulkanPostFX.LOGGER.error(
                    "[{}] Failed to load VPFX native pack from '{}': [{}][{}] {}",
                    VulkanPostFX.MOD_ID,
                    zipPath.getFileName(),
                    e.getCode(),
                    e.getPath(),
                    e.getMessage()
            );
            return null;
        } catch (Exception e) {
            VulkanPostFX.LOGGER.error(
                    "[{}] Failed to parse zip shader pack: {}",
                    VulkanPostFX.MOD_ID,
                    zipPath,
                    e
            );
            return null;
        }
    }

    private ShaderPackResourceIndex buildResourceIndex(Path zipPath) throws IOException {
        Set<String> resources = new LinkedHashSet<>();

        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .map(ShaderPackResourceIndex::normalize)
                    .forEach(resources::add);
        }

        return new ShaderPackResourceIndex(resources);
    }
}