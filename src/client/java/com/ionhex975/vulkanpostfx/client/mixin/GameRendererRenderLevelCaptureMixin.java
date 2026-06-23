package com.ionhex975.vulkanpostfx.client.mixin;

import com.ionhex975.vulkanpostfx.client.hook.PostFxHookBridge;
import com.ionhex975.vulkanpostfx.client.shader.uniform.VpfxFrameEnvironmentState;
import com.ionhex975.vulkanpostfx.client.shader.uniform.VpfxFrameProjectionState;
import com.ionhex975.vulkanpostfx.client.state.PostFxRuntimeState;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererRenderLevelCaptureMixin {
    @Shadow
    @Final
    private CrossFrameResourcePool resourcePool;

    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"
            )
    )
    private void vulkanpostfx$captureFinalWorldMatrices(
            DeltaTracker deltaTracker,
            CallbackInfo ci,
            @Local CameraRenderState cameraState,
            @Local Matrix4fc modelViewMatrix,
            @Local Matrix4f projectionMatrix
    ) {
        PostFxRuntimeState.incrementFrameEpoch();
        Minecraft minecraft = Minecraft.getInstance();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);

        VpfxFrameEnvironmentState.capture(cameraState, partialTick);
        RenderTarget mainTarget = minecraft.gameRenderer.mainRenderTarget();
        VpfxFrameProjectionState.capture(
                cameraState,
                projectionMatrix,
                modelViewMatrix,
                mainTarget.width,
                mainTarget.height
        );
    }

    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
                    shift = At.Shift.AFTER
            )
    )
    private void vulkanpostfx$applyWorldStagePostEffectBeforeHand(
            DeltaTracker deltaTracker,
            CallbackInfo ci
    ) {
        PostFxHookBridge.onWorldPostEffectBeforeHand(
                Minecraft.getInstance(),
                this.resourcePool
        );
    }
}
