package com.ionhex975.vulkanpostfx.client.diagnostics.exporter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class VpfxDiagnosticZipWriter implements AutoCloseable {
    private final ZipOutputStream out;
    private final List<String> skippedFiles = new ArrayList<>();
    private int filesWritten;

    VpfxDiagnosticZipWriter(Path outputPath) throws IOException {
        Files.createDirectories(outputPath.getParent());
        this.out = new ZipOutputStream(Files.newOutputStream(outputPath));
    }

    void addText(String entryName, String content) throws IOException {
        String normalized = normalizeEntryName(entryName);
        ZipEntry entry = new ZipEntry(normalized);
        out.putNextEntry(entry);
        byte[] data = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
        out.write(data);
        out.closeEntry();
        filesWritten++;
    }

    void addFileIfExists(Path source, String entryName) throws IOException {
        String normalized = normalizeEntryName(entryName);
        if (source == null || !Files.isRegularFile(source)) {
            skippedFiles.add(normalized + " (not found: " + String.valueOf(source) + ")");
            return;
        }

        ZipEntry entry = new ZipEntry(normalized);
        out.putNextEntry(entry);
        try (InputStream input = Files.newInputStream(source)) {
            input.transferTo(out);
        }
        out.closeEntry();
        filesWritten++;
    }

    int filesWritten() {
        return filesWritten;
    }

    List<String> skippedFiles() {
        return List.copyOf(skippedFiles);
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    private static String normalizeEntryName(String entryName) {
        if (entryName == null || entryName.isBlank()) {
            return "unnamed.txt";
        }
        return entryName.replace('\\', '/').replaceAll("^/", "");
    }
}
