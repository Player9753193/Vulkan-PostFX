package com.ionhex975.vulkanpostfx.client.postfx;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.depth.VpfxSceneDepthProvider;
import com.ionhex975.vulkanpostfx.client.state.PostFxRuntimeState;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;

/**
 * 为 VPFX 外部 PostChain 提供稳定的世界场景 depth。
 *
 * 方向上不再依赖“post slot 里 main target 的 depth 恰好还可用”，
 * 而是在世界主渲染结束后主动拷贝一份 depth，作为显式 external target 对外暴露。
 */
public final class SceneDepthCaptureTargets {
    private static final SceneDepthCaptureTargets INSTANCE = new SceneDepthCaptureTargets();

    private RenderTarget sceneDepthTarget;
    private int width;
    private int height;
    private boolean ready;
    private boolean firstAllocatedLogged;
    private boolean firstCapturedLogged;

    private SceneDepthCaptureTargets() {
    }

    public static SceneDepthCaptureTargets get() {
        return INSTANCE;
    }

    public void captureFromMainTarget(Minecraft minecraft) {
        RenderSystem.assertOnRenderThread();

        if (minecraft == null) {
            VpfxSceneDepthProvider.markUnavailable("minecraft client is null");
            return;
        }
        if (minecraft.gameRenderer == null) {
            VpfxSceneDepthProvider.markUnavailable("gameRenderer is null");
            return;
        }

        RenderTarget mainTarget = minecraft.gameRenderer.mainRenderTarget();
        if (mainTarget == null) {
            VpfxSceneDepthProvider.markUnavailable("main render target is null");
            return;
        }

        ensureAllocated(mainTarget.width, mainTarget.height);

        if (!this.ready || this.sceneDepthTarget == null) {
            VpfxSceneDepthProvider.markUnavailable("scene depth target is not allocated");
            return;
        }

        if (mainTarget.getDepthTexture() == null) {
            VpfxSceneDepthProvider.markUnavailable("main render target has no depth texture");
            return;
        }
        if (this.sceneDepthTarget.getDepthTexture() == null) {
            VpfxSceneDepthProvider.markUnavailable("VPFX scene depth target has no depth texture");
            return;
        }

        try {
            this.sceneDepthTarget.copyDepthFrom(mainTarget);
            VpfxSceneDepthProvider.markCaptured(mainTarget.width, mainTarget.height, PostFxRuntimeState.currentFrameEpoch());

            if (!this.firstCapturedLogged) {
                this.firstCapturedLogged = true;
                VulkanPostFX.LOGGER.info(
                        "[{}] Scene depth capture completed: {}x{}",
                        VulkanPostFX.MOD_ID,
                        mainTarget.width,
                        mainTarget.height
                );
            }
        } catch (Throwable t) {
            VpfxSceneDepthProvider.markUnavailable("scene depth copy failed: " + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
            VulkanPostFX.LOGGER.error(
                    "[{}] Scene depth capture failed",
                    VulkanPostFX.MOD_ID,
                    t
            );
        }
    }

    public void ensureAllocated(int width, int height) {
        RenderSystem.assertOnRenderThread();

        if (this.ready
                && this.sceneDepthTarget != null
                && this.width == width
                && this.height == height) {
            return;
        }

        release();

        try {
            this.sceneDepthTarget = new TextureTarget("VulkanPostFX Scene Depth", width, height, true, GpuFormat.RGBA8_UNORM);
            this.width = width;
            this.height = height;
            this.ready = true;
            VpfxSceneDepthProvider.markAllocated(width, height);

            if (!this.firstAllocatedLogged) {
                this.firstAllocatedLogged = true;
                VulkanPostFX.LOGGER.info(
                        "[{}] Scene depth target allocated: {}x{}, targetClass={}",
                        VulkanPostFX.MOD_ID,
                        width,
                        height,
                        this.sceneDepthTarget.getClass().getName()
                );
            }
        } catch (Throwable t) {
            this.sceneDepthTarget = null;
            this.width = 0;
            this.height = 0;
            this.ready = false;
            VpfxSceneDepthProvider.markUnavailable("failed to allocate scene depth target: " + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));

            VulkanPostFX.LOGGER.error(
                    "[{}] Failed to allocate scene depth target",
                    VulkanPostFX.MOD_ID,
                    t
            );
        }
    }

    public void release() {
        RenderSystem.assertOnRenderThread();

        if (this.sceneDepthTarget != null) {
            this.sceneDepthTarget.destroyBuffers();
        }

        this.sceneDepthTarget = null;
        this.width = 0;
        this.height = 0;
        this.ready = false;
        VpfxSceneDepthProvider.markReleased("scene depth target released");
    }

    public boolean isReady() {
        return this.ready && this.sceneDepthTarget != null;
    }

    public RenderTarget getSceneDepthTarget() {
        return this.sceneDepthTarget;
    }
}
