package com.ionhex975.vulkanpostfx.client.mixin;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.postfx.PostFxExternalTargetIds;
import com.ionhex975.vulkanpostfx.client.postfx.PostFxExternalTargetRunner;
import com.ionhex975.vulkanpostfx.client.runtime.zip.RuntimeZipPackState;
import com.ionhex975.vulkanpostfx.client.state.PostFxRuntimeState;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(PostChain.class)
public abstract class PostChainProcessMixin {
    @Unique
    private static boolean vulkanpostfx$firstExternalInterceptLogged;

    private static final Set<Identifier> INTERCEPTION_TARGETS = Set.of(
            PostFxExternalTargetIds.SCENE_COLOR,
            PostFxExternalTargetIds.SCENE_DEPTH,
            PostFxExternalTargetIds.SHADOW_DEPTH
    );

    @Inject(
            method = "process",
            at = @At("HEAD"),
            cancellable = true
    )
    private void vulkanpostfx$processWithExternalTargets(
            RenderTarget mainTarget,
            GraphicsResourceAllocator resourceAllocator,
            CallbackInfo ci
    ) {
        if (PostFxRuntimeState.isSkipPostChainThisFrame()) {
            VulkanPostFX.LOGGER.info(
                    "[{}] VPFX NR-1E-B: PostChain.process cancelled via mixin guard (skipPostChainThisFrame=true, reason={})",
                    VulkanPostFX.MOD_ID,
                    PostFxRuntimeState.skipPostChainReasonForLog()
            );
            ci.cancel();
            return;
        }

        PostChain self = (PostChain) (Object) this;
        Set<Identifier> externalTargets =
                ((PostChainAccessor) self).vulkanpostfx$getExternalTargets();

        if (externalTargets == null || externalTargets.isEmpty()) {
            return;
        }

        boolean hasVpfxExternal = false;
        for (Identifier id : INTERCEPTION_TARGETS) {
            if (externalTargets.contains(id)) {
                hasVpfxExternal = true;
                break;
            }
        }
        if (!hasVpfxExternal) {
            return;
        }

        boolean builtinShadowDepthDebug =
                PostFxRuntimeState.isShadowDepthDebugViewEnabled()
                        && externalTargets.contains(PostFxExternalTargetIds.SHADOW_DEPTH);

        if (!RuntimeZipPackState.isActive() && !builtinShadowDepthDebug) {
            VulkanPostFX.LOGGER.warn(
                    "[{}] PostChain references VPFX external target(s) {}, but runtime ZIP pack is not active; falling back to vanilla process()",
                    VulkanPostFX.MOD_ID,
                    externalTargets
            );
            return;
        }

        if (!vulkanpostfx$firstExternalInterceptLogged) {
            vulkanpostfx$firstExternalInterceptLogged = true;
            VulkanPostFX.LOGGER.info(
                    "[{}] Intercepting PostChain.process for chain(s) referencing VPFX external target(s) {}",
                    VulkanPostFX.MOD_ID,
                    externalTargets
            );
        }

        if (PostFxRuntimeState.isSkipPostChainThisFrame()) {
            VulkanPostFX.LOGGER.info(
                    "[{}] VPFX NR-1E-B: external PostChain skipped this frame = true",
                    VulkanPostFX.MOD_ID
            );
            VulkanPostFX.LOGGER.info(
                    "[{}] VPFX NR-1E-B: skip reason                           = {}",
                    VulkanPostFX.MOD_ID,
                    PostFxRuntimeState.skipPostChainReasonForLog()
            );
            VulkanPostFX.LOGGER.info(
                    "[{}] VPFX NR-1E-B: native backend replacement             = false",
                    VulkanPostFX.MOD_ID
            );
            VulkanPostFX.LOGGER.info(
                    "[{}] VPFX NR-1E-B: user shader native execution           = false",
                    VulkanPostFX.MOD_ID
            );
            VulkanPostFX.LOGGER.info(
                    "[{}] VPFX NR-1E-B: builtin passthrough only               = true",
                    VulkanPostFX.MOD_ID
            );
            ci.cancel();
            return;
        }

        PostFxExternalTargetRunner.process(
                self,
                mainTarget,
                resourceAllocator
        );
        ci.cancel();
    }
}
