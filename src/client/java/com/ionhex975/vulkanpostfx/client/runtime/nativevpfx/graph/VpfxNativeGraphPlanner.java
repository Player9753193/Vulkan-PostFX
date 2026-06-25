package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.graph;

import com.ionhex975.vulkanpostfx.client.pack.ActiveShaderPackManager;
import com.ionhex975.vulkanpostfx.client.pack.ShaderPackContainer;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxGraphDefinition;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxNativePackDefinition;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxPassDefinition;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxPassInput;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxTargetDefinition;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.VpfxNativeFailureStage;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.VpfxNativePipelineKey;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.VpfxPassType;
import com.ionhex975.vulkanpostfx.client.runtime.texture.dynamic.VpfxRuntimeTextureBus;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class VpfxNativeGraphPlanner {

    private VpfxNativeGraphPlanner() {
    }

    public static VpfxNativeGraphPlanResult plan() {
        ShaderPackContainer activePack = ActiveShaderPackManager.getActivePack();
        if (activePack == null || !activePack.isVpfxNativePack()) {
            return unsupported("no active VPFX native pack");
        }

        VpfxNativePackDefinition vpfxDef = activePack.vpfxDefinition();
        if (vpfxDef == null || vpfxDef.getGraph() == null) {
            return unsupported("VPFX native pack definition or graph unavailable");
        }

        VpfxGraphDefinition graph = vpfxDef.getGraph();
        Map<String, VpfxTargetDefinition> declaredTargets = graph.getTargets();
        List<VpfxPassDefinition> passes = graph.getPasses();
        if (passes.isEmpty()) {
            return unsupported("VPFX native pack graph has no passes");
        }

        Set<String> readableTargets = new LinkedHashSet<>();
        readableTargets.add("minecraft:main");
        readableTargets.add("minecraft:scene_color");
        readableTargets.add("minecraft:scene_depth");
        readableTargets.add("vulkanpostfx:scene_depth");
        readableTargets.add("minecraft:shadow_depth");
        readableTargets.add("vulkanpostfx:shadow_depth");
        readableTargets.addAll(declaredTargets.keySet());
        for (VpfxTargetDefinition target : declaredTargets.values()) {
            if (target.isHistory()) {
                readableTargets.add(historyAlias(target.getId()));
            }
        }

        VpfxNativeRuntimeGraph.Builder runtimeGraphBuilder = VpfxNativeRuntimeGraph.builder()
                .packId(vpfxDef.getManifest().getPackId())
                .addTarget(new VpfxNativeTargetNode("minecraft:scene_color", "COLOR"))
                .addTarget(new VpfxNativeTargetNode("minecraft:main", "COLOR"))
                .addTarget(new VpfxNativeTargetNode("minecraft:scene_depth", "DEPTH_INPUT"))
                .addTarget(new VpfxNativeTargetNode("vulkanpostfx:scene_depth", "DEPTH_INPUT"))
                .addTarget(new VpfxNativeTargetNode("minecraft:shadow_depth", "DEPTH_INPUT"))
                .addTarget(new VpfxNativeTargetNode("vulkanpostfx:shadow_depth", "DEPTH_INPUT"));

        for (String targetId : declaredTargets.keySet()) {
            VpfxTargetDefinition targetDefinition = declaredTargets.get(targetId);
            runtimeGraphBuilder.addTarget(new VpfxNativeTargetNode(
                    targetId,
                    targetDefinition != null && targetDefinition.isUseDepth() ? "COLOR_DEPTH" : "COLOR"
            ));
        }

        VpfxNativeGraphPlan.Builder graphPlanBuilder = VpfxNativeGraphPlan.builder()
                .supported(true);

        List<VpfxNativeInputBinding> allInputs = new ArrayList<>();
        List<VpfxNativeOutputBinding> allOutputs = new ArrayList<>();

        boolean writesMain = false;

        for (int i = 0; i < passes.size(); i++) {
            VpfxPassDefinition passDef = passes.get(i);
            String passId = passDef.identityOrIndex(i);

            if (passDef.getVertexShader() == null || passDef.getVertexShader().isBlank()
                    || passDef.getFragmentShader() == null || passDef.getFragmentShader().isBlank()) {
                return unsupported("pass has blank shader reference", passId);
            }

            if (passDef.getInputs().isEmpty()) {
                return unsupported("pass has no inputs", passId);
            }

            VpfxNativePassNode.Builder passNodeBuilder = VpfxNativePassNode.builder()
                    .passId(passId)
                    .passType(VpfxPassType.FULLSCREEN)
                    .vertexShaderRef(passDef.getVertexShader())
                    .fragmentShaderRef(passDef.getFragmentShader());

            List<String> samplerNames = new ArrayList<>();
            for (VpfxPassInput input : passDef.getInputs()) {
                String samplerName = normalizeSamplerName(input.getSamplerName(), samplerNames.size());
                VpfxNativeInputBinding binding;

                if (input.isTextureInput()) {
                    if (input.isUseDepthBuffer()) {
                        return unsupported("texture input does not support use_depth_buffer=true", passId);
                    }

                    String textureName = input.getTexture();
                    if (textureName == null || textureName.isBlank()) {
                        return unsupported("texture input has blank logical name", passId);
                    }
                    if (!vpfxDef.getManifest().getTextures().containsKey(textureName)
                            && !VpfxRuntimeTextureBus.isRuntimeBusTexture(textureName)) {
                        return unsupported("texture input is not declared in pack manifest: " + textureName, passId);
                    }
                    binding = VpfxNativeInputBinding.texture(samplerName, textureName);
                } else {
                    String targetId = input.getTarget();
                    if (targetId == null || targetId.isBlank()) {
                        return unsupported("pass input has no target", passId);
                    }

                    if (isHistoryAlias(targetId)) {
                        if (input.isUseDepthBuffer()) {
                            return unsupported("history input does not support use_depth_buffer=true yet: " + targetId, passId);
                        }

                        String baseTargetId = stripHistoryAlias(targetId);
                        VpfxTargetDefinition definition = declaredTargets.get(baseTargetId);
                        if (definition == null) {
                            return unsupported("history input refers to an undeclared target: " + baseTargetId, passId);
                        }
                        if (!definition.isHistory()) {
                            return unsupported("history input requires target history=true or ping_pong=true: " + baseTargetId, passId);
                        }
                    }

                    if (targetId.contains("shadow") && !isShadowDepthTarget(targetId)) {
                        return unsupported("unknown shadow input target: " + targetId, passId);
                    }

                    if (targetId.contains("depth") && !isSceneDepthTarget(targetId) && !isShadowDepthTarget(targetId)) {
                        return unsupported("unknown depth input target: " + targetId, passId);
                    }

                    if (!readableTargets.contains(targetId)) {
                        return unsupported("input target has not been declared or produced before use: " + targetId, passId);
                    }

                    if (input.isUseDepthBuffer()) {
                        String depthValidationError = validateDepthInputTarget(targetId, declaredTargets);
                        if (depthValidationError != null) {
                            return unsupported(depthValidationError, passId);
                        }
                    }

                    boolean depthInput = input.isUseDepthBuffer() || isSceneDepthTarget(targetId) || isShadowDepthTarget(targetId);
                    binding = VpfxNativeInputBinding.target(samplerName, targetId, depthInput);
                }

                passNodeBuilder.addInput(binding);
                allInputs.add(binding);
                samplerNames.add(binding.glslSamplerName());
            }

            String outputTarget = passDef.getOutput();
            if (outputTarget == null || outputTarget.isBlank()) {
                outputTarget = "minecraft:main";
            }

            if (!"minecraft:main".equals(outputTarget) && !declaredTargets.containsKey(outputTarget)) {
                return unsupported("output target is not minecraft:main and not declared: " + outputTarget, passId);
            }

            VpfxNativeOutputBinding output = new VpfxNativeOutputBinding(outputTarget);
            passNodeBuilder.addOutput(output);
            passNodeBuilder.samplerConvention(String.join(",", samplerNames));
            VpfxNativePassNode passNode = passNodeBuilder.build();
            graphPlanBuilder.addPlannedPass(passNode);
            runtimeGraphBuilder.addPass(passNode);
            allOutputs.add(output);

            readableTargets.add(outputTarget);
            if ("minecraft:main".equals(outputTarget)) {
                writesMain = true;
            }
        }

        if (!writesMain) {
            return unsupported("graph does not write to minecraft:main");
        }

        VpfxNativeRuntimeGraph runtimeGraph = runtimeGraphBuilder.build();
        VpfxNativeGraphPlan graphPlan = graphPlanBuilder.build();
        VpfxNativePassNode firstPass = graphPlan.plannedPasses().get(0);

        VpfxNativePipelineKey requiredPipelineKey = new VpfxNativePipelineKey(
                vpfxDef.getManifest().getPackId(),
                firstPass.passId(),
                firstPass.passType(),
                firstPass.vertexShaderRef(),
                firstPass.fragmentShaderRef(),
                "RGBA8_UNORM",
                "",
                "",
                firstPass.samplerConvention()
        );

        return VpfxNativeGraphPlanResult.builder()
                .planAttempted(true)
                .planSupported(true)
                .passCount(graphPlan.plannedPasses().size())
                .targetCount(runtimeGraph.targetCount())
                .firstPassName(firstPass.passId())
                .inputBindings(allInputs.stream().map(VpfxNativeInputBinding::toString).collect(Collectors.joining(", ")))
                .outputBindings(allOutputs.stream().map(VpfxNativeOutputBinding::toString).collect(Collectors.joining(", ")))
                .samplerConvention(graphPlan.plannedPasses().stream()
                        .map(VpfxNativePassNode::samplerConvention)
                        .collect(Collectors.joining(" | ")))
                .unsupportedReason("none")
                .failureStage(VpfxNativeFailureStage.NONE)
                .fallbackExpected(false)
                .requiredPipelineKey(requiredPipelineKey)
                .runtimeGraph(runtimeGraph)
                .graphPlan(graphPlan)
                .build();
    }

    public static boolean isHistoryAlias(String targetId) {
        return targetId != null && targetId.startsWith("history:");
    }

    public static String stripHistoryAlias(String targetId) {
        if (!isHistoryAlias(targetId)) {
            return targetId;
        }
        return targetId.substring("history:".length());
    }

    public static String historyAlias(String targetId) {
        return "history:" + targetId;
    }

    private static String validateDepthInputTarget(
            String targetId,
            Map<String, VpfxTargetDefinition> declaredTargets
    ) {
        if ("minecraft:main".equals(targetId)
                || "minecraft:scene_color".equals(targetId)
                || isSceneDepthTarget(targetId)
                || isShadowDepthTarget(targetId)) {
            return null;
        }

        VpfxTargetDefinition targetDefinition = declaredTargets.get(targetId);
        if (targetDefinition == null) {
            return "use_depth_buffer=true target is not declared: " + targetId;
        }
        if (!targetDefinition.isUseDepth()) {
            return "use_depth_buffer=true but target is not declared with use_depth=true: " + targetId;
        }
        return null;
    }

    private static boolean isSceneDepthTarget(String targetId) {
        return "vulkanpostfx:scene_depth".equals(targetId)
                || "minecraft:scene_depth".equals(targetId);
    }

    private static boolean isShadowDepthTarget(String targetId) {
        return "vulkanpostfx:shadow_depth".equals(targetId)
                || "minecraft:shadow_depth".equals(targetId);
    }

    private static String normalizeSamplerName(String samplerName, int index) {
        if (samplerName != null && !samplerName.isBlank()) {
            return samplerName;
        }
        return index == 0 ? "In" : "In" + index;
    }

    private static VpfxNativeGraphPlanResult unsupported(String reason) {
        return unsupported(reason, null);
    }

    private static VpfxNativeGraphPlanResult unsupported(String reason, String firstPassName) {
        return VpfxNativeGraphPlanResult.builder()
                .planAttempted(true)
                .planSupported(false)
                .passCount(0)
                .targetCount(0)
                .firstPassName(firstPassName != null ? firstPassName : "")
                .inputBindings("")
                .outputBindings("")
                .samplerConvention("none")
                .unsupportedReason(reason)
                .failureStage(VpfxNativeFailureStage.USER_PIPELINE_RESOLVE)
                .fallbackExpected(true)
                .build();
    }
}
