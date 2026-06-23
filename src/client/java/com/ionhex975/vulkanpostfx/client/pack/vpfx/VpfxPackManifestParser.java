package com.ionhex975.vulkanpostfx.client.pack.vpfx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class VpfxPackManifestParser {
    private static final Pattern PACK_ID_PATTERN = Pattern.compile("^[a-z0-9_.-]{3,64}$");
    private static final Pattern TARGET_ID_PATTERN = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");
    private static final Pattern TEXTURE_NAME_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    public VpfxPackManifest parse(ZipFile zipFile) throws VpfxManifestParseException {
        JsonObject root = readPackJson(zipFile);

        int formatVersion = getRequiredInt(root, "format_version", "format_version");
        if (formatVersion != 1) {
            throw new VpfxManifestParseException(
                    "F002",
                    "format_version",
                    "Unsupported VPFX format_version: " + formatVersion
            );
        }

        String packId = getRequiredString(root, "pack_id", "pack_id");
        if (!PACK_ID_PATTERN.matcher(packId).matches()) {
            throw new VpfxManifestParseException(
                    "F003",
                    "pack_id",
                    "Invalid pack_id. Expected pattern ^[a-z0-9_.-]{3,64}$"
            );
        }

        String name = getRequiredString(root, "name", "name");
        String version = getRequiredString(root, "version", "version");
        String entryPostEffect = getRequiredString(root, "entry_post_effect", "entry_post_effect");

        if (zipFile.getEntry(entryPostEffect) == null) {
            throw new VpfxManifestParseException(
                    "F005",
                    "entry_post_effect",
                    "Entry post effect file not found in zip: " + entryPostEffect
            );
        }

        String author = getOptionalString(root, "author");
        String description = getOptionalString(root, "description");

        VpfxCapabilitySet capabilities = parseCapabilities(root);
        Map<String, String> targets = parseTargets(root, capabilities);
        Map<String, VpfxTextureManifestEntry> textures = parseTextures(root, zipFile);
        VpfxPackMetadata metadata = parseMetadata(root);

        return new VpfxPackManifest(
                formatVersion,
                packId,
                name,
                version,
                author,
                description,
                entryPostEffect,
                capabilities,
                targets,
                textures,
                metadata
        );
    }

    private JsonObject readPackJson(ZipFile zipFile) throws VpfxManifestParseException {
        ZipEntry entry = zipFile.getEntry("pack.json");
        if (entry == null) {
            throw new VpfxManifestParseException(
                    "F001",
                    "pack.json",
                    "Missing pack.json in VPFX pack root"
            );
        }

        try (InputStream inputStream = zipFile.getInputStream(entry);
             InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {

            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new VpfxManifestParseException(
                        "F001",
                        "pack.json",
                        "pack.json root must be a JSON object"
                );
            }

            return parsed.getAsJsonObject();
        } catch (IOException e) {
            throw new VpfxManifestParseException(
                    "F001",
                    "pack.json",
                    "Failed to read pack.json: " + e.getMessage()
            );
        }
    }

    private VpfxCapabilitySet parseCapabilities(JsonObject root) throws VpfxManifestParseException {
        JsonObject object = getRequiredObject(root, "capabilities", "capabilities");

        boolean sceneColor = getRequiredBoolean(object, "scene_color", "capabilities.scene_color");
        boolean sceneDepth = getRequiredBoolean(object, "scene_depth", "capabilities.scene_depth");
        boolean shadowDepth = getRequiredBoolean(object, "shadow_depth", "capabilities.shadow_depth");
        boolean customTargets = getRequiredBoolean(object, "custom_targets", "capabilities.custom_targets");
        boolean compute = getRequiredBoolean(object, "compute", "capabilities.compute");

        return new VpfxCapabilitySet(sceneColor, sceneDepth, shadowDepth, customTargets, compute);
    }

    private Map<String, String> parseTargets(JsonObject root, VpfxCapabilitySet capabilities)
            throws VpfxManifestParseException {
        Map<String, String> result = new LinkedHashMap<>();

        if (!root.has("targets")) {
            if (capabilities.isShadowDepth()) {
                throw new VpfxManifestParseException(
                        "F007",
                        "targets",
                        "targets is required because shadow_depth capability is enabled"
                );
            }
            return result;
        }

        JsonObject targetsObject = getRequiredObject(root, "targets", "targets");
        for (Map.Entry<String, JsonElement> entry : targetsObject.entrySet()) {
            String key = entry.getKey();
            String path = "targets." + key;

            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()) {
                throw new VpfxManifestParseException(
                        "F006",
                        path,
                        "Target mapping value must be a string identifier"
                );
            }

            String value = entry.getValue().getAsString();
            if (!TARGET_ID_PATTERN.matcher(value).matches()) {
                throw new VpfxManifestParseException(
                        "F006",
                        path,
                        "Invalid target identifier: " + value
                );
            }

            result.put(key, value);
        }

        if (capabilities.isShadowDepth() && !result.containsKey("shadow_depth")) {
            throw new VpfxManifestParseException(
                    "F007",
                    "targets.shadow_depth",
                    "shadow_depth target mapping is required when shadow_depth capability is enabled"
            );
        }

        return result;
    }

    private Map<String, VpfxTextureManifestEntry> parseTextures(JsonObject root, ZipFile zipFile)
            throws VpfxManifestParseException {
        Map<String, VpfxTextureManifestEntry> result = new LinkedHashMap<>();

        if (!root.has("textures")) {
            return result;
        }

        JsonObject texturesObject = getRequiredObject(root, "textures", "textures");
        for (Map.Entry<String, JsonElement> entry : texturesObject.entrySet()) {
            String textureName = entry.getKey();
            String pathBase = "textures." + textureName;

            if (!TEXTURE_NAME_PATTERN.matcher(textureName).matches()) {
                throw new VpfxManifestParseException(
                        "F008",
                        pathBase,
                        "Invalid texture name. Expected GLSL-safe identifier"
                );
            }

            if (!entry.getValue().isJsonObject()) {
                throw new VpfxManifestParseException(
                        "F008",
                        pathBase,
                        "Texture entry must be an object"
                );
            }

            JsonObject object = entry.getValue().getAsJsonObject();
            String texturePath = getRequiredString(object, "path", pathBase + ".path");
            if (texturePath.isBlank()) {
                throw new VpfxManifestParseException(
                        "F008",
                        pathBase + ".path",
                        "Texture path must not be blank"
                );
            }

            if (zipFile.getEntry(texturePath) == null) {
                throw new VpfxManifestParseException(
                        "F008",
                        pathBase + ".path",
                        "Texture file not found in zip: " + texturePath
                );
            }

            String filterRaw = getOptionalString(object, "filter");
            String wrapRaw = getOptionalString(object, "wrap");

            VpfxTextureFilter filter;
            VpfxTextureWrap wrap;
            try {
                filter = VpfxTextureFilter.fromJson(filterRaw.isBlank() ? null : filterRaw);
            } catch (IllegalArgumentException e) {
                throw new VpfxManifestParseException(
                        "F008",
                        pathBase + ".filter",
                        e.getMessage()
                );
            }

            try {
                wrap = VpfxTextureWrap.fromJson(wrapRaw.isBlank() ? null : wrapRaw);
            } catch (IllegalArgumentException e) {
                throw new VpfxManifestParseException(
                        "F008",
                        pathBase + ".wrap",
                        e.getMessage()
                );
            }

            result.put(textureName, new VpfxTextureManifestEntry(
                    textureName,
                    texturePath,
                    filter,
                    wrap
            ));
        }

        return result;
    }

    private VpfxPackMetadata parseMetadata(JsonObject root) throws VpfxManifestParseException {
        if (!root.has("metadata")) {
            return new VpfxPackMetadata("", "", List.of());
        }

        JsonObject object = getRequiredObject(root, "metadata", "metadata");
        String homepage = getOptionalString(object, "homepage");
        String license = getOptionalString(object, "license");

        List<String> tags = new ArrayList<>();
        if (object.has("tags")) {
            JsonElement element = object.get("tags");
            if (!element.isJsonArray()) {
                throw new VpfxManifestParseException(
                        "F006",
                        "metadata.tags",
                        "metadata.tags must be an array"
                );
            }

            JsonArray array = element.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                JsonElement tag = array.get(i);
                if (!tag.isJsonPrimitive() || !tag.getAsJsonPrimitive().isString()) {
                    throw new VpfxManifestParseException(
                            "F006",
                            "metadata.tags[" + i + "]",
                            "metadata.tags entries must be strings"
                    );
                }
                tags.add(tag.getAsString());
            }
        }

        return new VpfxPackMetadata(homepage, license, tags);
    }

    private JsonObject getRequiredObject(JsonObject parent, String key, String path)
            throws VpfxManifestParseException {
        if (!parent.has(key) || !parent.get(key).isJsonObject()) {
            throw new VpfxManifestParseException(
                    "F006",
                    path,
                    "Expected object field: " + key
            );
        }
        return parent.getAsJsonObject(key);
    }

    private String getRequiredString(JsonObject parent, String key, String path)
            throws VpfxManifestParseException {
        if (!parent.has(key) || !parent.get(key).isJsonPrimitive()
                || !parent.get(key).getAsJsonPrimitive().isString()) {
            throw new VpfxManifestParseException(
                    "F006",
                    path,
                    "Expected string field: " + key
            );
        }

        String value = parent.get(key).getAsString();
        if (value.isBlank()) {
            throw new VpfxManifestParseException(
                    "F006",
                    path,
                    "String field must not be blank: " + key
            );
        }

        return value;
    }

    private String getOptionalString(JsonObject parent, String key) throws VpfxManifestParseException {
        if (!parent.has(key)) {
            return "";
        }

        JsonElement element = parent.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new VpfxManifestParseException(
                    "F006",
                    key,
                    "Expected optional string field: " + key
            );
        }

        return element.getAsString();
    }

    private int getRequiredInt(JsonObject parent, String key, String path)
            throws VpfxManifestParseException {
        if (!parent.has(key) || !parent.get(key).isJsonPrimitive()
                || !parent.get(key).getAsJsonPrimitive().isNumber()) {
            throw new VpfxManifestParseException(
                    "F006",
                    path,
                    "Expected integer field: " + key
            );
        }
        return parent.get(key).getAsInt();
    }

    private boolean getRequiredBoolean(JsonObject parent, String key, String path)
            throws VpfxManifestParseException {
        if (!parent.has(key) || !parent.get(key).isJsonPrimitive()
                || !parent.get(key).getAsJsonPrimitive().isBoolean()) {
            throw new VpfxManifestParseException(
                    "F006",
                    path,
                    "Expected boolean field: " + key
            );
        }
        return parent.get(key).getAsBoolean();
    }
}