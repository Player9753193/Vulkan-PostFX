package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.framegraph;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxTextureFilter;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxTextureWrap;
import com.ionhex975.vulkanpostfx.client.runtime.texture.VpfxRuntimeTextureDescriptor;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.systems.RenderSystem;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Sampler resolver for the native framegraph.
 *
 * The public SamplerCache surface has changed across 26.x snapshots, so repeat sampling is
 * resolved reflectively when the cache exposes a repeat/wrap method. If not available, VPFX
 * falls back to clamp-to-edge rather than failing the render path.
 */
public final class VpfxNativeSamplerResolver {
    private static boolean repeatReflectionLogged;
    private static boolean repeatFallbackLogged;

    private VpfxNativeSamplerResolver() {
    }

    public static GpuSampler forTarget(FilterMode mode) {
        return RenderSystem.getSamplerCache().getClampToEdge(mode);
    }

    public static GpuSampler forTexture(VpfxRuntimeTextureDescriptor descriptor) {
        FilterMode mode = resolveFilterMode(descriptor);
        VpfxTextureWrap wrap = descriptor == null ? VpfxTextureWrap.CLAMP : descriptor.getWrap();

        if (wrap == VpfxTextureWrap.REPEAT) {
            GpuSampler repeat = tryResolveRepeatSampler(mode);
            if (repeat != null) {
                return repeat;
            }

            if (!repeatFallbackLogged) {
                repeatFallbackLogged = true;
                VulkanPostFX.LOGGER.warn(
                        "[{}] VPFX NR-2.2: repeat sampler requested, but current Minecraft SamplerCache does not expose a repeat sampler method. Falling back to clamp-to-edge.",
                        VulkanPostFX.MOD_ID
                );
            }
        }

        return RenderSystem.getSamplerCache().getClampToEdge(mode);
    }

    private static FilterMode resolveFilterMode(VpfxRuntimeTextureDescriptor descriptor) {
        if (descriptor == null || descriptor.getFilter() == null) {
            return FilterMode.LINEAR;
        }
        return descriptor.getFilter() == VpfxTextureFilter.NEAREST
                ? FilterMode.NEAREST
                : FilterMode.LINEAR;
    }

    private static GpuSampler tryResolveRepeatSampler(FilterMode mode) {
        Object cache = RenderSystem.getSamplerCache();
        if (cache == null) {
            return null;
        }

        for (Method method : cache.getClass().getMethods()) {
            if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != FilterMode.class) {
                continue;
            }
            if (!GpuSampler.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }

            String name = method.getName().toLowerCase(Locale.ROOT);
            if (!name.contains("repeat") && !name.contains("wrap")) {
                continue;
            }
            if (name.contains("clamp")) {
                continue;
            }

            try {
                GpuSampler sampler = (GpuSampler) method.invoke(cache, mode);
                if (sampler != null) {
                    if (!repeatReflectionLogged) {
                        repeatReflectionLogged = true;
                        VulkanPostFX.LOGGER.info(
                                "[{}] VPFX NR-2.2: repeat sampler resolved through SamplerCache#{}({})",
                                VulkanPostFX.MOD_ID,
                                method.getName(),
                                mode
                        );
                    }
                    return sampler;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        return null;
    }
}
