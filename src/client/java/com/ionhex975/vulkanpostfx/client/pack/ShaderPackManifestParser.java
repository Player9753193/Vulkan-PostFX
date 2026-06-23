package com.ionhex975.vulkanpostfx.client.pack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;

/**
 * 解析 pack.json 的最小解析器。
 *
 * 当前规则：
 * - 必填：id / name / version / entry_post_effect
 * - 可选：entry_effect_key
 */
public final class ShaderPackManifestParser {
    private ShaderPackManifestParser() {
    }

    public static ShaderPackManifest parse(Reader reader) throws IOException {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

        String id = getRequiredString(root, "id");
        String name = getRequiredString(root, "name");
        int version = getRequiredInt(root, "version");
        String entryEffectKey = getOptionalString(root, "entry_effect_key", "");
        String entryPostEffect = getRequiredString(root, "entry_post_effect");

        return new ShaderPackManifest(
                id,
                name,
                version,
                entryEffectKey,
                entryPostEffect
        );
    }

    private static String getRequiredString(JsonObject root, String key) throws IOException {
        if (!root.has(key) || !root.get(key).isJsonPrimitive()) {
            throw new IOException("Missing or invalid string field: " + key);
        }
        return root.get(key).getAsString();
    }

    private static String getOptionalString(JsonObject root, String key, String defaultValue) {
        if (!root.has(key) || !root.get(key).isJsonPrimitive()) {
            return defaultValue;
        }
        return root.get(key).getAsString();
    }

    private static int getRequiredInt(JsonObject root, String key) throws IOException {
        if (!root.has(key) || !root.get(key).isJsonPrimitive()) {
            throw new IOException("Missing or invalid int field: " + key);
        }
        return root.get(key).getAsInt();
    }
}