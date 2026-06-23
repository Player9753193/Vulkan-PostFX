package com.ionhex975.vulkanpostfx.client.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Accessor("postEffectId")
    void vulkanpostfx$setPostEffectId(Identifier id);

    @Accessor("effectActive")
    void vulkanpostfx$setEffectActive(boolean active);
}