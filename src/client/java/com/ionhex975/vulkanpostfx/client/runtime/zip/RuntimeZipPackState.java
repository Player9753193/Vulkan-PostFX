package com.ionhex975.vulkanpostfx.client.runtime.zip;

import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxTargetDefinition;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;
import java.util.Map;

public final class RuntimeZipPackState {
    private static volatile boolean active;
    private static volatile boolean resourcePackInjectionAllowed;
    private static volatile String packId = "";
    private static volatile String runtimeNamespace = "";
    private static volatile Path runtimeRoot;
    private static volatile Identifier externalPostEffectId;
    private static volatile boolean minecraftResourceReloadedWithRuntimePack;
    private static volatile Map<String, VpfxTargetDefinition> targetDefinitions = Map.of();

    private RuntimeZipPackState() {
    }

    public static void apply(RuntimeZipPackMaterializationResult result) {
        active = true;
        // A materialized runtime pack must remain visible to Minecraft's resource
        // pack discovery while this VPFX pack is active. The safety guard belongs
        // around forced Minecraft#reloadResourcePacks() calls, not around pack
        // visibility itself; otherwise native packs cannot run first and PostChain
        // fallback can never find the generated post effect.
        resourcePackInjectionAllowed = true;
        packId = result.packId();
        runtimeNamespace = result.runtimeNamespace();
        runtimeRoot = result.runtimeRoot();
        externalPostEffectId = result.externalPostEffectId();
        targetDefinitions = Map.copyOf(result.targetDefinitions());
    }

    public static void clear() {
        active = false;
        resourcePackInjectionAllowed = false;
        packId = "";
        runtimeNamespace = "";
        runtimeRoot = null;
        externalPostEffectId = null;
        minecraftResourceReloadedWithRuntimePack = false;
        targetDefinitions = Map.of();
    }

    public static boolean isActive() {
        return active;
    }

    /**
     * Controls whether the generated runtime pack is visible to Minecraft's resource pack
     * discovery during the current reload transaction. Keep this false by default; hard
     * resource reload failures inside a loaded world can disconnect the client.
     */
    public static boolean isResourcePackInjectionAllowed() {
        return resourcePackInjectionAllowed;
    }

    public static void setResourcePackInjectionAllowed(boolean allowed) {
        resourcePackInjectionAllowed = allowed;
    }

    public static void disableResourcePackInjection() {
        resourcePackInjectionAllowed = false;
    }

    public static void enableResourcePackInjection() {
        resourcePackInjectionAllowed = true;
    }

    public static String getPackId() {
        return packId;
    }

    public static String getRuntimeNamespace() {
        return runtimeNamespace;
    }

    public static Path getRuntimeRoot() {
        return runtimeRoot;
    }


    public static void markMinecraftResourceReloadCompleted() {
        if (active && resourcePackInjectionAllowed) {
            minecraftResourceReloadedWithRuntimePack = true;
        }
    }

    public static boolean isMinecraftResourceReloadedWithRuntimePack() {
        return minecraftResourceReloadedWithRuntimePack;
    }

    public static Identifier getExternalPostEffectId() {
        return externalPostEffectId;
    }

    public static Map<String, VpfxTargetDefinition> getTargetDefinitions() {
        return targetDefinitions;
    }

    public static VpfxTargetDefinition getTargetDefinition(String targetId) {
        return targetDefinitions.get(targetId);
    }

    public static boolean hasScaledTargets() {
        for (VpfxTargetDefinition definition : targetDefinitions.values()) {
            if (definition.getScale().isPresent()) {
                double scale = definition.getScale().get();
                if (Math.abs(scale - 1.0) > 1.0E-6) {
                    return true;
                }
            }
        }
        return false;
    }
}