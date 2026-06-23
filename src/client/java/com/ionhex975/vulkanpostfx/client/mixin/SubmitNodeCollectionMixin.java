package com.ionhex975.vulkanpostfx.client.mixin;

import com.ionhex975.vulkanpostfx.client.shadow.VanillaEntityShadowSuppressor;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(SubmitNodeCollection.class)
public abstract class SubmitNodeCollectionMixin {
    @Inject(method = "submitShadow", at = @At("HEAD"), cancellable = true)
    private void vulkanpostfx$suppressVanillaCircularEntityShadow(
            PoseStack poseStack,
            float radius,
            List<EntityRenderState.ShadowPiece> pieces,
            CallbackInfo ci
    ) {
        if (VanillaEntityShadowSuppressor.shouldSuppress()) {
            ci.cancel();
        }
    }
}
