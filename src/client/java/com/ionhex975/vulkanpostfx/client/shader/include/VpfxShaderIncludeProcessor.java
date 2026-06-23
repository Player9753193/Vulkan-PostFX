package com.ionhex975.vulkanpostfx.client.shader.include;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * VPFX include 预处理器。
 *
 * 支持：
 * - #include "relative/path.glsl"
 * - 相对当前 shader 所在目录解析
 * - 自动递归展开
 *
 * 明确限制：
 * - 禁止 .. 路径逃逸
 * - 限制最大递归深度
 * - 检测循环 include
 */
public final class VpfxShaderIncludeProcessor {
    private static final Pattern INCLUDE_PATTERN =
            Pattern.compile("(?m)^\\s*#include\\s+\"([^\"]+)\"\\s*$");

    private static final int MAX_INCLUDE_DEPTH = 32;

    private final ZipFile zipFile;

    public VpfxShaderIncludeProcessor(ZipFile zipFile) {
        this.zipFile = zipFile;
    }

    public String process(String zipShaderPath) throws VpfxShaderIncludeException {
        return processInternal(normalizeZipPath(zipShaderPath), new HashSet<>(), 0);
    }

    private String processInternal(
            String currentZipPath,
            Set<String> activeStack,
            int depth
    ) throws VpfxShaderIncludeException {
        if (depth > MAX_INCLUDE_DEPTH) {
            throw new VpfxShaderIncludeException(
                    "I001",
                    currentZipPath,
                    "Include depth exceeded limit: " + MAX_INCLUDE_DEPTH
            );
        }

        if (!activeStack.add(currentZipPath)) {
            throw new VpfxShaderIncludeException(
                    "I002",
                    currentZipPath,
                    "Include cycle detected"
            );
        }

        String source = readZipText(currentZipPath);
        Matcher matcher = INCLUDE_PATTERN.matcher(source);
        StringBuffer out = new StringBuffer();

        while (matcher.find()) {
            String rawIncludePath = matcher.group(1);
            String resolvedPath = resolveIncludePath(currentZipPath, rawIncludePath);

            String expanded = processInternal(resolvedPath, activeStack, depth + 1);

            String replacement = "\n// BEGIN include: " + resolvedPath + "\n"
                    + expanded
                    + "\n// END include: " + resolvedPath + "\n";

            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(out);
        activeStack.remove(currentZipPath);
        return out.toString();
    }

    private String resolveIncludePath(String currentZipPath, String includePath)
            throws VpfxShaderIncludeException {
        String normalizedInclude = includePath.replace('\\', '/').trim();

        if (normalizedInclude.isBlank()) {
            throw new VpfxShaderIncludeException(
                    "I003",
                    currentZipPath,
                    "Blank include path"
            );
        }

        if (normalizedInclude.contains("..")) {
            throw new VpfxShaderIncludeException(
                    "I004",
                    includePath,
                    "Parent path traversal is not allowed in include paths"
            );
        }

        // 绝对 ZIP 风格：从 shaders/ 开始
        if (normalizedInclude.startsWith("shaders/")) {
            return normalizeZipPath(normalizedInclude);
        }

        // 前导 / 也当作 ZIP 内部绝对路径
        while (normalizedInclude.startsWith("/")) {
            normalizedInclude = normalizedInclude.substring(1);
        }
        if (normalizedInclude.startsWith("shaders/")) {
            return normalizeZipPath(normalizedInclude);
        }

        // 相对路径：相对当前文件目录
        int slash = currentZipPath.lastIndexOf('/');
        String baseDir = slash >= 0 ? currentZipPath.substring(0, slash + 1) : "";
        return normalizeZipPath(baseDir + normalizedInclude);
    }

    private String readZipText(String zipPath) throws VpfxShaderIncludeException {
        ZipEntry entry = zipFile.getEntry(zipPath);
        if (entry == null || entry.isDirectory()) {
            throw new VpfxShaderIncludeException(
                    "I005",
                    zipPath,
                    "Included shader file not found in zip"
            );
        }

        try (InputStream in = zipFile.getInputStream(entry)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new VpfxShaderIncludeException(
                    "I006",
                    zipPath,
                    "Failed to read included shader file: " + e.getMessage()
            );
        }
    }

    private String normalizeZipPath(String path) {
        String normalized = path.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }
}