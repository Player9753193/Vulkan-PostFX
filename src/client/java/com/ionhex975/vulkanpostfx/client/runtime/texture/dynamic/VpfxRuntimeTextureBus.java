package com.ionhex975.vulkanpostfx.client.runtime.texture.dynamic;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.light.colored.volume.VpfxColoredLightVolumeAtlas;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxTextureFilter;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxTextureWrap;
import com.ionhex975.vulkanpostfx.client.runtime.texture.VpfxRuntimeTextureDescriptor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central registry for VPFX runtime-generated data textures.
 *
 * The bus is deliberately backend-neutral. PostChain can resolve these logical
 * names to Minecraft textures, and a later native/compute path can bind the
 * same logical names to native GPU images without changing VPFX pack syntax.
 */
public final class VpfxRuntimeTextureBus {
    public static final String SCENE_DEPTH = "scene_depth";
    public static final String SHADOW_DEPTH = "shadow_depth";
    public static final String COLORED_LIGHT_VOLUME = "colored_light_volume";
    public static final String MATERIAL_MASK = "material_mask";
    public static final String NORMAL_BUFFER = "normal_buffer";

    private static final Map<String, VpfxRuntimeTextureHandle> HANDLES = new LinkedHashMap<>();
    private static boolean bootstrapped;

    private VpfxRuntimeTextureBus() {
    }

    public static synchronized void bootstrapDefaults() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;

        registerIfAbsent(VpfxRuntimeTextureHandle.declared(
                SCENE_DEPTH,
                Identifier.fromNamespaceAndPath(VulkanPostFX.MOD_ID, SCENE_DEPTH),
                0,
                0,
                VpfxRuntimeTextureFormat.R32F,
                true,
                "Provided by SceneDepthCaptureTargets when available."
        ));
        registerIfAbsent(VpfxRuntimeTextureHandle.declared(
                SHADOW_DEPTH,
                Identifier.fromNamespaceAndPath(VulkanPostFX.MOD_ID, SHADOW_DEPTH),
                0,
                0,
                VpfxRuntimeTextureFormat.R32F,
                true,
                "Provided by ShadowRenderTargetsLite when available."
        ));
        registerIfAbsent(VpfxRuntimeTextureHandle.declared(
                COLORED_LIGHT_VOLUME,
                Identifier.fromNamespaceAndPath(VulkanPostFX.MOD_ID, COLORED_LIGHT_VOLUME),
                VpfxColoredLightVolumeAtlas.ATLAS_WIDTH,
                VpfxColoredLightVolumeAtlas.ATLAS_HEIGHT,
                VpfxRuntimeTextureFormat.RGBA8,
                true,
                "Reserved for the CPU-built colored light volume atlas."
        ));
        registerIfAbsent(VpfxRuntimeTextureHandle.declared(
                MATERIAL_MASK,
                Identifier.fromNamespaceAndPath(VulkanPostFX.MOD_ID, MATERIAL_MASK),
                0,
                0,
                VpfxRuntimeTextureFormat.R8,
                true,
                "Reserved for future material/water classification."
        ));
        registerIfAbsent(VpfxRuntimeTextureHandle.declared(
                NORMAL_BUFFER,
                Identifier.fromNamespaceAndPath(VulkanPostFX.MOD_ID, NORMAL_BUFFER),
                0,
                0,
                VpfxRuntimeTextureFormat.RGBA8,
                true,
                "Reserved for future normal reconstruction/buffer input."
        ));
    }

    public static synchronized void registerOrUpdate(VpfxRuntimeTextureHandle handle) {
        bootstrapDefaults();
        if (handle == null) {
            return;
        }
        HANDLES.put(handle.logicalName(), handle);
    }

    public static synchronized void registerIfAbsent(VpfxRuntimeTextureHandle handle) {
        if (handle == null) {
            return;
        }
        HANDLES.putIfAbsent(handle.logicalName(), handle);
    }

    public static synchronized void markReady(String logicalName, int width, int height, long frameEpoch, String reason) {
        bootstrapDefaults();
        VpfxRuntimeTextureHandle handle = HANDLES.get(logicalName);
        if (handle == null) {
            return;
        }
        HANDLES.put(logicalName, handle.markReady(width, height, frameEpoch, reason));
    }

    public static synchronized void markUnavailable(String logicalName, String reason) {
        bootstrapDefaults();
        VpfxRuntimeTextureHandle handle = HANDLES.get(logicalName);
        if (handle == null) {
            return;
        }
        HANDLES.put(logicalName, handle.markUnavailable(reason));
    }

    public static synchronized VpfxRuntimeTextureHandle get(String logicalName) {
        bootstrapDefaults();
        return HANDLES.get(logicalName);
    }

    public static synchronized boolean isRuntimeBusTexture(String logicalName) {
        bootstrapDefaults();
        // Only colored_light_volume is currently exposed as a regular VPFX
        // texture input. scene_depth/shadow_depth remain target inputs, and
        // material_mask/normal_buffer are future placeholders.
        return COLORED_LIGHT_VOLUME.equals(logicalName) && HANDLES.containsKey(logicalName);
    }

    public static synchronized VpfxRuntimeTextureDescriptor toRuntimeDescriptor(String logicalName) {
        bootstrapDefaults();
        VpfxRuntimeTextureHandle handle = HANDLES.get(logicalName);
        if (handle == null || handle.location() == null) {
            return null;
        }
        return new VpfxRuntimeTextureDescriptor(
                handle.logicalName(),
                "runtime-bus:" + handle.logicalName(),
                handle.location().getPath(),
                handle.location().toString(),
                Math.max(1, handle.width()),
                Math.max(1, handle.height()),
                true,
                VpfxTextureFilter.LINEAR,
                VpfxTextureWrap.CLAMP
        );
    }

    public static synchronized List<VpfxRuntimeTextureDescriptor> runtimeDescriptors() {
        bootstrapDefaults();
        List<VpfxRuntimeTextureDescriptor> descriptors = new ArrayList<>();
        for (String logicalName : HANDLES.keySet()) {
            if (!isRuntimeBusTexture(logicalName)) {
                continue;
            }
            VpfxRuntimeTextureDescriptor descriptor = toRuntimeDescriptor(logicalName);
            if (descriptor != null) {
                descriptors.add(descriptor);
            }
        }
        return List.copyOf(descriptors);
    }

    public static synchronized List<VpfxRuntimeTextureHandle> snapshot() {
        bootstrapDefaults();
        return List.copyOf(HANDLES.values());
    }

    public static synchronized Map<String, VpfxRuntimeTextureHandle> snapshotMap() {
        bootstrapDefaults();
        return Collections.unmodifiableMap(new LinkedHashMap<>(HANDLES));
    }

    public static synchronized String debugSummary() {
        bootstrapDefaults();
        List<String> parts = new ArrayList<>();
        for (VpfxRuntimeTextureHandle handle : HANDLES.values()) {
            parts.add(handle.logicalName() + "=" + (handle.ready() ? "ready" : "pending")
                    + "(" + handle.format().id() + ", " + handle.sizeString() + ")");
        }
        return String.join(", ", parts);
    }
}
