package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.framegraph;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.depth.VpfxSceneDepthProvider;
import com.ionhex975.vulkanpostfx.client.depth.VpfxSceneDepthState;
import com.ionhex975.vulkanpostfx.client.pack.ActiveShaderPackManager;
import com.ionhex975.vulkanpostfx.client.pack.ShaderPackContainer;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxGraphDefinition;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.VpfxNativeExecutionResult;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.VpfxNativeFailureStage;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.graph.VpfxNativeGraphPlanResult;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.graph.VpfxNativeGraphPlanner;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.graph.VpfxNativeInputBinding;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.graph.VpfxNativePassNode;
import com.ionhex975.vulkanpostfx.client.runtime.zip.RuntimeZipPackState;
import com.ionhex975.vulkanpostfx.client.shadow.VpfxShadowDepthProvider;
import com.ionhex975.vulkanpostfx.client.shadow.VpfxShadowDepthState;
import com.ionhex975.vulkanpostfx.client.shader.uniform.VpfxBuiltinUniformBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;

import java.util.Optional;
import java.util.OptionalDouble;

public final class VpfxNativeFrameGraphExecutor {
    private static String lastLoggedSuccessSignature = "";

    private VpfxNativeFrameGraphExecutor() {
    }

    public static VpfxNativeExecutionResult execute(Minecraft minecraft) {
        VpfxNativeGraphPlanResult plan = VpfxNativeGraphPlanner.plan();
        if (!plan.planSupported() || plan.runtimeGraph() == null || plan.graphPlan() == null) {
            return VpfxNativeExecutionResult.builder()
                    .attempted(true)
                    .copySucceeded(false)
                    .fallbackReason(plan.unsupportedReason())
                    .failureStage(plan.failureStage())
                    .failureMessage(plan.unsupportedReason())
                    .postChainFallbackExpected(false)
                    .build();
        }

        if (minecraft == null || minecraft.gameRenderer == null) {
            return failed("Minecraft or GameRenderer unavailable", VpfxNativeFailureStage.MAIN_TARGET_UNAVAILABLE);
        }

        if (!RenderSystem.isOnRenderThread()) {
            return failed("native framegraph must execute on render thread", VpfxNativeFailureStage.NOT_RENDER_THREAD);
        }

        RenderTarget mainTarget = minecraft.gameRenderer.mainRenderTarget();
        if (mainTarget == null || mainTarget.width <= 0 || mainTarget.height <= 0) {
            return failed("mainRenderTarget unavailable or invalid", VpfxNativeFailureStage.MAIN_TARGET_UNAVAILABLE);
        }

        ShaderPackContainer activePack = ActiveShaderPackManager.getActivePack();
        if (activePack == null || !activePack.isVpfxNativePack() || activePack.vpfxDefinition() == null) {
            return failed("active VPFX native pack unavailable", VpfxNativeFailureStage.USER_PIPELINE_RESOLVE);
        }

        VpfxGraphDefinition sourceGraph = activePack.vpfxDefinition().getGraph();
        String runtimeNamespace = RuntimeZipPackState.getRuntimeNamespace();
        if (runtimeNamespace == null || runtimeNamespace.isBlank()) {
            return failed("runtime namespace unavailable", VpfxNativeFailureStage.USER_PIPELINE_RESOLVE);
        }

        VpfxNativeGraphPlanResult planned = plan;
        if (planned.inputBindings().contains("scene_depth")) {
            VpfxSceneDepthState depthState = VpfxSceneDepthProvider.currentState();
            if (!depthState.available()) {
                return failed(
                        "scene_depth input requested but scene depth is not available: " + depthState.reason(),
                        VpfxNativeFailureStage.USER_PIPELINE_RESOLVE
                );
            }
        }

        if (planned.inputBindings().contains("shadow_depth")) {
            VpfxShadowDepthState shadowDepthState = VpfxShadowDepthProvider.currentState();
            if (!shadowDepthState.available()) {
                return failed(
                        "shadow_depth input requested but shadow depth is not available: " + shadowDepthState.reason(),
                        VpfxNativeFailureStage.USER_PIPELINE_RESOLVE
                );
            }
        }

        int executedPasses = 0;

        try (VpfxNativeTargetPool targetPool = VpfxNativeTargetPool.create(mainTarget, sourceGraph.getTargets())) {
            GpuBufferSlice builtins = VpfxBuiltinUniformBuffer.writeAndGetNativeSlice();

            for (VpfxNativePassNode pass : plan.graphPlan().plannedPasses()) {
                VpfxNativeFrameTarget outputTarget = resolveOutputTarget(targetPool, pass);
                RenderPipeline pipeline = VpfxNativeFrameGraphPipelineCache.getOrCreate(
                        runtimeNamespace,
                        activePack.vpfxDefinition().getManifest().getPackId(),
                        pass
                );

                try (RenderPass renderPass = RenderSystem.getDevice()
                        .createCommandEncoder()
                        .createRenderPass(
                                () -> "VPFX NR-2.4 framegraph pass: " + pass.passId(),
                                outputTarget.colorView(),
                                Optional.empty(),
                                outputTarget.depthView(),
                                OptionalDouble.empty()
                        )) {
                    RenderSystem.bindDefaultUniforms(renderPass);
                    renderPass.setUniform(VpfxBuiltinUniformBuffer.BLOCK_NAME, builtins);
                    renderPass.setPipeline(pipeline);

                    for (VpfxNativeInputBinding input : pass.inputs()) {
                        if (input.isTextureInput()) {
                            VpfxNativeRuntimeTextureBindingResult textureBinding = VpfxNativeRuntimeTextureResolver.resolve(
                                    minecraft,
                                    runtimeNamespace,
                                    input.textureName()
                            );
                            if (!textureBinding.available()) {
                                return expectedPostChainFallback(
                                        "native runtime texture input unavailable for pass '"
                                                + pass.passId()
                                                + "': "
                                                + textureBinding.summary(),
                                        textureBinding.transientFailure()
                                                ? VpfxNativeFailureStage.USER_PIPELINE_RESOLVE
                                                : VpfxNativeFailureStage.NATIVE_DRAW_FAILED
                                );
                            }

                            renderPass.bindTexture(
                                    input.glslSamplerName(),
                                    textureBinding.textureView(),
                                    VpfxNativeSamplerResolver.forTexture(textureBinding.descriptor())
                            );
                            continue;
                        }

                        VpfxNativeFrameTarget inputTarget = targetPool.resolveInput(input.targetId(), input.isDepthInput());
                        GpuTextureView sampledView = inputTarget == null
                                ? null
                                : input.isDepthInput() ? inputTarget.depthView() : inputTarget.sampledView();
                        if (inputTarget == null || sampledView == null) {
                            throw new IllegalStateException(
                                    "input target unavailable for pass '" + pass.passId() + "': " + input
                            );
                        }
                        renderPass.bindTexture(
                                input.glslSamplerName(),
                                sampledView,
                                VpfxNativeSamplerResolver.forTarget(input.isDepthInput()
                                        ? com.mojang.blaze3d.textures.FilterMode.NEAREST
                                        : com.mojang.blaze3d.textures.FilterMode.LINEAR)
                        );
                    }

                    renderPass.draw(3, 1, 0, 0);
                    executedPasses++;
                }
            }

            targetPool.commitFrame();
        } catch (Throwable t) {
            return VpfxNativeExecutionResult.builder()
                    .attempted(true)
                    .copySucceeded(true)
                    .userPipelineAttempted(true)
                    .userPipelineAvailable(false)
                    .userPipelineFallbackUsed(false)
                    .actualPipeline("native framegraph")
                    .fallbackReason("native framegraph failed: " + t.getMessage())
                    .failureStage(VpfxNativeFailureStage.NATIVE_DRAW_FAILED)
                    .failureMessage(t.getMessage())
                    .postChainFallbackExpected(false)
                    .build();
        }

        String inputBindingSummary = plan.inputBindings();
        String successSignature = activePack.manifest().id() + ":" + executedPasses + ":" + plan.targetCount();
        if (!successSignature.equals(lastLoggedSuccessSignature)) {
            lastLoggedSuccessSignature = successSignature;
            VulkanPostFX.LOGGER.info(
                    "[{}] VPFX NR-2.4: native framegraph executed successfully. passes={}, targets={}, pack={}, sceneDepthInput={}, shadowDepthInput={}, customTextureInput={}, historyInput={}",
                    VulkanPostFX.MOD_ID,
                    executedPasses,
                    plan.targetCount(),
                    activePack.manifest().id(),
                    inputBindingSummary.contains("scene_depth"),
                    inputBindingSummary.contains("shadow_depth"),
                    inputBindingSummary.contains("texture:"),
                    inputBindingSummary.contains("history:")
            );
        }

        return VpfxNativeExecutionResult.builder()
                .attempted(true)
                .copySucceeded(true)
                .pipelineCreated(false)
                .renderPassCreated(true)
                .drawExecuted(executedPasses > 0)
                .nativeSucceeded(executedPasses == plan.passCount())
                .fallbackStillActive(false)
                .diagnosticMode(false)
                .userShaderNativeExecution(true)
                .builtinPassthroughOnly(false)
                .userPipelineAttempted(true)
                .userPipelineAvailable(true)
                .actualPipeline("native framegraph passes=" + executedPasses)
                .pipelineFallbackReason("none")
                .failureStage(VpfxNativeFailureStage.NONE)
                .failureMessage("none")
                .postChainFallbackExpected(false)
                .build();
    }


    private static VpfxNativeFrameTarget resolveOutputTarget(VpfxNativeTargetPool targetPool, VpfxNativePassNode pass) {
        if (pass.outputs().isEmpty()) {
            throw new IllegalStateException("pass has no output: " + pass.passId());
        }

        String targetId = pass.outputs().get(0).targetId();
        VpfxNativeFrameTarget target = targetPool.resolveOutput(targetId);
        if (target == null || target.colorView() == null) {
            throw new IllegalStateException("output target unavailable for pass '" + pass.passId() + "': " + targetId);
        }
        return target;
    }

    private static VpfxNativeExecutionResult expectedPostChainFallback(String message, VpfxNativeFailureStage stage) {
        return VpfxNativeExecutionResult.builder()
                .attempted(true)
                .copySucceeded(true)
                .userPipelineAttempted(true)
                .userPipelineAvailable(false)
                .actualPipeline("native framegraph")
                .fallbackReason(message)
                .pipelineFallbackReason(message)
                .failureStage(stage)
                .failureMessage(message)
                .postChainFallbackExpected(true)
                .build();
    }

    private static VpfxNativeExecutionResult failed(String message, VpfxNativeFailureStage stage) {
        return VpfxNativeExecutionResult.builder()
                .attempted(true)
                .fallbackReason(message)
                .failureStage(stage)
                .failureMessage(message)
                .postChainFallbackExpected(false)
                .build();
    }
}
