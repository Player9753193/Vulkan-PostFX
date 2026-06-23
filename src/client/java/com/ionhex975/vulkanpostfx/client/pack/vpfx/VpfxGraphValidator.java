package com.ionhex975.vulkanpostfx.client.pack.vpfx;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class VpfxGraphValidator {
    private static final Pattern SAMPLER_NAME_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Pattern TARGET_ID_PATTERN = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");
    private static final Set<String> BUILTIN_TARGETS = Set.of(
            "minecraft:main",
            "minecraft:scene_color",
            "minecraft:scene_depth",
            "minecraft:shadow_depth",
            "vulkanpostfx:scene_depth",
            "vulkanpostfx:shadow_depth"
    );

    public List<VpfxValidationMessage> validate(
            VpfxPackManifest manifest,
            VpfxGraphDefinition graph,
            VpfxRuntimeCapabilities runtimeCapabilities
    ) {
        List<VpfxValidationMessage> messages = new ArrayList<>();

        validateCapabilities(manifest, runtimeCapabilities, messages);

        if (graph.getPasses().isEmpty()) {
            messages.add(fatal("G002", "passes", "VPFX graph must contain at least one pass"));
            return messages;
        }

        Map<String, VpfxTargetDefinition> declaredTargets = graph.getTargets();
        Map<String, VpfxTextureManifestEntry> declaredTextures = manifest.getTextures();

        validateDeclaredTargets(declaredTargets, messages);
        validatePasses(graph, declaredTargets, declaredTextures, runtimeCapabilities, messages);
        validateGraphWritesToMain(graph, messages);

        return messages;
    }

    private void validateDeclaredTargets(
            Map<String, VpfxTargetDefinition> declaredTargets,
            List<VpfxValidationMessage> messages
    ) {
        for (Map.Entry<String, VpfxTargetDefinition> entry : declaredTargets.entrySet()) {
            String targetId = entry.getKey();
            if (!TARGET_ID_PATTERN.matcher(targetId).matches()) {
                messages.add(fatal("G007", "targets." + targetId, "Invalid target identifier"));
            }

            VpfxTargetDefinition target = entry.getValue();
            if (target.getScale().isPresent()) {
                double scale = target.getScale().get();
                if (!Double.isFinite(scale) || scale <= 0.0 || scale > 1.0) {
                    messages.add(fatal("G011", "targets." + targetId + ".scale", "scale must be a finite number in (0, 1]"));
                }
            }
        }
    }

    private void validatePasses(
            VpfxGraphDefinition graph,
            Map<String, VpfxTargetDefinition> declaredTargets,
            Map<String, VpfxTextureManifestEntry> declaredTextures,
            VpfxRuntimeCapabilities runtimeCapabilities,
            List<VpfxValidationMessage> messages
    ) {
        Set<String> writtenTargets = new LinkedHashSet<>();

        for (int i = 0; i < graph.getPasses().size(); i++) {
            VpfxPassDefinition pass = graph.getPasses().get(i);
            String passPath = pass.identityOrIndex(i);

            if (pass.getInputs().isEmpty()) {
                messages.add(fatal("G003", passPath + ".inputs", "Each pass must have at least one input"));
            }

            Set<String> samplerNames = new HashSet<>();
            Set<String> passInputTargets = new HashSet<>();

            for (int j = 0; j < pass.getInputs().size(); j++) {
                VpfxPassInput input = pass.getInputs().get(j);
                String inputPath = passPath + ".inputs[" + j + "]";

                if (!SAMPLER_NAME_PATTERN.matcher(input.getSamplerName()).matches()) {
                    messages.add(fatal("G008", inputPath + ".sampler_name", "Invalid sampler_name"));
                }

                if (!samplerNames.add(input.getSamplerName())) {
                    messages.add(fatal("G009", inputPath + ".sampler_name", "Duplicate sampler_name within one pass"));
                }

                boolean hasTarget = input.isTargetInput();
                boolean hasTexture = input.isTextureInput();

                if (hasTarget == hasTexture) {
                    messages.add(fatal("G012", inputPath, "Each input must contain exactly one of: target or texture"));
                    continue;
                }

                if (hasTarget) {
                    passInputTargets.add(input.getTarget());

                    boolean builtin = BUILTIN_TARGETS.contains(input.getTarget());
                    boolean internal = declaredTargets.containsKey(input.getTarget());

                    if (!builtin && !internal) {
                        messages.add(fatal("G005", inputPath + ".target", "Input target not found: " + input.getTarget()));
                    }

                    validateUseDepthBuffer(input, inputPath, declaredTargets, runtimeCapabilities, messages);

                    if (!builtin && !writtenTargets.contains(input.getTarget())) {
                        messages.add(fatal("G017", inputPath + ".target",
                                "Pass reads from target '" + input.getTarget()
                                        + "' that has not been written yet (pass index " + i + "). "
                                        + "Targets are only available after a previous pass writes to them."));
                    }
                }

                if (hasTexture) {
                    if (!declaredTextures.containsKey(input.getTexture())) {
                        messages.add(fatal("G013", inputPath + ".texture", "Input texture not declared: " + input.getTexture()));
                    }

                    if (input.isUseDepthBuffer()) {
                        messages.add(fatal("G014", inputPath, "texture input does not support use_depth_buffer=true"));
                    }
                }
            }

            String output = pass.getOutput();
            if (!"minecraft:main".equals(output) && !declaredTargets.containsKey(output)) {
                messages.add(fatal("G004", passPath + ".output", "Output target not declared: " + output));
            }

            if (passInputTargets.contains(output) && !BUILTIN_TARGETS.contains(output)) {
                messages.add(fatal("G015", passPath,
                        "Pass writes to '" + output + "' but also reads from the same target (self-read/write). "
                                + "A pass must not read from its own output."));
            }

            writtenTargets.add(output);
        }
    }

    private void validateUseDepthBuffer(
            VpfxPassInput input,
            String inputPath,
            Map<String, VpfxTargetDefinition> declaredTargets,
            VpfxRuntimeCapabilities runtimeCapabilities,
            List<VpfxValidationMessage> messages
    ) {
        if (!input.isUseDepthBuffer()) {
            return;
        }

        String target = input.getTarget();

        if ("minecraft:main".equals(target)) {
            if (!runtimeCapabilities.isSceneDepth()) {
                messages.add(fatal("G006", inputPath, "Scene depth requested but runtime does not provide it"));
            }
        } else if ("vulkanpostfx:scene_depth".equals(target)) {
            if (!runtimeCapabilities.isSceneDepth()) {
                messages.add(fatal("G006", inputPath, "Scene depth requested but runtime does not provide it"));
            }
        } else if ("vulkanpostfx:shadow_depth".equals(target)) {
            if (!runtimeCapabilities.isShadowDepth()) {
                messages.add(fatal("G006", inputPath, "Shadow depth requested but runtime does not provide it"));
            }
        } else if (declaredTargets.containsKey(target)) {
            VpfxTargetDefinition targetDef = declaredTargets.get(target);
            if (!targetDef.isUseDepth()) {
                messages.add(fatal(
                        "G006",
                        inputPath,
                        "use_depth_buffer=true but target '" + target + "' is not declared with use_depth=true"
                ));
            }
        }
    }

    private void validateGraphWritesToMain(
            VpfxGraphDefinition graph,
            List<VpfxValidationMessage> messages
    ) {
        boolean writesToMain = false;
        for (int i = 0; i < graph.getPasses().size(); i++) {
            VpfxPassDefinition pass = graph.getPasses().get(i);
            if ("minecraft:main".equals(pass.getOutput())) {
                writesToMain = true;
                break;
            }
        }

        if (!writesToMain) {
            messages.add(fatal("G016", "passes",
                    "Graph does not write to minecraft:main. At least one pass must output to minecraft:main "
                            + "to produce a visible result."));
        }
    }

    private void validateCapabilities(
            VpfxPackManifest manifest,
            VpfxRuntimeCapabilities runtimeCapabilities,
            List<VpfxValidationMessage> messages
    ) {
        VpfxCapabilitySet required = manifest.getCapabilities();

        if (required.isSceneColor() && !runtimeCapabilities.isSceneColor()) {
            messages.add(fatal("C001", "capabilities.scene_color", "Pack requires scene_color but runtime does not provide it"));
        }
        if (required.isSceneDepth() && !runtimeCapabilities.isSceneDepth()) {
            messages.add(fatal("C002", "capabilities.scene_depth", "Pack requires scene_depth but runtime does not provide it"));
        }
        if (required.isShadowDepth() && !runtimeCapabilities.isShadowDepth()) {
            messages.add(fatal("C003", "capabilities.shadow_depth", "Pack requires shadow_depth but runtime does not provide it"));
        }
        if (required.isCustomTargets() && !runtimeCapabilities.isCustomTargets()) {
            messages.add(fatal("C004", "capabilities.custom_targets", "Pack requires custom_targets but runtime does not provide it"));
        }
        if (required.isCompute() && !runtimeCapabilities.isCompute()) {
            messages.add(fatal("C005", "capabilities.compute", "Pack requires compute but runtime does not provide it"));
        }
    }

    private VpfxValidationMessage fatal(String code, String path, String message) {
        return new VpfxValidationMessage(VpfxValidationMessage.Severity.FATAL, code, path, message);
    }
}
