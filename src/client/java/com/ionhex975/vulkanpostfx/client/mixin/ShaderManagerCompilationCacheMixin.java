package com.ionhex975.vulkanpostfx.client.mixin;

import com.ionhex975.vulkanpostfx.client.postfx.PostFxExternalTargetIds;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostChainConfig;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.LinkedHashSet;
import java.util.Set;

@Mixin(targets = "net.minecraft.client.renderer.ShaderManager$CompilationCache")
public abstract class ShaderManagerCompilationCacheMixin {

    @Redirect(
            method = "loadPostChain",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/PostChain;load(Lnet/minecraft/client/renderer/PostChainConfig;Lnet/minecraft/client/renderer/texture/TextureManager;Ljava/util/Set;Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/renderer/Projection;Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;)Lnet/minecraft/client/renderer/PostChain;"
            )
    )
    private PostChain vulkanpostfx$redirectPostChainLoad(
            PostChainConfig config,
            TextureManager textureManager,
            Set<Identifier> allowedExternalTargets,
            Identifier id,
            Projection projection,
            ProjectionMatrixBuffer projectionMatrixBuffer
    ) throws ShaderManager.CompilationException {
        LinkedHashSet<Identifier> merged = new LinkedHashSet<>();

        if (allowedExternalTargets != null) {
            merged.addAll(allowedExternalTargets);
        }

        merged.addAll(PostFxExternalTargetIds.allowedTargets());

        return PostChain.load(
                config,
                textureManager,
                Set.copyOf(merged),
                id,
                projection,
                projectionMatrixBuffer
        );
    }
}