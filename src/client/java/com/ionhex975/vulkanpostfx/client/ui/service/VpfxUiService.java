package com.ionhex975.vulkanpostfx.client.ui.service;

import com.ionhex975.vulkanpostfx.client.pack.ActiveShaderPackManager;
import com.ionhex975.vulkanpostfx.client.pack.BuiltinShaderPackSource;
import com.ionhex975.vulkanpostfx.client.pack.ShaderPackContainer;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxGraphDefinition;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxNativePackDefinition;
import com.ionhex975.vulkanpostfx.client.reload.VpfxHotReloadManager;
import com.ionhex975.vulkanpostfx.client.runtime.ActivePostEffectBridge;
import com.ionhex975.vulkanpostfx.client.runtime.ActivePostEffectSource;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.VpfxNativeRuntimeSupport;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.VpfxNativeRuntimeSupportResult;
import com.ionhex975.vulkanpostfx.client.runtime.posteffect.ZipPostEffectConfig;
import com.ionhex975.vulkanpostfx.client.runtime.zip.RuntimeZipPackState;
import com.ionhex975.vulkanpostfx.client.state.PostFxRuntimeState;
import com.ionhex975.vulkanpostfx.client.ui.model.VpfxPackListEntry;
import com.ionhex975.vulkanpostfx.client.ui.model.VpfxUiState;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * UI-facing facade for VPFX screens.
 *
 * The UI reads immutable snapshots and submits reload/toggle requests through this class,
 * instead of touching runtime executors directly from Screen render/click handlers.
 */
public final class VpfxUiService {
    private static final VpfxUiService INSTANCE = new VpfxUiService();

    private VpfxUiService() {
    }

    public static VpfxUiService get() {
        return INSTANCE;
    }

    public void refreshRegistry() {
        ActiveShaderPackManager.bootstrap();
    }

    public VpfxUiState snapshot() {
        ShaderPackContainer activePack = ActiveShaderPackManager.getActivePack();
        ActivePostEffectSource source = ActivePostEffectBridge.getActiveSource();
        ZipPostEffectConfig parsed = source == null ? null : source.parsedConfig();

        int passCount = parsed != null ? parsed.passes().size() : graphPassCount(activePack);
        int targetCount = parsed != null ? parsed.targets().size() : graphTargetCount(activePack);

        Identifier externalEffect = PostFxRuntimeState.getActiveExternalPostEffectId();
        Identifier failedEffect = PostFxRuntimeState.getFailedExternalPostEffectId();

        String effectId;
        if (PostFxRuntimeState.isShadowDepthDebugViewEnabled()) {
            effectId = "SHADOW_DEPTH_DEBUG";
        } else if (externalEffect != null) {
            effectId = externalEffect.toString();
        } else {
            effectId = PostFxRuntimeState.getActiveEffectKey();
        }

        String failedEffectId = failedEffect == null ? "" : failedEffect.toString();
        String fallbackReason;
        if (failedEffect != null) {
            fallbackReason = "failed external post effect: " + failedEffect;
        } else if (PostFxRuntimeState.isNativeRuntimeFallbackActive()) {
            fallbackReason = PostFxRuntimeState.getNativeRuntimeFallbackSummary();
        } else {
            fallbackReason = PostFxRuntimeState.skipPostChainReasonForLog();
        }

        Path runtimeRoot = RuntimeZipPackState.getRuntimeRoot();

        return new VpfxUiState(
                activePack == null ? "none" : activePack.manifest().id(),
                activePack == null ? "None" : activePack.manifest().name(),
                activePack == null ? "none" : activePack.sourceId(),
                PostFxRuntimeState.getActiveRuntimeBackendId(),
                PostFxRuntimeState.getActiveRuntimeBackendDisplayName(),
                PostFxRuntimeState.isDebugEffectEnabled(),
                PostFxRuntimeState.isShadowDepthDebugViewEnabled(),
                PostFxRuntimeState.isActiveNativeRuntimeBackend(),
                PostFxRuntimeState.activeRuntimeBackendUsesPostChain(),
                passCount,
                targetCount,
                effectId,
                failedEffectId,
                fallbackReason == null || fallbackReason.isBlank() ? "none" : fallbackReason,
                ActiveShaderPackManager.getActiveConfig().selectionModeForLog(),
                RuntimeZipPackState.getRuntimeNamespace(),
                runtimeRoot == null ? "" : runtimeRoot.toString(),
                buildPackEntries(activePack)
        );
    }

    public void setVpfxEnabled(boolean enabled) {
        PostFxRuntimeState.setDebugEffectEnabled(enabled);
        PostFxRuntimeState.requestReapply();
    }

    public boolean toggleVpfxEnabled() {
        boolean enabled = PostFxRuntimeState.toggleDebugEffectEnabled();
        PostFxRuntimeState.requestReapply();
        return enabled;
    }

    public boolean toggleShadowDepthDebug() {
        boolean enabled = PostFxRuntimeState.toggleShadowDepthDebugView();
        PostFxRuntimeState.requestReapply();
        return enabled;
    }

    public CompletableFuture<Void> reloadCurrent(String reason) {
        return VpfxHotReloadManager.hotReloadCurrentPack(Minecraft.getInstance(), reason);
    }

    public CompletableFuture<Void> selectAuto(String reason) {
        return VpfxHotReloadManager.selectAutoAndReload(Minecraft.getInstance(), reason);
    }

    public CompletableFuture<Void> selectBuiltin(String reason) {
        return VpfxHotReloadManager.selectBuiltinAndReload(Minecraft.getInstance(), reason);
    }

    public CompletableFuture<Void> selectExternal(String packId, String reason) {
        return VpfxHotReloadManager.selectExternalAndReload(Minecraft.getInstance(), packId, reason);
    }

    private static List<VpfxPackListEntry> buildPackEntries(ShaderPackContainer activePack) {
        List<VpfxPackListEntry> entries = new ArrayList<>();
        for (ShaderPackContainer pack : ActiveShaderPackManager.getDiscoveredPacks()) {
            entries.add(toEntry(pack, activePack));
        }
        return List.copyOf(entries);
    }

    private static VpfxPackListEntry toEntry(ShaderPackContainer pack, ShaderPackContainer activePack) {
        boolean selected = ActiveShaderPackManager.isActivePack(pack);
        boolean valid = hasEntryPostEffect(pack);
        int passes = graphPassCount(pack);
        int targets = graphTargetCount(pack);
        String source = sourceLabel(pack);

        boolean nativeCompatible = false;
        String backendHint;
        String diagnostic;

        if (BuiltinShaderPackSource.SOURCE_ID.equals(pack.sourceId())) {
            backendHint = "Builtin";
            diagnostic = valid ? "Builtin debug pack" : "Builtin pack has no entry post effect";
        } else if (!valid) {
            backendHint = "Broken";
            diagnostic = "Missing entry post effect: " + pack.manifest().entryPostEffect();
        } else if (pack.isVpfxNativePack() && pack.vpfxDefinition() != null) {
            VpfxNativePackDefinition definition = pack.vpfxDefinition();
            try {
                VpfxNativeRuntimeSupportResult support = VpfxNativeRuntimeSupport.check(
                        definition.getGraph(),
                        definition.getManifest()
                );
                nativeCompatible = support.isSupported();
                backendHint = nativeCompatible ? "Native" : "PostChain";
                diagnostic = support.reason();
            } catch (Throwable t) {
                backendHint = "Broken";
                diagnostic = "Native check failed: " + t.getClass().getSimpleName();
            }
        } else {
            backendHint = "PostChain";
            diagnostic = "Legacy or non-native pack";
        }

        return new VpfxPackListEntry(
                pack.manifest().id(),
                pack.manifest().name(),
                source,
                selected,
                valid,
                nativeCompatible,
                backendHint,
                passes,
                targets,
                diagnostic
        );
    }

    private static boolean hasEntryPostEffect(ShaderPackContainer pack) {
        if (pack == null) {
            return false;
        }
        String entryPostEffect = pack.manifest().entryPostEffect();
        return entryPostEffect != null
                && !entryPostEffect.isBlank()
                && pack.resourceIndex().exists(entryPostEffect);
    }

    private static int graphPassCount(ShaderPackContainer pack) {
        VpfxGraphDefinition graph = graph(pack);
        return graph == null ? 0 : graph.getPasses().size();
    }

    private static int graphTargetCount(ShaderPackContainer pack) {
        VpfxGraphDefinition graph = graph(pack);
        return graph == null ? 0 : graph.getTargets().size();
    }

    private static VpfxGraphDefinition graph(ShaderPackContainer pack) {
        if (pack == null || !pack.isVpfxNativePack() || pack.vpfxDefinition() == null) {
            return null;
        }
        return pack.vpfxDefinition().getGraph();
    }

    private static String sourceLabel(ShaderPackContainer pack) {
        if (pack == null) {
            return "none";
        }
        if (BuiltinShaderPackSource.SOURCE_ID.equals(pack.sourceId())) {
            return "builtin";
        }
        if ("zip".equals(pack.sourceId())) {
            return "external ZIP";
        }
        return pack.sourceId();
    }
}
