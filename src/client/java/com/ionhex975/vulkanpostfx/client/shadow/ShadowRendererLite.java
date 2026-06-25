package com.ionhex975.vulkanpostfx.client.shadow;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxCapabilityResolver;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxRuntimeCapabilities;
import com.ionhex975.vulkanpostfx.client.state.PostFxRuntimeState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/**
 * Shadow renderer：独立 shadow map 资源 + 生命周期调度。
 */
public final class ShadowRendererLite {
    private static final int DEFAULT_SHADOW_MAP_SIZE = 8192;
    private static final float DEFAULT_TERRAIN_SHADOW_DISTANCE = 160.0F;
    private static final float DEFAULT_ENTITY_SHADOW_DISTANCE_MUL = 0.4F;

    private static boolean firstPreparedLogged;

    private ShadowRendererLite() {
    }

    public static void prepareFrame(
            Minecraft minecraft,
            CameraRenderState cameraState
    ) {
        VpfxRuntimeCapabilities caps = new VpfxCapabilityResolver().resolve();
        ShadowFrameState state = ShadowFrameState.get();

        if (!caps.isShadowDepth() || !PostFxRuntimeState.isDebugEffectEnabled()) {
            state.setShadowPassEnabled(false);
            state.setShadowTargetState(false, 0);
            VpfxShadowDepthProvider.markUnavailable(!caps.isShadowDepth()
                    ? "shadow_depth capability is disabled"
                    : "VPFX effect is disabled");
            return;
        }

        if (minecraft.level == null || cameraState == null || !cameraState.initialized) {
            state.setShadowPassEnabled(false);
            state.setShadowTargetState(false, 0);
            VpfxShadowDepthProvider.markUnavailable("client level or camera state is unavailable");
            return;
        }

        ShadowRenderTargetsLite targets = ShadowRenderTargetsLite.get();
        targets.ensureAllocated(DEFAULT_SHADOW_MAP_SIZE);

        float terrainDistance = DEFAULT_TERRAIN_SHADOW_DISTANCE;
        float entityDistance = terrainDistance * DEFAULT_ENTITY_SHADOW_DISTANCE_MUL;

        state.setShadowPassEnabled(state.hasRenderableShadowLight());
        state.setShadowDistances(terrainDistance, entityDistance);
        state.setShadowCasterControls(true, true);
        state.setShadowTargetState(targets.isReady(), targets.getShadowMapSize());
        VpfxShadowDepthProvider.markPrepared(state);

        if (!state.isShadowPassEnabled()) {
            VpfxShadowDepthProvider.markUnavailable("no renderable sun/moon shadow light this frame: primaryLight="
                    + state.getPrimaryLight()
                    + ", intensity=" + state.getShadowLightIntensity());
        }

        if (targets.isReady() && state.isShadowPassEnabled()) {
            state.requestShadowRender();
        }

        if (!firstPreparedLogged) {
            firstPreparedLogged = true;
            VulkanPostFX.LOGGER.info(
                    "[{}] Shadow renderer prepared: cameraPos={}, shadowMapSize={}, targetReady={}, terrainShadowDistance={}, entityShadowDistance={}, shadowEntities={}, shadowPlayer={}",
                    VulkanPostFX.MOD_ID,
                    cameraState.pos,
                    targets.getShadowMapSize(),
                    targets.isReady(),
                    terrainDistance,
                    entityDistance,
                    true,
                    true
            );
        }
    }

    public static void executeShadowPassLite(
            Minecraft minecraft,
            LevelRenderer levelRenderer
    ) {
        VpfxRuntimeCapabilities caps = new VpfxCapabilityResolver().resolve();
        if (!caps.isShadowDepth() || !PostFxRuntimeState.isDebugEffectEnabled()) {
            return;
        }

        ShadowDepthPassLite.execute(minecraft, levelRenderer);
    }
}
