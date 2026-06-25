package com.ionhex975.vulkanpostfx.client.runtime.zip;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ionhex975.vulkanpostfx.client.runtime.texture.VpfxRuntimeTextureDescriptor;
import com.ionhex975.vulkanpostfx.client.runtime.texture.VpfxRuntimeTextureManifest;
import com.ionhex975.vulkanpostfx.client.shader.uniform.VpfxBuiltinUniformBuffer;

public final class ZipPostEffectNamespaceRewriter {
    private ZipPostEffectNamespaceRewriter() {
    }

    public static String rewrite(
            String rawJson,
            String originalNamespace,
            String runtimeNamespace,
            VpfxRuntimeTextureManifest runtimeTextureManifest
    ) {
        JsonObject root = JsonParser.parseString(rawJson).getAsJsonObject();

        rewriteTargets(root, originalNamespace, runtimeNamespace);
        rewritePasses(root, originalNamespace, runtimeNamespace, runtimeTextureManifest);

        return root.toString();
    }

    private static void rewriteTargets(JsonObject root, String originalNamespace, String runtimeNamespace) {
        if (!root.has("targets") || !root.get("targets").isJsonObject()) {
            return;
        }

        JsonObject oldTargets = root.getAsJsonObject("targets");
        JsonObject newTargets = new JsonObject();

        for (String key : oldTargets.keySet()) {
            String rewrittenKey = rewriteNamespacedString(key, originalNamespace, runtimeNamespace);
            newTargets.add(rewrittenKey, oldTargets.get(key));
        }

        root.add("targets", newTargets);
    }

    private static void rewritePasses(
            JsonObject root,
            String originalNamespace,
            String runtimeNamespace,
            VpfxRuntimeTextureManifest runtimeTextureManifest
    ) {
        if (!root.has("passes") || !root.get("passes").isJsonArray()) {
            return;
        }

        JsonArray passes = root.getAsJsonArray("passes");
        for (JsonElement passElement : passes) {
            if (!passElement.isJsonObject()) {
                continue;
            }

            JsonObject pass = passElement.getAsJsonObject();

            rewriteStringField(pass, "vertex_shader", originalNamespace, runtimeNamespace);
            rewriteStringField(pass, "fragment_shader", originalNamespace, runtimeNamespace);
            rewriteStringField(pass, "output", originalNamespace, runtimeNamespace);

            ensureVpfxBuiltinsUniformBlock(pass);

            if (pass.has("inputs") && pass.get("inputs").isJsonArray()) {
                JsonArray inputs = pass.getAsJsonArray("inputs");
                for (JsonElement inputElement : inputs) {
                    if (!inputElement.isJsonObject()) {
                        continue;
                    }

                    JsonObject input = inputElement.getAsJsonObject();

                    if (input.has("target")) {
                        rewriteStringField(input, "target", originalNamespace, runtimeNamespace);
                    }

                    if (input.has("texture")) {
                        String logicalTextureName = input.get("texture").getAsString();
                        VpfxRuntimeTextureDescriptor descriptor =
                                runtimeTextureManifest.getTexture(logicalTextureName);

                        if (descriptor == null) {
                            throw new IllegalStateException(
                                    "Texture logical name is not registered in runtime texture manifest: " + logicalTextureName
                            );
                        }

                        input.remove("texture");
                        input.remove("target");
                        input.remove("use_depth_buffer");

                        input.addProperty("location", descriptor.getLocationId());
                        input.addProperty("width", descriptor.getWidth());
                        input.addProperty("height", descriptor.getHeight());
                        input.addProperty("bilinear", descriptor.isBilinear());
                    }
                }
            }
        }
    }

    private static void ensureVpfxBuiltinsUniformBlock(JsonObject pass) {
        JsonObject uniforms;
        if (pass.has("uniforms") && pass.get("uniforms").isJsonObject()) {
            uniforms = pass.getAsJsonObject("uniforms");
        } else {
            uniforms = new JsonObject();
            pass.add("uniforms", uniforms);
        }

        if (uniforms.has(VpfxBuiltinUniformBuffer.BLOCK_NAME)) {
            return;
        }

        // 15 * vec4 + 15 * mat4 = 75 vec4 slots。
        // 这里的 JSON uniforms 只负责让 PostPass 为 VpfxBuiltins 分到足够大的 GPU buffer。
        final int vec4SlotCount = 75;

        JsonArray blockValues = new JsonArray();

        for (int i = 0; i < vec4SlotCount; i++) {
            JsonObject vec4Value = new JsonObject();
            vec4Value.addProperty("type", "vec4");

            JsonArray value = new JsonArray();
            value.add(0.0F);
            value.add(0.0F);
            value.add(0.0F);
            value.add(0.0F);

            vec4Value.add("value", value);
            blockValues.add(vec4Value);
        }

        uniforms.add(VpfxBuiltinUniformBuffer.BLOCK_NAME, blockValues);
    }

    private static void rewriteStringField(
            JsonObject object,
            String fieldName,
            String originalNamespace,
            String runtimeNamespace
    ) {
        if (!object.has(fieldName) || !object.get(fieldName).isJsonPrimitive()) {
            return;
        }

        String value = object.get(fieldName).getAsString();
        object.addProperty(fieldName, rewriteNamespacedString(value, originalNamespace, runtimeNamespace));
    }

    private static String rewriteNamespacedString(
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
}
