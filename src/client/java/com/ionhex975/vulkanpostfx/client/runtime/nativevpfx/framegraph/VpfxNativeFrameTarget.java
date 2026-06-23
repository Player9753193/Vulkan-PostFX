package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.framegraph;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTextureView;

/**
 * Native framegraph target wrapper.
 *
 * A target can be:
 * - output-capable color target, for example minecraft:main or a VPFX transient target;
 * - input-only target, for example vulkanpostfx:scene_depth, where the sampled view is the depth texture view.
 */
public final class VpfxNativeFrameTarget {
    private final String id;
    private final RenderTarget renderTarget;
    private final boolean owned;
    private final boolean outputCapable;
    private final GpuTextureView sampledViewOverride;

    public VpfxNativeFrameTarget(String id, RenderTarget renderTarget, boolean owned) {
        this(id, renderTarget, owned, true, null);
    }

    public VpfxNativeFrameTarget(
            String id,
            RenderTarget renderTarget,
            boolean owned,
            boolean outputCapable,
            GpuTextureView sampledViewOverride
    ) {
        this.id = id != null ? id : "unknown";
        this.renderTarget = renderTarget;
        this.owned = owned;
        this.outputCapable = outputCapable;
        this.sampledViewOverride = sampledViewOverride;
    }

    public String id() {
        return id;
    }

    public RenderTarget renderTarget() {
        return renderTarget;
    }

    public boolean owned() {
        return owned;
    }

    public boolean outputCapable() {
        return outputCapable;
    }

    public int width() {
        return renderTarget == null ? 0 : renderTarget.width;
    }

    public int height() {
        return renderTarget == null ? 0 : renderTarget.height;
    }

    public GpuTextureView sampledView() {
        if (sampledViewOverride != null) {
            return sampledViewOverride;
        }
        return colorView();
    }

    public GpuTextureView colorView() {
        return renderTarget == null ? null : renderTarget.getColorTextureView();
    }

    public GpuTextureView depthView() {
        return renderTarget == null ? null : renderTarget.getDepthTextureView();
    }

    public void destroyIfOwned() {
        if (!owned || renderTarget == null) {
            return;
        }

        try {
            renderTarget.destroyBuffers();
        } catch (Exception ignored) {
        }
    }
}
