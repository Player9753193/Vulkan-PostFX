package com.ionhex975.vulkanpostfx.client.postfx;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.depth.VpfxSceneDepthProvider;
import com.ionhex975.vulkanpostfx.client.depth.VpfxSceneDepthState;
import com.ionhex975.vulkanpostfx.client.diagnostics.VpfxFailureDiagnostics;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxCapabilityResolver;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxRuntimeCapabilities;
import com.ionhex975.vulkanpostfx.client.shadow.ShadowRenderTargetsLite;
import com.ionhex975.vulkanpostfx.client.shadow.VpfxShadowDepthProvider;
import com.ionhex975.vulkanpostfx.client.shadow.VpfxShadowDepthState;
import com.ionhex975.vulkanpostfx.client.state.PostFxRuntimeState;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;

public final class PostFxExternalTargetRunner {
    private static boolean firstSceneColorBoundLogged;
    private static boolean firstSceneDepthTargetBoundLogged;
    private static boolean firstSceneDepthFallbackLogged;
    private static boolean firstShadowTargetBoundLogged;
    private static boolean firstShadowFallbackLogged;

    private PostFxExternalTargetRunner() {
    }

    public static void process(
            PostChain chain,
            RenderTarget mainTarget,
            GraphicsResourceAllocator resourceAllocator
    ) {
        FrameGraphBuilder frame = new FrameGraphBuilder();
        MutableTargetBundle bundle = new MutableTargetBundle();

        bundle.put(
                PostChain.MAIN_TARGET_ID,
                frame.importExternal("main", mainTarget)
        );

        bundle.put(
                PostFxExternalTargetIds.SCENE_COLOR,
                frame.importExternal("scene_color", mainTarget)
        );

        if (!firstSceneColorBoundLogged) {
            firstSceneColorBoundLogged = true;
            VulkanPostFX.LOGGER.info(
                    "[{}] Bound minecraft:scene_color to main target in PostChain.process bundle",
                    VulkanPostFX.MOD_ID
            );
        }

        VpfxRuntimeCapabilities caps =
                new VpfxCapabilityResolver().resolve();

        if (caps.isSceneDepth()) {
            SceneDepthCaptureTargets sceneDepthTargets = SceneDepthCaptureTargets.get();
            VpfxSceneDepthState sceneDepthState = VpfxSceneDepthProvider.currentState();
            RenderTarget sceneDepthTarget = sceneDepthTargets.getSceneDepthTarget();

            if (sceneDepthTargets.isReady() && sceneDepthState.available() && sceneDepthTarget != null) {
                bundle.put(
                        PostFxExternalTargetIds.SCENE_DEPTH,
                        frame.importExternal("scene_depth", sceneDepthTarget)
                );

                if (!firstSceneDepthTargetBoundLogged) {
                    firstSceneDepthTargetBoundLogged = true;
                    VulkanPostFX.LOGGER.info(
                            "[{}] Added runtime scene depth target to PostChain.process bundle: id={}, size={}x{}",
                            VulkanPostFX.MOD_ID,
                            PostFxExternalTargetIds.SCENE_DEPTH,
                            sceneDepthTarget.width,
                            sceneDepthTarget.height
                    );
                }
            } else {
                bundle.put(
                        PostFxExternalTargetIds.SCENE_DEPTH,
                        frame.importExternal("scene_depth_fallback_main", mainTarget)
                );

                if (!firstSceneDepthFallbackLogged) {
                    firstSceneDepthFallbackLogged = true;
                    VulkanPostFX.LOGGER.warn(
                            "[{}] Scene depth target is not ready during PostChain.process; falling back to main target depth for external id {}, stateAvailable={}, targetReady={}, reason={}",
                            VulkanPostFX.MOD_ID,
                            PostFxExternalTargetIds.SCENE_DEPTH,
                            sceneDepthState.available(),
                            sceneDepthState.targetReady(),
                            sceneDepthState.reason()
                    );
                }
            }
        }

        if (caps.isShadowDepth()) {
            ShadowRenderTargetsLite shadowTargets = ShadowRenderTargetsLite.get();
            VpfxShadowDepthState shadowDepthState = VpfxShadowDepthProvider.currentState();
            RenderTarget shadowDepthTarget = shadowTargets.getShadowDepthTarget();

            if (shadowTargets.isReady() && shadowDepthState.available() && shadowDepthTarget != null) {
                bundle.put(
                        PostFxExternalTargetIds.SHADOW_DEPTH,
                        frame.importExternal("shadow_depth", shadowDepthTarget)
                );

                if (!firstShadowTargetBoundLogged) {
                    firstShadowTargetBoundLogged = true;
                    VulkanPostFX.LOGGER.info(
                            "[{}] Added runtime shadow target to PostChain.process bundle: id={}, size={}x{}",
                            VulkanPostFX.MOD_ID,
                            PostFxExternalTargetIds.SHADOW_DEPTH,
                            shadowDepthTarget.width,
                            shadowDepthTarget.height
                    );
                }
            } else {
                bundle.put(
                        PostFxExternalTargetIds.SHADOW_DEPTH,
                        frame.importExternal("shadow_depth_fallback_main", mainTarget)
                );

                if (!firstShadowFallbackLogged) {
                    firstShadowFallbackLogged = true;
                    VulkanPostFX.LOGGER.warn(
                            "[{}] Shadow target is not ready during PostChain.process; falling back to main target for external id {}, stateAvailable={}, targetReady={}, reason={}",
                            VulkanPostFX.MOD_ID,
                            PostFxExternalTargetIds.SHADOW_DEPTH,
                            shadowDepthState.available(),
                            shadowDepthState.targetReady(),
                            shadowDepthState.reason()
                    );
                }
            }
        }

        try {
            chain.addToFrame(frame, mainTarget.width, mainTarget.height, bundle);
            frame.execute(resourceAllocator);
        } catch (Exception e) {
            Identifier failedId = PostFxRuntimeState.getActiveExternalPostEffectId();
            PostFxRuntimeState.setFailedExternalPostEffectId(failedId);
            VpfxFailureDiagnostics.write("addToFrame/execute", e);

            VulkanPostFX.LOGGER.error(
                    "[{}] VPFX external PostChain execution failed (addToFrame or frame.execute). "
                            + "Pack has been marked as unavailable. externalPostEffectId={}",
                    VulkanPostFX.MOD_ID,
                    failedId,
                    e
            );

            VulkanPostFX.LOGGER.info(
                    "[{}] Falling back to vanilla rendering (external pack disabled). "
                            + "Press F8 twice to re-enable VPFX after fixing the pack.",
                    VulkanPostFX.MOD_ID
            );
        }
    }
}
