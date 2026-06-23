package com.ionhex975.vulkanpostfx.client.mixin;

import com.ionhex975.vulkanpostfx.client.hook.PostFxHookBridge;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererPostSlotMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    /**
     * 命中“世界完成、GUI 开始之前”的关键节点。
     *
     * 这里选择在 doEntityOutline() 调用之后注入，
     * 因为源码表明：
     * - 世界主渲染已完成；
     * - 实体描边已完成；
     * - 官方 PostChain 就是在这个位置之后执行；
     * - GUI 还没有开始。
     *
     * 这是当前最适合我们做自定义 PostFX PoC 的候选插入位。
     */
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V",
                    shift = At.Shift.AFTER
            )
    )
    private void vulkanpostfx$afterEntityOutline(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        PostFxHookBridge.onPostEffectSlot(this.minecraft, (GameRenderer) (Object) this);
    }
}