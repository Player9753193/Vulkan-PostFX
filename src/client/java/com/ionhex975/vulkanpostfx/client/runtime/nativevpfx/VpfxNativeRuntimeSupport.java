package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.diagnostics.VpfxDiagnosticsConfig;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxGraphDefinition;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxPackManifest;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxPassDefinition;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxPassInput;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxTargetDefinition;
import com.ionhex975.vulkanpostfx.client.runtime.vpfx.VpfxPostChainBackend;
import com.ionhex975.vulkanpostfx.client.runtime.texture.dynamic.VpfxRuntimeTextureBus;
import com.ionhex975.vulkanpostfx.client.runtime.vpfx.VpfxRuntimeBackend;
import com.ionhex975.vulkanpostfx.client.state.PostFxRuntimeState;

public final class VpfxNativeRuntimeSupport {

    private static final String SYSTEM_PROPERTY = "vulkanpostfx.vpfx.nativeRuntime";
    private static final String SYSTEM_PROPERTY_EXECUTE = "vulkanpostfx.vpfx.nativeRuntime.execute";
    private static final String SYSTEM_PROPERTY_FORCE = "vulkanpostfx.vpfx.nativeRuntime.force";

    /**
     * Runtime backend priority only.
     *
     * Default policy is native-first:
     * - If the selected VPFX pack passes the native framegraph support check, use the native backend.
     * - If the selected pack does not pass the native support check, fall back to minecraft_postchain.
     * - If the user explicitly sets -Dvulkanpostfx.vpfx.nativeRuntime=false, force minecraft_postchain.
     *
     * This class only decides backend priority. It must not change native execution semantics,
     * PostChain execution, shaders, vignette behavior, or shadow rendering.
     */
    private static final boolean DEFAULT_NATIVE_RUNTIME_ENABLED = true;

    private static boolean executeFlagLogged;
    private static boolean flagVisibilityLogged;
    private static boolean executeImpliesNativeLogged;
    private static boolean defaultNativeRuntimeLogged;

    private VpfxNativeRuntimeSupport() {
    }

    private static void logFlagVisibilityOnce() {
        if (flagVisibilityLogged) {
            return;
        }

        flagVisibilityLogged = true;

        String nativeRuntimeRaw = System.getProperty(SYSTEM_PROPERTY);
        String executeRaw = System.getProperty(SYSTEM_PROPERTY_EXECUTE);
        String forceRaw = System.getProperty(SYSTEM_PROPERTY_FORCE);

        boolean execute = Boolean.getBoolean(SYSTEM_PROPERTY_EXECUTE);
        boolean force = Boolean.getBoolean(SYSTEM_PROPERTY_FORCE);
        boolean nativeRequested = isNativeRuntimeRequested();

        VulkanPostFX.LOGGER.info(
                "[{}] VPFX Native Runtime flags: nativeRuntimeProperty={}, nativeRuntimeDefaultEnabled={}, nativeRuntimeEffective={}, nativeRuntime.execute={}, nativeRuntime.force={}, isExecuteEnabled={}",
                VulkanPostFX.MOD_ID,
                nativeRuntimeRaw == null ? "<unset>" : nativeRuntimeRaw,
                DEFAULT_NATIVE_RUNTIME_ENABLED,
                nativeRequested,
                executeRaw == null ? execute : executeRaw,
                forceRaw == null ? force : forceRaw,
                isExecuteEnabled()
        );

        if (nativeRuntimeRaw == null && DEFAULT_NATIVE_RUNTIME_ENABLED && !defaultNativeRuntimeLogged) {
            defaultNativeRuntimeLogged = true;
            VulkanPostFX.LOGGER.info(
                    "[{}] VPFX Native Runtime defaults to native-first priority. Use -D{}=false to force minecraft_postchain.",
                    VulkanPostFX.MOD_ID,
                    SYSTEM_PROPERTY
            );
        }
    }

    public static VpfxRuntimeBackend selectBackend(VpfxPackManifest manifest, VpfxGraphDefinition graph) {
        return selectBackendResult(manifest, graph).backend();
    }

    public static VpfxBackendSelectionResult selectBackendResult(VpfxPackManifest manifest, VpfxGraphDefinition graph) {
        logFlagVisibilityOnce();

        String packId = manifest == null ? "unknown" : manifest.getPackId();
        boolean nativeRequested = isNativeRuntimeRequested();
        if (!nativeRequested) {
            String reason = "native runtime priority disabled by property; selecting minecraft_postchain";
            VulkanPostFX.LOGGER.info(
                    "[{}] VPFX Native Runtime priority disabled by property. Selecting minecraft_postchain for pack '{}'.",
                    VulkanPostFX.MOD_ID,
                    packId
            );
            return VpfxBackendSelectionResult.postChainSelected(new VpfxPostChainBackend(), reason);
        }

        VpfxNativeRuntimeSupportResult result = check(graph, manifest);
        boolean force = Boolean.getBoolean(SYSTEM_PROPERTY_FORCE);

        if (result.isSupported()) {
            VulkanPostFX.LOGGER.info(
                    "[{}] VPFX Native Runtime NR-2.3: support check PASSED for pack '{}'. "
                            + "Selecting native framegraph backend and bypassing minecraft_postchain at render time.",
                    VulkanPostFX.MOD_ID,
                    packId
            );
            return VpfxBackendSelectionResult.nativeSelected(new VpfxNativeRuntimeBackend(false));
        }

        if (force) {
            VulkanPostFX.LOGGER.warn(
                    "[{}] VPFX Native Runtime NR-2.3: support check FAILED for pack '{}': {}. "
                            + "Force flag is enabled, so native backend will still be selected. "
                            + "If native preparation or execution fails, VPFX will still fall back to minecraft_postchain.",
                    VulkanPostFX.MOD_ID,
                    packId,
                    result.reason()
            );
            return VpfxBackendSelectionResult.nativeForced(new VpfxNativeRuntimeBackend(true), result.reason());
        }

        String fallbackReason = result.reason();
        VulkanPostFX.LOGGER.warn(
                "[{}] VPFX Native Runtime NR-2.3: support check FAILED for pack '{}': {}. Falling back to minecraft_postchain.",
                VulkanPostFX.MOD_ID,
                packId,
                fallbackReason
        );
        return VpfxBackendSelectionResult.postChainFallback(
                new VpfxPostChainBackend(),
                true,
                false,
                VpfxBackendSelectionResult.STAGE_SUPPORT_CHECK,
                fallbackReason
        );
    }

    public static VpfxNativeRuntimeSupportResult check(VpfxGraphDefinition graph, VpfxPackManifest manifest) {
        if (graph == null) {
            return VpfxNativeRuntimeSupportResult.unsupported("native framegraph unsupported: null graph");
        }

        if (graph.getPasses().isEmpty()) {
            return VpfxNativeRuntimeSupportResult.unsupported("native framegraph unsupported: graph has no passes");
        }

        boolean writesMain = false;
        for (VpfxPassDefinition pass : graph.getPasses()) {
            if (pass.getVertexShader() == null || pass.getVertexShader().isBlank()
                    || pass.getFragmentShader() == null || pass.getFragmentShader().isBlank()) {
                return VpfxNativeRuntimeSupportResult.unsupported("native framegraph unsupported: pass has blank shader reference");
            }

            if (pass.getInputs().isEmpty()) {
                return VpfxNativeRuntimeSupportResult.unsupported("native framegraph unsupported: pass has no inputs");
            }

            for (VpfxPassInput input : pass.getInputs()) {
                if (input.isTextureInput()) {
                    if (input.isUseDepthBuffer()) {
                        return VpfxNativeRuntimeSupportResult.unsupported("native framegraph unsupported: texture input does not support use_depth_buffer=true");
                    }

                    String texture = input.getTexture();
                    if (texture == null || texture.isBlank()) {
                        return VpfxNativeRuntimeSupportResult.unsupported("native framegraph unsupported: blank texture input");
                    }
                    if ((manifest == null || !manifest.getTextures().containsKey(texture))
                            && !VpfxRuntimeTextureBus.isRuntimeBusTexture(texture)) {
                        return VpfxNativeRuntimeSupportResult.unsupported("native framegraph unsupported: undeclared texture input: " + texture);
                    }
                    continue;
                }

                String target = input.getTarget();
                if (target == null || target.isBlank()) {
                    return VpfxNativeRuntimeSupportResult.unsupported("native framegraph unsupported: blank input target");
                }

                if (target.startsWith("history:")) {
                    if (input.isUseDepthBuffer()) {
                        return VpfxNativeRuntimeSupportResult.unsupported("native framegraph unsupported: history input does not support use_depth_buffer=true: " + target);
                    }

                    String baseTargetId = target.substring("history:".length());
                    VpfxTargetDefinition definition = graph.getTargets().get(baseTargetId);
                    if (definition == null) {
                        return VpfxNativeRuntimeSupportResult.unsupported("native framegraph unsupported: history input refers to undeclared target: " + baseTargetId);
                    }
                    if (!definition.isHistory()) {
                        return VpfxNativeRuntimeSupportResult.unsupported("native framegraph unsupported: history input requires target history=true or ping_pong=true: " + baseTargetId);
                    }
                    continue;
                }

                if (target.contains("shadow") && !isShadowDepthTarget(target)) {
                    return VpfxNativeRuntimeSupportResult.unsupported("native framegraph unsupported: unknown shadow input target: " + target);
                }
                if (target.contains("depth") && !isSceneDepthTarget(target) && !isShadowDepthTarget(target)) {
                    return VpfxNativeRuntimeSupportResult.unsupported("native framegraph unsupported: unknown depth input target: " + target);
                }

                if (!"minecraft:main".equals(target)
                        && !"minecraft:scene_color".equals(target)
                        && !isSceneDepthTarget(target)
                        && !isShadowDepthTarget(target)
                        && !graph.getTargets().containsKey(target)) {
                    return VpfxNativeRuntimeSupportResult.unsupported("native framegraph unsupported: input target is not declared: " + target);
                }

                if (input.isUseDepthBuffer()) {
                    String depthValidationError = validateDepthInputTarget(target, graph);
                    if (depthValidationError != null) {
                        return VpfxNativeRuntimeSupportResult.unsupported("native framegraph unsupported: " + depthValidationError);
                    }
                }
            }

            String output = pass.getOutput();
            if (output == null || output.isBlank()) {
                return VpfxNativeRuntimeSupportResult.unsupported("native framegraph unsupported: pass has blank output");
            }
            if ("minecraft:main".equals(output)) {
                writesMain = true;
            } else if (!graph.getTargets().containsKey(output)) {
                return VpfxNativeRuntimeSupportResult.unsupported("native framegraph unsupported: output target is not declared: " + output);
            }
        }

        if (!writesMain) {
            return VpfxNativeRuntimeSupportResult.unsupported("native framegraph unsupported: graph does not write to minecraft:main");
        }

        return VpfxNativeRuntimeSupportResult.supported();
    }


    private static String validateDepthInputTarget(String target, VpfxGraphDefinition graph) {
        if ("minecraft:main".equals(target)
                || "minecraft:scene_color".equals(target)
                || isSceneDepthTarget(target)
                || isShadowDepthTarget(target)) {
            return null;
        }

        VpfxTargetDefinition targetDefinition = graph.getTargets().get(target);
        if (targetDefinition == null) {
            return "use_depth_buffer=true target is not declared: " + target;
        }
        if (!targetDefinition.isUseDepth()) {
            return "use_depth_buffer=true but target is not declared with use_depth=true: " + target;
        }
        return null;
    }

    private static boolean isSceneDepthTarget(String target) {
        return "vulkanpostfx:scene_depth".equals(target)
                || "minecraft:scene_depth".equals(target);
    }

    private static boolean isShadowDepthTarget(String target) {
        return "vulkanpostfx:shadow_depth".equals(target)
                || "minecraft:shadow_depth".equals(target);
    }

    public static boolean isNativeRuntimeRequested() {
        boolean execute = Boolean.getBoolean(SYSTEM_PROPERTY_EXECUTE);
        boolean force = Boolean.getBoolean(SYSTEM_PROPERTY_FORCE);

        if (force) {
            return true;
        }

        if (execute) {
            if (!Boolean.getBoolean(SYSTEM_PROPERTY) && !executeImpliesNativeLogged) {
                executeImpliesNativeLogged = true;
                VulkanPostFX.LOGGER.warn(
                        "[{}] VPFX Native Runtime: -D{}=true was set without -D{}=true. "
                                + "For native activation, execute=true will imply nativeRuntime=true.",
                        VulkanPostFX.MOD_ID,
                        SYSTEM_PROPERTY_EXECUTE,
                        SYSTEM_PROPERTY
                );
            }
            return true;
        }

        String nativeRuntimeRaw = System.getProperty(SYSTEM_PROPERTY);
        if (nativeRuntimeRaw != null) {
            return Boolean.parseBoolean(nativeRuntimeRaw);
        }

        return DEFAULT_NATIVE_RUNTIME_ENABLED;
    }

    public static boolean isExecuteRequested() {
        return Boolean.getBoolean(SYSTEM_PROPERTY_EXECUTE);
    }

    public static boolean isExecuteEnabled() {
        if (PostFxRuntimeState.isNativeRuntimeFallbackActive()
                && PostFxRuntimeState.activeRuntimeBackendUsesPostChain()) {
            return false;
        }

        if (PostFxRuntimeState.isActiveNativeRuntimeBackend()) {
            return true;
        }

        boolean nativeRuntimeEnabled = isNativeRuntimeRequested();
        boolean executeEnabled = Boolean.getBoolean(SYSTEM_PROPERTY_EXECUTE);

        if (!executeEnabled) {
            return false;
        }

        if (!nativeRuntimeEnabled) {
            if (!executeFlagLogged) {
                executeFlagLogged = true;
                VulkanPostFX.LOGGER.warn(
                        "[{}] VPFX Native Runtime: -D{}=true is ignored because native runtime was not requested.",
                        VulkanPostFX.MOD_ID,
                        SYSTEM_PROPERTY_EXECUTE
                );
            }
            return false;
        }

        return true;
    }

    public static void runDryRunCheck(VpfxPackManifest manifest, VpfxGraphDefinition graph) {
        logFlagVisibilityOnce();

        if (!isNativeRuntimeRequested() || !VpfxDiagnosticsConfig.legacyNativeDiagnosticsEnabled()) {
            return;
        }

        VulkanPostFX.LOGGER.info(
                "[{}] VPFX Native Runtime NR-2.3: legacy dry-run/support diagnostics enabled",
                VulkanPostFX.MOD_ID
        );

        VpfxNativeRuntimeSupportResult result = check(graph, manifest);

        if (result.isSupported()) {
            VulkanPostFX.LOGGER.info(
                    "[{}] VPFX Native Runtime NR-2.3: pack '{}' (id={}) is COMPATIBLE with native framegraph backend.",
                    VulkanPostFX.MOD_ID,
                    manifest == null ? "unknown" : manifest.getName(),
                    manifest == null ? "unknown" : manifest.getPackId()
            );
        } else {
            VulkanPostFX.LOGGER.info(
                    "[{}] VPFX Native Runtime NR-2.3: pack '{}' (id={}) is NOT compatible with native framegraph backend: {}.",
                    VulkanPostFX.MOD_ID,
                    manifest == null ? "unknown" : manifest.getName(),
                    manifest == null ? "unknown" : manifest.getPackId(),
                    result.reason()
            );
        }
    }
}
