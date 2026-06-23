package com.ionhex975.vulkanpostfx.client.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelRenderer.class)
public interface LevelRendererShadowAccess {
    @Accessor("entityRenderDispatcher")
    EntityRenderDispatcher vulkanpostfx$getEntityRenderDispatcher();

    @Accessor("renderBuffers")
    RenderBuffers vulkanpostfx$getRenderBuffers();

    @Accessor("featureRenderDispatcher")
    FeatureRenderDispatcher vulkanpostfx$getFeatureRenderDispatcher();

    @Accessor("levelRenderState")
    LevelRenderState vulkanpostfx$getLevelRenderState();

    @Accessor("viewArea")
    ViewArea vulkanpostfx$getViewArea();

    @Accessor("visibleSections")
    ObjectArrayList<SectionRenderDispatcher.RenderSection> vulkanpostfx$getVisibleSections();
}
