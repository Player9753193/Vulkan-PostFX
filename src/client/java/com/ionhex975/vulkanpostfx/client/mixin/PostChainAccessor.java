package com.ionhex975.vulkanpostfx.client.mixin;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostChainConfig;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;
import java.util.Set;

@Mixin(PostChain.class)
public interface PostChainAccessor {
    @Invoker("addToFrame")
    void vulkanpostfx$invokeAddToFrame(
            FrameGraphBuilder frame,
            int width,
            int height,
            PostChain.TargetBundle bundle
    );

    @Invoker("getOrCreatePersistentTarget")
    RenderTarget vulkanpostfx$invokeGetOrCreatePersistentTarget(
            Identifier id,
            RenderTargetDescriptor descriptor
    );

    @Accessor("externalTargets")
    Set<Identifier> vulkanpostfx$getExternalTargets();

    @Accessor("internalTargets")
    Map<Identifier, PostChainConfig.InternalTarget> vulkanpostfx$getInternalTargets();
}