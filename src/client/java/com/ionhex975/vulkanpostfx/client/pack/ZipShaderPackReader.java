package com.ionhex975.vulkanpostfx.client.pack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * ZIP 光影包读取器。
 *
 * 当前最小能力：
 * - 按包内路径读取文本文件
 */
public final class ZipShaderPackReader {
    private ZipShaderPackReader() {
    }

    public static String readText(Path zipPath, String internalPath) throws IOException {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            String normalized = ShaderPackResourceIndex.normalize(internalPath);
            ZipEntry entry = zipFile.getEntry(normalized);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("Zip entry not found: " + normalized);
            }

            try (InputStream input = zipFile.getInputStream(entry)) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }
}