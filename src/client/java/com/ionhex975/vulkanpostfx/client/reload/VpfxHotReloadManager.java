package com.ionhex975.vulkanpostfx.client.reload;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.config.ActiveShaderPackConfig;
import com.ionhex975.vulkanpostfx.client.pack.ActiveShaderPackManager;
import com.ionhex975.vulkanpostfx.client.pack.ShaderPackContainer;
import com.ionhex975.vulkanpostfx.client.runtime.ActivePostEffectBridge;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.VpfxNativeFullscreenExecutor;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.VpfxNativeUserPipelineCache;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.VpfxNativeUserShaderDryRun;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.VpfxNativeTransientTargetDryRun;
import com.ionhex975.vulkanpostfx.client.runtime.zip.RuntimeZipPackLocator;
import com.ionhex975.vulkanpostfx.client.runtime.zip.RuntimeZipPackState;
import com.ionhex975.vulkanpostfx.client.state.PostFxRuntimeState;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;

/**
 * VPFX hot reload / pack activation entry.
 *
 * 这版只处理“加载/选择/重载入口”安全性，不改任何渲染执行路径：
 * - 不修改 PostFxHookBridge；
 * - 不修改 native framegraph executor；
 * - 不在普通 safe reload 中关闭当前 VPFX 效果。
 *
 * 设计目标：
 * - native direct backend：世界内允许 soft activation，不调用 Minecraft#reloadResourcePacks；
 * - minecraft_postchain backend：世界内不做 hard reload，避免资源加载失败后被 Minecraft 踢出世界；
 * - 如果选择新包失败：回滚 config 并重新准备原来的活动包，尽量保持当前画面效果；
 * - 如需在开发环境强制允许世界内 hard reload，可使用 -Dvulkanpostfx.vpfx.allowInWorldHardReload=true。
 */
public final class VpfxHotReloadManager {
    private static final String ZIP_SOURCE_ID = "zip";
    private static final String ALLOW_IN_WORLD_HARD_RELOAD_PROPERTY = "vulkanpostfx.vpfx.allowInWorldHardReload";

    private VpfxHotReloadManager() {
    }

    public static CompletableFuture<Void> selectAutoAndReload(Minecraft minecraft, String reason) {
        return selectAndReload(
                minecraft,
                new ActiveShaderPackConfig(ActiveShaderPackConfig.SELECTOR_AUTO, true),
                true,
                reason
        );
    }

    public static CompletableFuture<Void> selectBuiltinAndReload(Minecraft minecraft, String reason) {
        return selectAndReload(
                minecraft,
                ActiveShaderPackConfig.forcedBuiltinConfig(),
                false,
                reason
        );
    }

    public static CompletableFuture<Void> selectExternalAndReload(Minecraft minecraft, String packId, String reason) {
        return selectAndReload(
                minecraft,
                new ActiveShaderPackConfig(packId, false),
                true,
                reason
        );
    }

    public static CompletableFuture<Void> selectAndReload(
            Minecraft minecraft,
            ActiveShaderPackConfig config,
            boolean enableShaderAfterReload,
            String reason
    ) {
        Minecraft client = minecraft != null ? minecraft : Minecraft.getInstance();
        ActiveShaderPackConfig previousConfig = ActiveShaderPackManager.getActiveConfig();
        boolean previousEnabled = PostFxRuntimeState.isDebugEffectEnabled();

        try {
            ActiveShaderPackManager.saveConfig(config);
        } catch (IOException e) {
            VulkanPostFX.LOGGER.error(
                    "[{}] Failed to save VPFX shader pack selection before reload: active_pack_id='{}'",
                    VulkanPostFX.MOD_ID,
                    config.activePackId(),
                    e
            );
            return CompletableFuture.failedFuture(e);
        }

        CompletableFuture<Void> reloadFuture = hotReloadCurrentPack(client, enableShaderAfterReload, reason);
        CompletableFuture<Void> guardedFuture = new CompletableFuture<>();

        reloadFuture.whenComplete((ignored, throwable) -> {
            if (throwable == null) {
                guardedFuture.complete(null);
                return;
            }

            client.execute(() -> {
                try {
                    ActiveShaderPackManager.saveConfig(previousConfig);
                    prepareActivePackBeforeMinecraftResourceReload("rollback-after-failed-select:" + reason);

                    ShaderPackContainer restoredPack = ActiveShaderPackManager.getActivePack();
                    if (ActiveShaderPackManager.isBuiltinPack(restoredPack)) {
                        PostFxRuntimeState.setDebugEffectEnabled(false);
                    } else {
                        PostFxRuntimeState.setDebugEffectEnabled(previousEnabled);
                    }
                    PostFxRuntimeState.requestReapply();

                    VulkanPostFX.LOGGER.warn(
                            "[{}] VPFX selection reload failed; restored previous config active_pack_id='{}', mode={}, restoredPack='{}', shaderEnabled={}",
                            VulkanPostFX.MOD_ID,
                            previousConfig.activePackId(),
                            previousConfig.selectionModeForLog(),
                            restoredPack == null ? "none" : restoredPack.manifest().name(),
                            PostFxRuntimeState.isDebugEffectEnabled()
                    );
                } catch (Throwable rollbackError) {
                    VulkanPostFX.LOGGER.error(
                            "[{}] VPFX selection reload failed and previous config rollback also failed",
                            VulkanPostFX.MOD_ID,
                            rollbackError
                    );
                }

                guardedFuture.completeExceptionally(throwable);
            });
        });

        return guardedFuture;
    }

    public static CompletableFuture<Void> hotReloadCurrentPack(Minecraft minecraft, String reason) {
        return hotReloadCurrentPack(minecraft, PostFxRuntimeState.isDebugEffectEnabled(), reason);
    }

    public static CompletableFuture<Void> hotReloadCurrentPack(
            Minecraft minecraft,
            boolean enableShaderAfterReload,
            String reason
    ) {
        Minecraft client = minecraft != null ? minecraft : Minecraft.getInstance();
        CompletableFuture<Void> result = new CompletableFuture<>();

        client.execute(() -> {
            try {
                VulkanPostFX.LOGGER.info(
                        "[{}] VPFX safe-load reload requested: reason={}, enableShaderAfterReload={}",
                        VulkanPostFX.MOD_ID,
                        reason,
                        enableShaderAfterReload
                );

                prepareActivePackBeforeMinecraftResourceReload("safe-load:" + reason);

                ShaderPackContainer activePack = ActiveShaderPackManager.getActivePack();
                boolean effectiveEnableAfterReload = resolveEffectiveEnableAfterReload(activePack, enableShaderAfterReload, reason);
                boolean postChainHardReloadRequired = requiresMinecraftResourceReload(activePack)
                        && PostFxRuntimeState.activeRuntimeBackendUsesPostChain();

                if (!postChainHardReloadRequired) {
                    applyPostReloadEnableState(effectiveEnableAfterReload);

                    VulkanPostFX.LOGGER.info(
                            "[{}] VPFX safe-load reload completed without Minecraft resource reload: activePack='{}', source='{}', backend={}, shaderEnabled={}",
                            VulkanPostFX.MOD_ID,
                            activePack == null ? "none" : activePack.manifest().name(),
                            activePack == null ? "none" : activePack.sourceId(),
                            PostFxRuntimeState.getActiveRuntimeBackendId(),
                            PostFxRuntimeState.isDebugEffectEnabled()
                    );
                    result.complete(null);
                    return;
                }

                if (isWorldLoaded(client) && !isInWorldHardReloadAllowed()) {
                    VulkanPostFX.LOGGER.warn(
                            "[{}] VPFX hard resource reload blocked in-world: activePack='{}', source='{}', backend={}, runtimeRoot={}, externalPostEffectId={}. "
                                    + "Keeping the pack active and deferring Minecraft PostChain resource reload to avoid a resource-load disconnect. "
                                    + "Native execution may still run; if PostChain resources are not loaded yet, rendering will temporarily stay vanilla. "
                                    + "Leave the world or return to the title screen before reloading this PostChain fallback. "
                                    + "Unsafe override: -D{}=true",
                            VulkanPostFX.MOD_ID,
                            activePack == null ? "none" : activePack.manifest().name(),
                            activePack == null ? "none" : activePack.sourceId(),
                            PostFxRuntimeState.getActiveRuntimeBackendId(),
                            RuntimeZipPackState.getRuntimeRoot(),
                            RuntimeZipPackState.getExternalPostEffectId(),
                            ALLOW_IN_WORLD_HARD_RELOAD_PROPERTY
                    );
                    PostFxRuntimeState.requestReapply();
                    result.complete(null);
                    return;
                }

                if (!RuntimeZipPackLocator.isMaterializedPackReady()) {
                    IllegalStateException error = new IllegalStateException(
                            "Selected VPFX ZIP pack did not produce a ready runtime resource pack"
                    );
                    VulkanPostFX.LOGGER.error(
                            "[{}] VPFX hard reload aborted before Minecraft resource reload: runtime pack is not ready. activePack='{}', runtimeRoot={}, runtimeNamespace={}",
                            VulkanPostFX.MOD_ID,
                            activePack == null ? "none" : activePack.manifest().name(),
                            RuntimeZipPackState.getRuntimeRoot(),
                            RuntimeZipPackState.getRuntimeNamespace()
                    );
                    result.completeExceptionally(error);
                    return;
                }

                // hard reload 分支会短暂关闭 VPFX；只允许在非世界内执行，避免资源重载失败导致断开世界。
                boolean previousEnabled = PostFxRuntimeState.isDebugEffectEnabled();
                PostFxRuntimeState.setDebugEffectEnabled(false);
                PostFxRuntimeState.requestReapply();
                RuntimeZipPackState.enableResourcePackInjection();

                VulkanPostFX.LOGGER.info(
                        "[{}] Starting explicit Minecraft resource reload for VPFX runtime pack: activePack='{}', source='{}', runtimeNamespace={}, runtimeRoot={}, externalPostEffectId={}",
                        VulkanPostFX.MOD_ID,
                        activePack.manifest().name(),
                        activePack.sourceId(),
                        RuntimeZipPackState.getRuntimeNamespace(),
                        RuntimeZipPackState.getRuntimeRoot(),
                        RuntimeZipPackState.getExternalPostEffectId()
                );

                CompletableFuture<Void> reloadFuture = client.reloadResourcePacks();
                reloadFuture.whenComplete((ignored, throwable) -> client.execute(() -> {
                    if (throwable != null) {
                        PostFxRuntimeState.setDebugEffectEnabled(false);
                        PostFxRuntimeState.requestReapply();

                        VulkanPostFX.LOGGER.error(
                                "[{}] VPFX Minecraft resource reload failed. VPFX shader was left disabled to avoid a reload/crash loop; check latest.log for post_effect/shader errors. previousEnabled={}",
                                VulkanPostFX.MOD_ID,
                                previousEnabled,
                                throwable
                        );
                        result.completeExceptionally(throwable);
                        return;
                    }

                    PostFxRuntimeState.clearFailedExternalPostEffectId();
                    applyPostReloadEnableState(effectiveEnableAfterReload);

                    ShaderPackContainer reloadedPack = ActiveShaderPackManager.getActivePack();
                    VulkanPostFX.LOGGER.info(
                            "[{}] VPFX Minecraft resource reload completed: activePack='{}', source='{}', externalPostEffectId={}, shaderEnabled={}",
                            VulkanPostFX.MOD_ID,
                            reloadedPack == null ? "none" : reloadedPack.manifest().name(),
                            reloadedPack == null ? "none" : reloadedPack.sourceId(),
                            RuntimeZipPackState.getExternalPostEffectId(),
                            PostFxRuntimeState.isDebugEffectEnabled()
                    );
                    result.complete(null);
                }));
            } catch (Throwable t) {
                PostFxRuntimeState.requestReapply();

                VulkanPostFX.LOGGER.error(
                        "[{}] VPFX safe-load reload failed before Minecraft resource reload; current render state was left unchanged",
                        VulkanPostFX.MOD_ID,
                        t
                );
                result.completeExceptionally(t);
            }
        });

        return result;
    }

    /**
     * Prepare the selected runtime pack. Despite the historical method name, this method itself
     * must not start Minecraft's resource reload.
     */
    public static void prepareActivePackBeforeMinecraftResourceReload(String reason) {
        ActiveShaderPackManager.bootstrap();

        VpfxNativeUserPipelineCache.clear();
        VpfxNativeFullscreenExecutor.invalidatePipeline();

        PostFxRuntimeState.clearPendingNativeRuntimeChecks();
        PostFxRuntimeState.clearFailedExternalPostEffectId();
        VpfxNativeUserShaderDryRun.clearPendingPipelineCreate();
        VpfxNativeTransientTargetDryRun.resetNr2aPlanLog();

        ActivePostEffectBridge.refreshFromActivePack();
        PostFxRuntimeState.requestReapply();

        ShaderPackContainer activePack = ActiveShaderPackManager.getActivePack();
        VulkanPostFX.LOGGER.info(
                "[{}] VPFX safe-load preparation completed: reason={}, activePack='{}', source='{}', backend={}, runtimePackActive={}, runtimePackReady={}, runtimeNamespace='{}', runtimeRoot={}, externalPostEffectId={}, discovered={}, configMode={}",
                VulkanPostFX.MOD_ID,
                reason,
                activePack == null ? "none" : activePack.manifest().name(),
                activePack == null ? "none" : activePack.sourceId(),
                PostFxRuntimeState.getActiveRuntimeBackendId(),
                RuntimeZipPackState.isActive(),
                RuntimeZipPackLocator.isMaterializedPackReady(),
                RuntimeZipPackState.getRuntimeNamespace(),
                RuntimeZipPackState.getRuntimeRoot(),
                RuntimeZipPackState.getExternalPostEffectId(),
                ActiveShaderPackManager.getDiscoveredPacks().size(),
                ActiveShaderPackManager.getActiveConfig().selectionModeForLog()
        );
    }

    /**
     * Backward-compatible name for older call sites. Do not call this from PostFxReloadHooks.
     */
    public static void prepareActivePackForResourceReload(String reason) {
        prepareActivePackBeforeMinecraftResourceReload(reason);
    }

    /**
     * Backward-compatible name from the aborted soft-reload patch.
     */
    public static void prepareActivePackForSoftReload(String reason) {
        prepareActivePackBeforeMinecraftResourceReload(reason);
    }

    private static void applyPostReloadEnableState(boolean enableShaderAfterReload) {
        PostFxRuntimeState.setDebugEffectEnabled(enableShaderAfterReload);
        PostFxRuntimeState.requestReapply();
    }

    private static boolean resolveEffectiveEnableAfterReload(
            ShaderPackContainer activePack,
            boolean requestedEnable,
            String reason
    ) {
        if (!requestedEnable) {
            return false;
        }

        if (ActiveShaderPackManager.isBuiltinPack(activePack) && isSelectionReloadReason(reason)) {
            VulkanPostFX.LOGGER.info(
                    "[{}] VPFX selection resolved to builtin pack; shader will stay disabled to avoid locking the user into debug_invert",
                    VulkanPostFX.MOD_ID
            );
            return false;
        }

        return true;
    }

    private static boolean isSelectionReloadReason(String reason) {
        if (reason == null) {
            return false;
        }
        return reason.contains("select-auto")
                || reason.contains("select-builtin")
                || reason.contains("select-external")
                || reason.contains("/vpfx reload auto")
                || reason.contains("/vpfx reload builtin");
    }

    private static boolean requiresMinecraftResourceReload(ShaderPackContainer activePack) {
        return isExternalZipPack(activePack) && RuntimeZipPackState.isActive();
    }

    private static boolean isExternalZipPack(ShaderPackContainer activePack) {
        return activePack != null && ZIP_SOURCE_ID.equals(activePack.sourceId());
    }

    private static boolean isInWorldHardReloadAllowed() {
        return Boolean.parseBoolean(System.getProperty(ALLOW_IN_WORLD_HARD_RELOAD_PROPERTY, "false"));
    }

    private static boolean isWorldLoaded(Minecraft client) {
        if (client == null) {
            return false;
        }

        for (Field field : Minecraft.class.getDeclaredFields()) {
            Class<?> type = field.getType();
            String typeName = type.getName();
            String simpleName = type.getSimpleName();
            if (!"net.minecraft.client.multiplayer.ClientLevel".equals(typeName)
                    && !"ClientLevel".equals(simpleName)) {
                continue;
            }

            try {
                field.setAccessible(true);
                return field.get(client) != null;
            } catch (ReflectiveOperationException e) {
                VulkanPostFX.LOGGER.warn(
                        "[{}] Failed to inspect Minecraft current level field '{}' for VPFX reload policy; assuming no world is loaded",
                        VulkanPostFX.MOD_ID,
                        field.getName(),
                        e
                );
                return false;
            }
        }

        return false;
    }
}
