package com.ionhex975.vulkanpostfx.client.postfx;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
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
            return;
        }

        RenderTarget mainTarget = minecraft.gameRenderer.mainRenderTarget();
        if (mainTarget == null) {
            return;
        }

        ensureAllocated(mainTarget.width, mainTarget.height);

        if (!this.ready || this.sceneDepthTarget == null) {
            return;
        }

        if (mainTarget.getDepthTexture() == null || this.sceneDepthTarget.getDepthTexture() == null) {
            return;
        }

        try {
            this.sceneDepthTarget.copyDepthFrom(mainTarget);

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
    }

    public boolean isReady() {
        return this.ready && this.sceneDepthTarget != null;
    }

    public RenderTarget getSceneDepthTarget() {
        return this.sceneDepthTarget;
    }
}
