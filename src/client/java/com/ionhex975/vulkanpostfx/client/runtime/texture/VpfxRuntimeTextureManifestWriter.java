package com.ionhex975.vulkanpostfx.client.runtime.texture;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxTextureFilter;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxTextureManifestEntry;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class VpfxRuntimeTextureManifestWriter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private VpfxRuntimeTextureManifestWriter() {
    }

    public static VpfxRuntimeTextureManifest build(
            String runtimeNamespace,
            Map<String, VpfxTextureManifestEntry> declaredTextures,
            Path zipPath
    ) throws IOException {
        Map<String, VpfxRuntimeTextureDescriptor> result = new LinkedHashMap<>();

        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            for (Map.Entry<String, VpfxTextureManifestEntry> entry : declaredTextures.entrySet()) {
                String logicalName = entry.getKey();
                VpfxTextureManifestEntry texture = entry.getValue();

                String sourceZipPath = normalizeZipPath(texture.getPath());
                String effectPath = toEffectPath(sourceZipPath);
                String locationId = runtimeNamespace + ":" + effectPath;
                int[] size = readTextureSize(zipFile, sourceZipPath);
                boolean bilinear = texture.getFilter() == VpfxTextureFilter.LINEAR;

                result.put(logicalName, new VpfxRuntimeTextureDescriptor(
                        logicalName,
                        sourceZipPath,
                        effectPath,
                        locationId,
                        size[0],
                        size[1],
                        bilinear,
                        texture.getFilter(),
                        texture.getWrap()
                ));
            }
        }

        return new VpfxRuntimeTextureManifest(runtimeNamespace, result);
    }

    public static void write(VpfxRuntimeTextureManifest manifest, Path runtimeRoot) throws IOException {
        Path outPath = runtimeRoot
                .resolve("assets")
                .resolve(manifest.getRuntimeNamespace())
                .resolve("vpfx")
                .resolve("textures.json");

        Files.createDirectories(outPath.getParent());
        Files.writeString(outPath, toJson(manifest), StandardCharsets.UTF_8);
    }

    public static String toJson(VpfxRuntimeTextureManifest manifest) {
        JsonObject root = new JsonObject();
        root.addProperty("runtime_namespace", manifest.getRuntimeNamespace());

        JsonObject textures = new JsonObject();
        for (VpfxRuntimeTextureDescriptor descriptor : manifest.getTextures().values()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("source_zip_path", descriptor.getSourceZipPath());
            entry.addProperty("effect_path", descriptor.getEffectPath());
            entry.addProperty("location_id", descriptor.getLocationId());
            entry.addProperty("width", descriptor.getWidth());
            entry.addProperty("height", descriptor.getHeight());
            entry.addProperty("bilinear", descriptor.isBilinear());
            entry.addProperty("filter", descriptor.getFilter().getJsonName());
            entry.addProperty("wrap", descriptor.getWrap().getJsonName());
            textures.add(descriptor.getLogicalName(), entry);
        }

        root.add("textures", textures);
        return GSON.toJson(root);
    }

    private static String normalizeZipPath(String zipTexturePath) {
        String normalized = zipTexturePath.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static String toEffectPath(String sourceZipPath) throws IOException {
        String normalized = normalizeZipPath(sourceZipPath);

        if (!normalized.startsWith("textures/")) {
            throw new IOException("Declared texture path must start with 'textures/': " + sourceZipPath);
        }
        if (!normalized.endsWith(".png")) {
            throw new IOException("Declared texture path must end with '.png': " + sourceZipPath);
        }

        String stripped = normalized.substring("textures/".length());
        return stripped.substring(0, stripped.length() - ".png".length());
    }

    private static int[] readTextureSize(ZipFile zipFile, String sourceZipPath) throws IOException {
        ZipEntry entry = zipFile.getEntry(sourceZipPath);
        if (entry == null || entry.isDirectory()) {
            throw new IOException("Declared texture missing from zip: " + sourceZipPath);
        }

        try (InputStream in = zipFile.getInputStream(entry)) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new IOException("Failed to decode PNG texture: " + sourceZipPath);
            }
            return new int[]{image.getWidth(), image.getHeight()};
        }
    }
}