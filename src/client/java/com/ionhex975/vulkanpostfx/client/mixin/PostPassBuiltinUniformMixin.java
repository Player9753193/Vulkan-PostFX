package com.ionhex975.vulkanpostfx.client.mixin;

import com.ionhex975.vulkanpostfx.client.shader.uniform.VpfxBuiltinUniformBuffer;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * 继续沿用当前正式方案：
 * - 不往 customUniforms 里新塞不存在的 uniform 名
 * - 只更新已经由 JSON uniforms 创建好的 VpfxBuiltins buffer
 * - 不再要求 pass name 以 vpfxzip_ 开头：任何显式声明 VpfxBuiltins 的 VPFX pass 都可获得同一套内置数据。
 *
 * Fake Held-Light Glow 只是继续扩展 VpfxBuiltins 的内容，
 * 这里的注入点只需要按 buffer 是否存在来判定。
 */
@Mixin(PostPass.class)
public abstract class PostPassBuiltinUniformMixin {
    @Shadow
    @Final
    private String name;

    @Shadow
    @Final
    private Map<String, GpuBuffer> customUniforms;

    @Inject(method = "addToFrame", at = @At("HEAD"))
    private void vulkanpostfx$updateVpfxBuiltins(
            FrameGraphBuilder frame,
            Map<Identifier, ResourceHandle<RenderTarget>> targets,
            GpuBufferSlice shaderOrthoMatrix,
            CallbackInfo ci
    ) {
        GpuBuffer buffer = this.customUniforms.get(VpfxBuiltinUniformBuffer.BLOCK_NAME);
        if (buffer == null || buffer.isClosed()) {
            return;
        }

        VpfxBuiltinUniformBuffer.writeToExisting(buffer);
    }
}