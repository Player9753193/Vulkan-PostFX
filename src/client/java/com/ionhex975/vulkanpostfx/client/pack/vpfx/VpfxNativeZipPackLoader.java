package com.ionhex975.vulkanpostfx.client.pack.vpfx;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.shader.VpfxShaderSourcePreprocessor;
import com.ionhex975.vulkanpostfx.client.shader.include.VpfxShaderIncludeException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

/**
 * 正式主线入口：
 * - 只识别 VPFX native packs（zip 根必须存在 pack.json）
 * - parse manifest
 * - resolve runtime capabilities
 * - parse graph
 * - validate graph/capabilities
 * - 检查 shader 资源文件存在
 * - 检查 shader include 可展开
 */
public final class VpfxNativeZipPackLoader {
    private final VpfxPackManifestParser manifestParser = new VpfxPackManifestParser();
    private final VpfxGraphParser graphParser = new VpfxGraphParser();
    private final VpfxCapabilityResolver capabilityResolver = new VpfxCapabilityResolver();
    private final VpfxGraphValidator graphValidator = new VpfxGraphValidator();

    /**
     * @return null 表示不是 VPFX native pack（没有 pack.json）
     */
    public VpfxNativePackDefinition tryLoad(Path zipPath)
            throws VpfxPackLoadException {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            if (zipFile.getEntry("pack.json") == null) {
                return null;
            }

            VpfxPackManifest manifest;
            try {
                manifest = manifestParser.parse(zipFile);
            } catch (VpfxManifestParseException e) {
                throw new VpfxPackLoadException(e.getCode(), e.getFieldPath(), e.getMessage());
            }

            VpfxRuntimeCapabilities runtimeCapabilities = capabilityResolver.resolve();
            VpfxGraphDefinition graph;
            try {
                graph = graphParser.parse(zipFile, manifest.getEntryPostEffect());
            } catch (VpfxGraphParseException e) {
                throw new VpfxPackLoadException(e.getCode(), e.getPath(), e.getMessage());
            }

            List<VpfxValidationMessage> messages =
                    graphValidator.validate(manifest, graph, runtimeCapabilities);

            for (VpfxValidationMessage message : messages) {
                if (message.getSeverity() == VpfxValidationMessage.Severity.FATAL) {
                    throw new VpfxPackLoadException(message.getCode(), message.getPath(), message.getMessage());
                }
            }

            validateShaderFilesExistAndPreprocess(zipFile, graph);

            VulkanPostFX.LOGGER.info(
                    "[{}] VPFX native pack loaded: id='{}', name='{}', version='{}', entryPostEffect='{}', targets={}, passes={}, runtimeCapabilities={}",
                    VulkanPostFX.MOD_ID,
                    manifest.getPackId(),
                    manifest.getName(),
                    manifest.getVersion(),
                    manifest.getEntryPostEffect(),
                    graph.getTargets().size(),
                    graph.getPasses().size(),
                    runtimeCapabilities
            );

            return new VpfxNativePackDefinition(zipPath, manifest, graph, messages);
        } catch (IOException e) {
            throw new VpfxPackLoadException(
                    "Z001",
                    zipPath.toString(),
                    "Failed to open zip file: " + e.getMessage()
            );
        }
    }

    private void validateShaderFilesExistAndPreprocess(ZipFile zipFile, VpfxGraphDefinition graph)
            throws VpfxPackLoadException {
        VpfxShaderSourcePreprocessor preprocessor = new VpfxShaderSourcePreprocessor(zipFile);

        for (int i = 0; i < graph.getPasses().size(); i++) {
            VpfxPassDefinition pass = graph.getPasses().get(i);
            String passIdentity = pass.identityOrIndex(i);

            String vertexPath = toShaderZipPath(pass.getVertexShader(), true);
            String fragmentPath = toShaderZipPath(pass.getFragmentShader(), false);

            if (zipFile.getEntry(vertexPath) == null) {
                throw new VpfxPackLoadException(
                        "S001",
                        passIdentity + ".vertex_shader",
                        "Vertex shader file not found in zip: " + vertexPath
                );
            }

            if (zipFile.getEntry(fragmentPath) == null) {
                throw new VpfxPackLoadException(
                        "S002",
                        passIdentity + ".fragment_shader",
                        "Fragment shader file not found in zip: " + fragmentPath
                );
            }

            try {
                preprocessor.preprocess(vertexPath);
            } catch (VpfxShaderIncludeException e) {
                throw new VpfxPackLoadException(
                        "S004",
                        passIdentity + ".vertex_shader",
                        "Vertex shader preprocess error [" + e.getCode() + "][" + e.getPath() + "]: " + e.getMessage()
                );
            }

            try {
                preprocessor.preprocess(fragmentPath);
            } catch (VpfxShaderIncludeException e) {
                throw new VpfxPackLoadException(
                        "S005",
                        passIdentity + ".fragment_shader",
                        "Fragment shader preprocess error [" + e.getCode() + "][" + e.getPath() + "]: " + e.getMessage()
                );
            }
        }
    }

    /**
     * 资源 ID 形式：
     *   namespace:any/shader/path
     * ZIP 内部路径映射成：
     *   shaders/any/shader/path.vsh
     *   shaders/any/shader/path.fsh
     *
     * 不再限制必须以 post/ 开头（v1 放开）。
     * 禁止路径逃逸（..）。
     */
    private String toShaderZipPath(String resourceId, boolean vertex) throws VpfxPackLoadException {
        int colon = resourceId.indexOf(':');
        if (colon < 0 || colon == resourceId.length() - 1) {
            throw new VpfxPackLoadException(
                    "S003",
                    resourceId,
                    "Invalid shader resource id, expected namespace:path"
            );
        }

        String path = resourceId.substring(colon + 1);
        if (path.isBlank() || path.contains("..") || path.startsWith("/") || path.contains("\\")) {
            throw new VpfxPackLoadException(
                    "S003",
                    resourceId,
                    "Invalid shader path: must not be blank, escape, or absolute"
            );
        }

        String extension = vertex ? ".vsh" : ".fsh";
        return "shaders/" + path + extension;
    }
}