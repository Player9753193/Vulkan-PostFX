package com.ionhex975.vulkanpostfx.client.runtime;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.diagnostics.VpfxFailureDiagnostics;
import com.ionhex975.vulkanpostfx.client.effect.PostFxEffectRegistry;
import com.ionhex975.vulkanpostfx.client.pack.ActiveShaderPackManager;
import com.ionhex975.vulkanpostfx.client.pack.ShaderPackContainer;
import com.ionhex975.vulkanpostfx.client.pack.ZipShaderPackReader;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxNativePackDefinition;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.VpfxBackendSelectionResult;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.VpfxNativeFullscreenDryRun;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.VpfxNativeRuntimeSupport;
import com.ionhex975.vulkanpostfx.client.runtime.posteffect.ZipPostEffectConfig;
import com.ionhex975.vulkanpostfx.client.runtime.posteffect.ZipPostEffectParser;
import com.ionhex975.vulkanpostfx.client.runtime.posteffect.ZipShaderReferenceValidationResult;
import com.ionhex975.vulkanpostfx.client.runtime.posteffect.ZipShaderReferenceValidator;
import com.ionhex975.vulkanpostfx.client.runtime.texture.VpfxRuntimeTextureBootstrap;
import com.ionhex975.vulkanpostfx.client.runtime.texture.VpfxRuntimeTextureRegistry;
import com.ionhex975.vulkanpostfx.client.runtime.vpfx.VpfxPostChainBackend;
import com.ionhex975.vulkanpostfx.client.runtime.vpfx.VpfxRuntimeBackend;
import com.ionhex975.vulkanpostfx.client.runtime.zip.RuntimeZipPackMaterializationResult;
import com.ionhex975.vulkanpostfx.client.runtime.zip.RuntimeZipPackState;
import com.ionhex975.vulkanpostfx.client.state.PostFxRuntimeState;
import net.minecraft.client.Minecraft;

public final class ActivePostEffectBridge {
    private static ActivePostEffectSource activeSource = ActivePostEffectSource.NONE;

    private ActivePostEffectBridge() {
    }

    public static void refreshFromActivePack() {
        // Native fallback is intentionally sticky only within a single active runtime effect.
        // A pack refresh, pack switch, or re-materialization must start from a clean backend
        // decision; otherwise a previous pack's native failure can incorrectly force every
        // later pack through minecraft_postchain until the user restarts the game.
        PostFxRuntimeState.clearNativeRuntimeFallback("active shader pack refresh started");

        ShaderPackContainer activePack = ActiveShaderPackManager.getActivePack();
        if (activePack == null) {
            activeSource = ActivePostEffectSource.NONE;
            RuntimeZipPackState.clear();
            VpfxRuntimeTextureRegistry.clear();
            PostFxRuntimeState.clearActiveExternalPostEffectId();
            PostFxRuntimeState.setActiveEffectKey(PostFxEffectRegistry.DEBUG_INVERT);
            PostFxRuntimeState.setActiveRuntimeBackend(new VpfxPostChainBackend());

            VulkanPostFX.LOGGER.warn(
                    "[{}] No active shader pack; active post effect source cleared",
                    VulkanPostFX.MOD_ID
            );
            return;
        }

        String entryPostEffect = activePack.manifest().entryPostEffect();
        if (entryPostEffect == null || entryPostEffect.isBlank()) {
            activeSource = ActivePostEffectSource.NONE;
            RuntimeZipPackState.clear();
            VpfxRuntimeTextureRegistry.clear();
            PostFxRuntimeState.clearActiveExternalPostEffectId();
            PostFxRuntimeState.setActiveEffectKey(PostFxEffectRegistry.DEBUG_INVERT);
            PostFxRuntimeState.setActiveRuntimeBackend(new VpfxPostChainBackend());

            VulkanPostFX.LOGGER.warn(
                    "[{}] Active shader pack '{}' does not declare entry_post_effect",
                    VulkanPostFX.MOD_ID,
                    activePack.manifest().name()
            );
            return;
        }

        if ("builtin".equals(activePack.sourceId())) {
            activeSource = new ActivePostEffectSource(
                    "builtin",
                    entryPostEffect,
                    "",
                    null,
                    null
            );

            RuntimeZipPackState.clear();
            VpfxRuntimeTextureRegistry.clear();
            PostFxRuntimeState.clearActiveExternalPostEffectId();
            PostFxRuntimeState.setActiveEffectKey(ActiveShaderPackManager.getActiveEffectKey());
            PostFxRuntimeState.setActiveRuntimeBackend(new VpfxPostChainBackend());

            VulkanPostFX.LOGGER.info(
                    "[{}] Active post effect source prepared from builtin pack: {}, resolvedBuiltinEffectKey={}",
                    VulkanPostFX.MOD_ID,
                    entryPostEffect,
                    PostFxRuntimeState.getActiveEffectKey()
            );
            return;
        }

        if ("zip".equals(activePack.sourceId())) {
            try {
                String rawJson = ZipShaderPackReader.readText(activePack.sourcePath(), entryPostEffect);
                ZipPostEffectConfig parsedConfig = ZipPostEffectParser.parse(rawJson);
                ZipShaderReferenceValidationResult validationResult =
                        ZipShaderReferenceValidator.validate(activePack, parsedConfig);

                if (!validationResult.isValid()) {
                    activeSource = ActivePostEffectSource.NONE;
                    RuntimeZipPackState.clear();
                    VpfxRuntimeTextureRegistry.clear();
                    PostFxRuntimeState.clearActiveExternalPostEffectId();
                    PostFxRuntimeState.setActiveEffectKey(PostFxEffectRegistry.DEBUG_INVERT);
                    PostFxRuntimeState.setActiveRuntimeBackend(new VpfxPostChainBackend());

                    VulkanPostFX.LOGGER.error(
                            "[{}] Active ZIP post effect source failed shader validation: checked={}, missing={}",
                            VulkanPostFX.MOD_ID,
                            validationResult.checkedCount(),
                            validationResult.missingReferences()
                    );
                    return;
                }

                VpfxBackendSelectionResult backendSelection = selectRuntimeBackend(activePack);
                VpfxRuntimeBackend backend = backendSelection.backend();
                PostFxRuntimeState.setActiveRuntimeBackend(backend);

                String nativeFallbackStage = backendSelection.fallbackUsed()
                        ? backendSelection.fallbackStage()
                        : null;
                String nativeFallbackReason = backendSelection.fallbackUsed()
                        ? backendSelection.fallbackReason()
                        : null;

                /*
                 * Materialize before any native shader/pipeline preflight.
                 *
                 * Native RenderPipeline lookup is namespace based. The previous implementation
                 * ran VpfxNativeFullscreenDryRun before RuntimeZipPackState.apply(materialized),
                 * so switching from one ZIP pack to another could make the dry-run resolve the
                 * new pack's shader ids through the old active runtime namespace. That produced
                 * errors like:
                 *   original = vpfx_fake_held_light_glow:composite/final
                 *   runtime  = vpfxzip_vpfx_bsl_tone_showcase:composite/final
                 *
                 * Write the new runtime pack first, apply its runtime namespace, then run native
                 * diagnostics against the current materialized state.
                 */
                RuntimeZipPackMaterializationResult materialized = backend.materialize(
                        activePack,
                        Minecraft.getInstance().gameDirectory.toPath()
                );

                RuntimeZipPackState.apply(materialized);

                // 注册 runtime texture manifest，供后续 pass/sampler 绑定使用
                VpfxRuntimeTextureRegistry.clear();
                VpfxRuntimeTextureBootstrap.registerRuntimeTextureManifest(
                        materialized.runtimeTextureManifestPath()
                );

                if (activePack.isVpfxNativePack()
                        && activePack.vpfxDefinition() != null
                        && backend.capabilities().nativeRuntime()) {
                    VpfxNativePackDefinition vpfxDef = activePack.vpfxDefinition();
                    try {
                        VpfxNativeRuntimeSupport.runDryRunCheck(vpfxDef.getManifest(), vpfxDef.getGraph());
                        VpfxNativeFullscreenDryRun.run(Minecraft.getInstance(), vpfxDef.getManifest(), vpfxDef.getGraph());
                    } catch (Throwable t) {
                        nativeFallbackStage = VpfxBackendSelectionResult.STAGE_PREPARE;
                        nativeFallbackReason = "native backend preparation failed after materialization: "
                                + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
                        backend = new VpfxPostChainBackend();
                        PostFxRuntimeState.setActiveRuntimeBackend(backend);

                        VulkanPostFX.LOGGER.warn(
                                "[{}] VPFX native backend preparation failed after runtime namespace activation. Falling back to minecraft_postchain without marking the pack failed: pack='{}', runtimeNamespace={}, stage={}, reason={}",
                                VulkanPostFX.MOD_ID,
                                activePack.manifest().name(),
                                materialized.runtimeNamespace(),
                                nativeFallbackStage,
                                nativeFallbackReason,
                                t
                        );
                    }
                }

                PostFxRuntimeState.setActiveExternalPostEffectId(materialized.externalPostEffectId());
                PostFxRuntimeState.setActiveEffectKey("external_zip");
                if (nativeFallbackReason != null) {
                    PostFxRuntimeState.markNativeRuntimeFallback(
                            materialized.externalPostEffectId(),
                            nativeFallbackStage,
                            nativeFallbackReason
                    );
                }

                activeSource = new ActivePostEffectSource(
                        "zip",
                        activePack.sourcePath() + "!/" + entryPostEffect,
                        rawJson,
                        parsedConfig,
                        validationResult
                );

                VulkanPostFX.LOGGER.info(
                        "[{}] Active post effect source loaded from zip: {} ({} chars, {} targets, {} passes, checkedShaders={}, runtimeNamespace={}, externalPostEffectId={}, runtimeTextureManifest={}, backend={}, backendCaps={})",
                        VulkanPostFX.MOD_ID,
                        activeSource.displayPath(),
                        rawJson.length(),
                        parsedConfig.targets().size(),
                        parsedConfig.passes().size(),
                        validationResult.checkedCount(),
                        materialized.runtimeNamespace(),
                        materialized.externalPostEffectId(),
                        materialized.runtimeTextureManifestPath(),
                        backend.id(),
                        backend.capabilities()
                );
                return;
            } catch (Exception e) {
                activeSource = ActivePostEffectSource.NONE;
                RuntimeZipPackState.clear();
                VpfxRuntimeTextureRegistry.clear();
                PostFxRuntimeState.clearActiveExternalPostEffectId();
                PostFxRuntimeState.setActiveEffectKey(PostFxEffectRegistry.DEBUG_INVERT);
                PostFxRuntimeState.setActiveRuntimeBackend(new VpfxPostChainBackend());
                VpfxFailureDiagnostics.write("materialize/activate", e, activePack.manifest().name());

                VulkanPostFX.LOGGER.error(
                        "[{}] Failed to load active ZIP post effect source from '{}'",
                        VulkanPostFX.MOD_ID,
                        entryPostEffect,
                        e
                );
                return;
            }
        }

        activeSource = ActivePostEffectSource.NONE;
        RuntimeZipPackState.clear();
        VpfxRuntimeTextureRegistry.clear();
        PostFxRuntimeState.clearActiveExternalPostEffectId();
        PostFxRuntimeState.setActiveEffectKey(PostFxEffectRegistry.DEBUG_INVERT);
        PostFxRuntimeState.setActiveRuntimeBackend(new VpfxPostChainBackend());

        VulkanPostFX.LOGGER.warn(
                "[{}] Unsupported shader pack source '{}'; active post effect source cleared",
                VulkanPostFX.MOD_ID,
                activePack.sourceId()
        );
    }

    private static VpfxBackendSelectionResult selectRuntimeBackend(ShaderPackContainer activePack) {
        if (activePack != null && activePack.isVpfxNativePack() && activePack.vpfxDefinition() != null) {
            VpfxNativePackDefinition vpfxDef = activePack.vpfxDefinition();
            return VpfxNativeRuntimeSupport.selectBackendResult(
                    vpfxDef.getManifest(),
                    vpfxDef.getGraph()
            );
        }

        return VpfxBackendSelectionResult.postChainSelected(
                new VpfxPostChainBackend(),
                "pack is not a VPFX native graph pack; selecting minecraft_postchain"
        );
    }

    public static ActivePostEffectSource getActiveSource() {
        return activeSource;
    }
}