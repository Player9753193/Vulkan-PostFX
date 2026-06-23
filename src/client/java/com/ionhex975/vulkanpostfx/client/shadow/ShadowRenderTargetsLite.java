package com.ionhex975.vulkanpostfx.client.shadow;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;

/**
 * Shadow depth target 管理器。
 *
 * 第二批整理：
 * - 正式按“固定 shadow resolution”工作
 * - 不再服务于 main depth mirror
 * - 仍然先保持单个正方形 shadow map
 */
public final class ShadowRenderTargetsLite {
    private static final ShadowRenderTargetsLite INSTANCE = new ShadowRenderTargetsLite();
    private static final int MIN_FALLBACK_SIZE = 1024;

    private RenderTarget shadowDepthTarget;
    private int requestedShadowMapSize;
    private int shadowMapSize;
    private boolean ready;
    private boolean firstAllocatedLogged;

    private ShadowRenderTargetsLite() {
    }

    public static ShadowRenderTargetsLite get() {
        return INSTANCE;
    }

    public void ensureAllocated(int size) {
        RenderSystem.assertOnRenderThread();

        if (this.ready && this.shadowDepthTarget != null && this.requestedShadowMapSize == size) {
            return;
        }

        release();

        int attemptedSize = size;
        while (attemptedSize >= MIN_FALLBACK_SIZE) {
            try {
                this.shadowDepthTarget = new TextureTarget("VulkanPostFX Shadow Depth", attemptedSize, attemptedSize, true, GpuFormat.RGBA8_UNORM);
                this.requestedShadowMapSize = size;
                this.shadowMapSize = attemptedSize;
                this.ready = true;

                if (!firstAllocatedLogged) {
                    firstAllocatedLogged = true;
                    VulkanPostFX.LOGGER.info(
                            "[{}] Shadow depth target allocated: {}x{}, requested={}x{}, targetClass={}",
                            VulkanPostFX.MOD_ID,
                            attemptedSize,
                            attemptedSize,
                            size,
                            size,
                            this.shadowDepthTarget.getClass().getName()
                    );
                } else if (attemptedSize != size) {
                    VulkanPostFX.LOGGER.warn(
                            "[{}] Shadow depth target fell back to {}x{} after failing requested {}x{}",
                            VulkanPostFX.MOD_ID,
                            attemptedSize,
                            attemptedSize,
                            size,
                            size
                    );
                }

                return;
            } catch (Throwable t) {
                this.shadowDepthTarget = null;
                this.requestedShadowMapSize = 0;
                this.shadowMapSize = 0;
                this.ready = false;

                if (attemptedSize <= MIN_FALLBACK_SIZE) {
                    VulkanPostFX.LOGGER.error(
                            "[{}] Failed to allocate shadow depth target at minimum fallback size {}x{}",
                            VulkanPostFX.MOD_ID,
                            attemptedSize,
                            attemptedSize,
                            t
                    );
                    return;
                }

                VulkanPostFX.LOGGER.warn(
                        "[{}] Failed to allocate shadow depth target at {}x{}; trying lower resolution",
                        VulkanPostFX.MOD_ID,
                        attemptedSize,
                        attemptedSize,
                        t
                );
                attemptedSize /= 2;
            }
        }
    }

    public void release() {
        RenderSystem.assertOnRenderThread();

        if (this.shadowDepthTarget != null) {
            this.shadowDepthTarget.destroyBuffers();
        }

        this.shadowDepthTarget = null;
        this.requestedShadowMapSize = 0;
        this.shadowMapSize = 0;
        this.ready = false;
    }

    public boolean isReady() {
        return this.ready && this.shadowDepthTarget != null;
    }

    public int getShadowMapSize() {
        return this.shadowMapSize;
    }

    public RenderTarget getShadowDepthTarget() {
        return this.shadowDepthTarget;
    }
}
