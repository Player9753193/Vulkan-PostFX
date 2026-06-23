package com.ionhex975.vulkanpostfx.client.postfx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 支持多个 external RenderTarget 的 TargetBundle。
 */
public final class MutableTargetBundle implements PostChain.TargetBundle {
    private final Map<Identifier, ResourceHandle<RenderTarget>> handles = new HashMap<>();

    public MutableTargetBundle put(Identifier id, ResourceHandle<RenderTarget> handle) {
        this.handles.put(id, handle);
        return this;
    }

    @Override
    public void replace(Identifier id, ResourceHandle<RenderTarget> handle) {
        if (!this.handles.containsKey(id)) {
            throw new IllegalArgumentException("No target with id " + id);
        }
        this.handles.put(id, handle);
    }

    @Nullable
    @Override
    public ResourceHandle<RenderTarget> get(Identifier id) {
        return this.handles.get(id);
    }
}