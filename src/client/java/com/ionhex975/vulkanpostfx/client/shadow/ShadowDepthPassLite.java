package com.ionhex975.vulkanpostfx.client.shadow;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.diagnostics.VpfxDiagnosticsConfig;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxCapabilityResolver;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxRuntimeCapabilities;
import com.ionhex975.vulkanpostfx.client.state.PostFxRuntimeState;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Vector4f;

public final class ShadowDepthPassLite {

    private static Boolean lastLoggedTerrainRendered;
    private static Integer lastLoggedEntitySubmitted;
    private static Boolean lastLoggedCastersRendered;
    private static int stableLogCounter;

    private ShadowDepthPassLite() {
    }

    public static void execute(Minecraft minecraft, LevelRenderer levelRenderer) {
        RenderSystem.assertOnRenderThread();

        VpfxRuntimeCapabilities caps = new VpfxCapabilityResolver().resolve();
        if (!caps.isShadowDepth()) {
            VpfxShadowDepthProvider.markUnavailable("shadow_depth capability is disabled");
            return;
        }

        ShadowFrameState state = ShadowFrameState.get();
        ShadowRenderTargetsLite targets = ShadowRenderTargetsLite.get();

        if (!state.isValid() || !state.isShadowPassEnabled() || !state.isShadowTargetReady() || !targets.isReady()) {
            VpfxShadowDepthProvider.markUnavailable("shadow pass prerequisites are not ready: valid="
                    + state.isValid()
                    + ", enabled=" + state.isShadowPassEnabled()
                    + ", targetReady=" + state.isShadowTargetReady()
                    + ", targetsReady=" + targets.isReady());
            return;
        }

        if (!state.consumeShadowRenderRequest()) {
            VpfxShadowDepthProvider.markPrepared(state);
            return;
        }

        RenderTarget target = targets.getShadowDepthTarget();
        if (target == null) {
            VpfxShadowDepthProvider.markUnavailable("shadow render target is null");
            return;
        }

        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

            if (target.getColorTexture() != null && target.getDepthTexture() != null) {
                encoder.clearColorAndDepthTextures(
                        target.getColorTexture(),
                        new Vector4f(0.0f, 0.0f, 0.0f, 0.0f),
                        target.getDepthTexture(),
                        0.0
                );
            } else if (target.getDepthTexture() != null) {
                encoder.clearDepthTexture(target.getDepthTexture(), 0.0);
            } else {
                throw new IllegalStateException("Shadow target has no depth texture");
            }

            boolean terrainRendered = ShadowTerrainPassLite.execute(
                    minecraft,
                    levelRenderer,
                    state,
                    target
            );

            int entitySubmitted = ShadowEntityPassLite.execute(
                    minecraft,
                    levelRenderer,
                    state,
                    target
            );

            boolean castersRendered = terrainRendered || entitySubmitted > 0;

            state.markShadowPassExecuted(castersRendered);
            VpfxShadowDepthProvider.markPassExecuted(state, castersRendered, PostFxRuntimeState.currentFrameEpoch());

            boolean stateChanged =
                    lastLoggedTerrainRendered == null
                            || lastLoggedTerrainRendered != terrainRendered
                            || lastLoggedEntitySubmitted == null
                            || lastLoggedEntitySubmitted != entitySubmitted
                            || lastLoggedCastersRendered == null
                            || lastLoggedCastersRendered != castersRendered;

            if (stateChanged || (++stableLogCounter >= stableLogIntervalFrames())) {
                stableLogCounter = 0;
                lastLoggedTerrainRendered = terrainRendered;
                lastLoggedEntitySubmitted = entitySubmitted;
                lastLoggedCastersRendered = castersRendered;

                VulkanPostFX.LOGGER.info(
                        "[{}] Shadow depth pass summary: shadowMapSize={}, terrainShadowDistance={}, entityShadowDistance={}, terrainRendered={}, entitySubmitted={}, castersRendered={}, reason={}",
                        VulkanPostFX.MOD_ID,
                        state.getShadowMapSize(),
                        state.getTerrainShadowDistance(),
                        state.getEntityShadowDistance(),
                        terrainRendered,
                        entitySubmitted,
                        castersRendered,
                        stateChanged ? "state-change" : "interval"
                );
            }
        } catch (Throwable t) {
            VpfxShadowDepthProvider.markUnavailable("shadow depth pass failed: " + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
            VulkanPostFX.LOGGER.error(
                    "[{}] Shadow depth pass execution failed",
                    VulkanPostFX.MOD_ID,
                    t
            );
        }
    }

    private static int stableLogIntervalFrames() {
        return VpfxDiagnosticsConfig.shadowPassLogIntervalFrames();
    }
}
