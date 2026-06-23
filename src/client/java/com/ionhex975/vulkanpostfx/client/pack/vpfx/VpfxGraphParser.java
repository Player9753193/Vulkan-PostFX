package com.ionhex975.vulkanpostfx.client.pack.vpfx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ionhex975.vulkanpostfx.VulkanPostFX;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class VpfxGraphParser {

    public VpfxGraphDefinition parse(ZipFile zipFile, String entryPath)
            throws VpfxGraphParseException {
        JsonObject root = readJsonObject(zipFile, entryPath);

        JsonObject targetsObject = getRequiredObject(root, "targets", "targets");
        JsonArray passesArray = getRequiredArray(root, "passes", "passes");

        Map<String, VpfxTargetDefinition> targets = parseTargets(targetsObject);
        List<VpfxPassDefinition> passes = parsePasses(passesArray);

        VulkanPostFX.LOGGER.info(
                "[{}] Parsed VPFX graph: entry='{}', targets={}, passes={}",
                VulkanPostFX.MOD_ID,
                entryPath,
                targets.size(),
                passes.size()
        );

        return new VpfxGraphDefinition(targets, passes);
    }

    private JsonObject readJsonObject(ZipFile zipFile, String entryPath)
            throws VpfxGraphParseException {
        ZipEntry entry = zipFile.getEntry(entryPath);
        if (entry == null) {
            throw new VpfxGraphParseException(
                    "G001",
                    entryPath,
                    "Graph entry file not found in zip: " + entryPath
            );
        }

        try (InputStream inputStream = zipFile.getInputStream(entry);
             InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {

            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new VpfxGraphParseException(
                        "G002",
                        entryPath,
                        "Graph root must be a JSON object"
                );
            }

            return parsed.getAsJsonObject();
        } catch (IOException e) {
            throw new VpfxGraphParseException(
                    "G003",
                    entryPath,
                    "Failed to read graph JSON: " + e.getMessage()
            );
        }
    }

    private Map<String, VpfxTargetDefinition> parseTargets(JsonObject targetsObject)
            throws VpfxGraphParseException {
        Map<String, VpfxTargetDefinition> result = new LinkedHashMap<>();

        for (Map.Entry<String, JsonElement> entry : targetsObject.entrySet()) {
            String targetId = entry.getKey();
            String path = "targets." + targetId;

            if (!entry.getValue().isJsonObject()) {
                throw new VpfxGraphParseException("G010", path, "Target definition must be an object");
            }

            JsonObject object = entry.getValue().getAsJsonObject();

            Double scale = null;
            boolean useDepth = false;
            boolean persistent = false;
            boolean history = false;
            boolean pingPong = false;
            float[] clearColor = null;

            if (object.has("scale")) {
                JsonElement scaleElement = object.get("scale");
                if (!scaleElement.isJsonPrimitive() || !scaleElement.getAsJsonPrimitive().isNumber()) {
                    throw new VpfxGraphParseException("G011", path + ".scale", "Target scale must be a number");
                }
                scale = scaleElement.getAsDouble();
            }

            if (object.has("use_depth")) {
                JsonElement useDepthElement = object.get("use_depth");
                if (!useDepthElement.isJsonPrimitive() || !useDepthElement.getAsJsonPrimitive().isBoolean()) {
                    throw new VpfxGraphParseException("G012", path + ".use_depth", "Target use_depth must be a boolean");
                }
                useDepth = useDepthElement.getAsBoolean();
            }

            persistent = getOptionalBoolean(object, "persistent", path + ".persistent", false);
            history = getOptionalBoolean(object, "history", path + ".history", false);
            pingPong = getOptionalBoolean(object, "ping_pong", path + ".ping_pong", false);

            if (object.has("clear_color")) {
                clearColor = parseClearColor(object.get("clear_color"), path + ".clear_color");
            }

            result.put(targetId, new VpfxTargetDefinition(
                    targetId,
                    scale,
                    useDepth,
                    clearColor,
                    persistent,
                    history,
                    pingPong
            ));
        }

        return result;
    }

    private List<VpfxPassDefinition> parsePasses(JsonArray passesArray)
            throws VpfxGraphParseException {
        List<VpfxPassDefinition> result = new ArrayList<>();

        for (int i = 0; i < passesArray.size(); i++) {
            JsonElement passElement = passesArray.get(i);
            String basePath = "passes[" + i + "]";

            if (!passElement.isJsonObject()) {
                throw new VpfxGraphParseException("G020", basePath, "Each pass must be an object");
            }

            JsonObject passObject = passElement.getAsJsonObject();

            String id = getOptionalString(passObject, "id");
            String debugLabel = getOptionalString(passObject, "debug_label");
            String vertexShader = getRequiredString(passObject, "vertex_shader", basePath + ".vertex_shader");
            String fragmentShader = getRequiredString(passObject, "fragment_shader", basePath + ".fragment_shader");
            JsonArray inputsArray = getRequiredArray(passObject, "inputs", basePath + ".inputs");
            String output = getRequiredString(passObject, "output", basePath + ".output");

            List<VpfxPassInput> inputs = parseInputs(inputsArray, basePath + ".inputs");

            result.add(new VpfxPassDefinition(id, debugLabel, vertexShader, fragmentShader, inputs, output));
        }

        return result;
    }

    private List<VpfxPassInput> parseInputs(JsonArray inputsArray, String basePath)
            throws VpfxGraphParseException {
        List<VpfxPassInput> result = new ArrayList<>();

        for (int i = 0; i < inputsArray.size(); i++) {
            JsonElement inputElement = inputsArray.get(i);
            String path = basePath + "[" + i + "]";

            if (!inputElement.isJsonObject()) {
                throw new VpfxGraphParseException("G030", path, "Each pass input must be an object");
            }

            JsonObject inputObject = inputElement.getAsJsonObject();
            String samplerName = getRequiredString(inputObject, "sampler_name", path + ".sampler_name");

            String target = getOptionalString(inputObject, "target");
            String texture = getOptionalString(inputObject, "texture");

            boolean useDepthBuffer = false;
            if (inputObject.has("use_depth_buffer")) {
                JsonElement depthElement = inputObject.get("use_depth_buffer");
                if (!depthElement.isJsonPrimitive() || !depthElement.getAsJsonPrimitive().isBoolean()) {
                    throw new VpfxGraphParseException(
                            "G031",
                            path + ".use_depth_buffer",
                            "use_depth_buffer must be a boolean"
                    );
                }
                useDepthBuffer = depthElement.getAsBoolean();
            }

            boolean hasTarget = target != null && !target.isBlank();
            boolean hasTexture = texture != null && !texture.isBlank();

            if (hasTarget == hasTexture) {
                throw new VpfxGraphParseException(
                        "G032",
                        path,
                        "Each pass input must contain exactly one of: target or texture"
                );
            }

            if (hasTexture && useDepthBuffer) {
                throw new VpfxGraphParseException(
                        "G033",
                        path,
                        "texture input does not support use_depth_buffer=true"
                );
            }

            result.add(new VpfxPassInput(
                    samplerName,
                    hasTarget ? target : "",
                    hasTexture ? texture : "",
                    useDepthBuffer
            ));
        }

        return result;
    }

    private float[] parseClearColor(JsonElement element, String path)
            throws VpfxGraphParseException {
        if (!element.isJsonArray()) {
            throw new VpfxGraphParseException("G040", path, "clear_color must be an array of 4 numbers");
        }

        JsonArray array = element.getAsJsonArray();
        if (array.size() != 4) {
            throw new VpfxGraphParseException("G041", path, "clear_color must contain exactly 4 elements");
        }

        float[] values = new float[4];
        for (int i = 0; i < 4; i++) {
            JsonElement value = array.get(i);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                throw new VpfxGraphParseException("G042", path + "[" + i + "]", "clear_color elements must be numbers");
            }
            values[i] = value.getAsFloat();
        }

        return values;
    }

    private JsonObject getRequiredObject(JsonObject parent, String key, String path)
            throws VpfxGraphParseException {
        if (!parent.has(key) || !parent.get(key).isJsonObject()) {
            throw new VpfxGraphParseException("G050", path, "Missing required object field: " + key);
        }
        return parent.getAsJsonObject(key);
    }

    private JsonArray getRequiredArray(JsonObject parent, String key, String path)
            throws VpfxGraphParseException {
        if (!parent.has(key) || !parent.get(key).isJsonArray()) {
            throw new VpfxGraphParseException("G052", path, "Missing required array field: " + key);
        }
        return parent.getAsJsonArray(key);
    }

    private String getRequiredString(JsonObject parent, String key, String path)
            throws VpfxGraphParseException {
        if (!parent.has(key) || !parent.get(key).isJsonPrimitive()
                || !parent.get(key).getAsJsonPrimitive().isString()) {
            throw new VpfxGraphParseException("G054", path, "Missing required string field: " + key);
        }

        String value = parent.get(key).getAsString();
        if (value.isBlank()) {
            throw new VpfxGraphParseException("G056", path, "String field must not be blank: " + key);
        }
        return value;
    }

    private boolean getOptionalBoolean(JsonObject parent, String key, String path, boolean defaultValue)
            throws VpfxGraphParseException {
        if (!parent.has(key)) {
            return defaultValue;
        }

        JsonElement element = parent.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new VpfxGraphParseException("G059", path, "Optional field must be a boolean: " + key);
        }

        return element.getAsBoolean();
    }

    private String getOptionalString(JsonObject parent, String key) throws VpfxGraphParseException {
        if (!parent.has(key)) {
            return "";
        }

        JsonElement element = parent.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new VpfxGraphParseException("G058", key, "Optional field must be a string: " + key);
        }

        return element.getAsString();
    }
}