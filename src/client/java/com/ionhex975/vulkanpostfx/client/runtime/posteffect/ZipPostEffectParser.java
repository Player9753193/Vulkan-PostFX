package com.ionhex975.vulkanpostfx.client.runtime.posteffect;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 把 ZIP 包中的 entry_post_effect JSON 解析成最小运行时中间表示。
 */
public final class ZipPostEffectParser {
    private ZipPostEffectParser() {
    }

    public static ZipPostEffectConfig parse(String rawJson) {
        JsonObject root = JsonParser.parseString(rawJson).getAsJsonObject();

        Set<String> targets = parseTargets(root);
        List<ZipPostEffectPass> passes = parsePasses(root);

        return new ZipPostEffectConfig(targets, passes);
    }

    private static Set<String> parseTargets(JsonObject root) {
        if (!root.has("targets") || !root.get("targets").isJsonObject()) {
            throw new IllegalStateException("post effect config is missing object field: targets");
        }

        JsonObject targetsObject = root.getAsJsonObject("targets");
        Set<String> targets = new LinkedHashSet<>();
        for (String key : targetsObject.keySet()) {
            targets.add(key);
        }
        return targets;
    }

    private static List<ZipPostEffectPass> parsePasses(JsonObject root) {
        if (!root.has("passes") || !root.get("passes").isJsonArray()) {
            throw new IllegalStateException("post effect config is missing array field: passes");
        }

        JsonArray passesArray = root.getAsJsonArray("passes");
        List<ZipPostEffectPass> passes = new ArrayList<>();

        for (JsonElement passElement : passesArray) {
            if (!passElement.isJsonObject()) {
                throw new IllegalStateException("each pass must be a JSON object");
            }

            JsonObject passObject = passElement.getAsJsonObject();
            String vertexShader = getRequiredString(passObject, "vertex_shader");
            String fragmentShader = getRequiredString(passObject, "fragment_shader");
            String output = getRequiredString(passObject, "output");

            List<ZipPostEffectInput> inputs = new ArrayList<>();
            if (passObject.has("inputs")) {
                if (!passObject.get("inputs").isJsonArray()) {
                    throw new IllegalStateException("pass.inputs must be an array");
                }

                JsonArray inputsArray = passObject.getAsJsonArray("inputs");
                for (JsonElement inputElement : inputsArray) {
                    if (!inputElement.isJsonObject()) {
                        throw new IllegalStateException("each input must be a JSON object");
                    }

                    JsonObject inputObject = inputElement.getAsJsonObject();
                    String samplerName = getRequiredString(inputObject, "sampler_name");

                    String target = getOptionalString(inputObject, "target");
                    String texture = getOptionalString(inputObject, "texture");

                    boolean useDepthBuffer = false;
                    if (inputObject.has("use_depth_buffer")) {
                        JsonElement depthElement = inputObject.get("use_depth_buffer");
                        if (!depthElement.isJsonPrimitive() || !depthElement.getAsJsonPrimitive().isBoolean()) {
                            throw new IllegalStateException("input.use_depth_buffer must be a boolean");
                        }
                        useDepthBuffer = depthElement.getAsBoolean();
                    }

                    boolean hasTarget = target != null && !target.isBlank();
                    boolean hasTexture = texture != null && !texture.isBlank();

                    if (hasTarget == hasTexture) {
                        throw new IllegalStateException("each input must contain exactly one of: target or texture");
                    }

                    if (hasTexture && useDepthBuffer) {
                        throw new IllegalStateException("texture input does not support use_depth_buffer=true");
                    }

                    inputs.add(new ZipPostEffectInput(
                            samplerName,
                            hasTarget ? target : "",
                            hasTexture ? texture : "",
                            useDepthBuffer
                    ));
                }
            }

            passes.add(new ZipPostEffectPass(
                    vertexShader,
                    fragmentShader,
                    inputs,
                    output
            ));
        }

        return passes;
    }

    private static String getRequiredString(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            throw new IllegalStateException("missing or invalid string field: " + key);
        }
        String value = object.get(key).getAsString();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("missing or blank string field: " + key);
        }
        return value;
    }

    private static String getOptionalString(JsonObject object, String key) {
        if (!object.has(key)) {
            return "";
        }
        if (!object.get(key).isJsonPrimitive()) {
            throw new IllegalStateException("invalid string field: " + key);
        }
        return object.get(key).getAsString();
    }
}