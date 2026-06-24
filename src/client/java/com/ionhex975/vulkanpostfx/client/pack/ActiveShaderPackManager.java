package com.ionhex975.vulkanpostfx.client.pack;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.config.ActiveShaderPackConfig;
import com.ionhex975.vulkanpostfx.client.effect.PostFxEffectRegistry;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.VpfxNativeRuntimeSupport;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.VpfxNativeRuntimeSupportResult;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 当前活动光影包管理器。
 *
 * 当前阶段职责：
 * - 注册 pack source
 * - 发现可用包
 * - 读取 active_pack_id 配置
 * - 默认自动选择 shaderpacks 目录中的第一个“适合自动启用”的外部 VPFX ZIP 包
 * - 在没有可自动启用外部包或配置强制 builtin 时回退到 builtin
 * - 管理当前活动包的 entryPostEffect 元数据
 *
 * 注意：
 * “能解析 pack.json”不等于“适合普通用户自动启用”。
 * Auto 模式会避开明显的错误样例 / negative test pack。
 */
public final class ActiveShaderPackManager {
    private static final String SHADER_PACK_DIRECTORY_NAME = "shaderpacks";
    private static final String CONFIG_DIRECTORY_NAME = "config";
    private static final String CONFIG_FILE_NAME = "vulkanpostfx.json";

    private static final String[] AUTO_EXCLUDED_MARKERS = {
            "error",
            "broken",
            "negative",
            "invalid",
            "missing",
            "failure",
            "fail_",
            "fail-",
            "no_auto",
            "no-auto",
            "diagnostic",
            "testpack"
    };

    private static final List<ShaderPackSource> SOURCES = new ArrayList<>();

    private static ShaderPackContainer activePack;
    private static List<ShaderPackContainer> discoveredPacks = List.of();
    private static List<ShaderPackScanIssue> discoveredPackIssues = List.of();
    private static ActiveShaderPackConfig activeConfig = ActiveShaderPackConfig.defaultConfig();

    private ActiveShaderPackManager() {
    }

    public static void bootstrap() {
        activePack = null;
        discoveredPacks = List.of();
        discoveredPackIssues = List.of();

        SOURCES.clear();

        Path shaderPackDirectory = getShaderPackDirectory();
        Path configPath = getConfigPath();

        SOURCES.add(new BuiltinShaderPackSource());
        SOURCES.add(new ZipShaderPackSource(shaderPackDirectory));

        List<ShaderPackContainer> discovered = new ArrayList<>();
        List<ShaderPackScanIssue> issues = new ArrayList<>();
        for (ShaderPackSource source : SOURCES) {
            discovered.addAll(source.discoverPacks());
            issues.addAll(source.getLastScanIssues());
        }

        discoveredPacks = List.copyOf(discovered);
        discoveredPackIssues = List.copyOf(issues);
        activeConfig = ActiveShaderPackConfig.loadOrCreate(configPath);

        ShaderPackContainer builtinPack = findBuiltinPack(discovered);
        List<ShaderPackContainer> externalPacks = findExternalPacks(discovered);
        activeConfig = migrateLegacyBuiltinConfigIfNeeded(activeConfig, externalPacks, configPath);

        activePack = selectActivePack(activeConfig, builtinPack, externalPacks);

        if (activePack == null) {
            VulkanPostFX.LOGGER.warn(
                    "[{}] No shader packs discovered at all. scannedSources={}, externalCandidates={}, configMode={}",
                    VulkanPostFX.MOD_ID,
                    SOURCES.size(),
                    externalPacks.size(),
                    activeConfig.selectionModeForLog()
            );
            logDiscoveredPacks();
            return;
        }

        VulkanPostFX.LOGGER.info(
                "[{}] Active shader pack set to '{}' from source '{}', id={}, entryEffectKey={}, entryPostEffect={}, entryPostEffectExists={}, discoveredTotal={}, discoveredInvalid={}, discoveredExternal={}, autoSelectableExternal={}, configMode={}",
                VulkanPostFX.MOD_ID,
                activePack.manifest().name(),
                activePack.sourceId(),
                activePack.manifest().id(),
                activePack.manifest().entryEffectKey(),
                activePack.manifest().entryPostEffect(),
                hasActiveEntryPostEffect(),
                discoveredPacks.size(),
                discoveredPackIssues.size(),
                externalPacks.size(),
                getAutoSelectableExternalPacks().size(),
                activeConfig.selectionModeForLog()
        );

        logDiscoveredPacks();
    }

    public static ShaderPackContainer getActivePack() {
        return activePack;
    }

    public static List<ShaderPackContainer> getDiscoveredPacks() {
        return discoveredPacks;
    }

    public static List<ShaderPackScanIssue> getDiscoveredPackIssues() {
        return discoveredPackIssues;
    }

    public static List<ShaderPackContainer> getExternalPacks() {
        return findExternalPacks(discoveredPacks);
    }

    public static List<ShaderPackContainer> getAutoSelectableExternalPacks() {
        return getExternalPacks().stream()
                .filter(ActiveShaderPackManager::isAutoSelectableExternalPack)
                .toList();
    }

    public static ActiveShaderPackConfig getActiveConfig() {
        return activeConfig;
    }

    public static Path getShaderPackDirectory() {
        return getRunDirectory().resolve(SHADER_PACK_DIRECTORY_NAME);
    }

    public static Path getConfigPath() {
        return getRunDirectory().resolve(CONFIG_DIRECTORY_NAME).resolve(CONFIG_FILE_NAME);
    }

    public static void saveConfig(ActiveShaderPackConfig config) throws IOException {
        ActiveShaderPackConfig.save(getConfigPath(), config);
        activeConfig = config;

        VulkanPostFX.LOGGER.info(
                "[{}] Saved shader pack config: active_pack_id='{}', auto_select_external_pack={}, force_builtin_pack={}, mode={}",
                VulkanPostFX.MOD_ID,
                config.activePackId(),
                config.autoSelectExternalPack(),
                config.forceBuiltinPack(),
                config.selectionModeForLog()
        );
    }

    public static boolean isBuiltinPack(ShaderPackContainer pack) {
        return pack != null && BuiltinShaderPackSource.SOURCE_ID.equals(pack.sourceId());
    }

    public static boolean isActivePack(ShaderPackContainer pack) {
        if (pack == null || activePack == null) {
            return false;
        }
        return pack.sourceId().equals(activePack.sourceId())
                && pack.manifest().id().equals(activePack.manifest().id());
    }

    public static boolean isAutoSelectableExternalPack(ShaderPackContainer pack) {
        if (pack == null || isBuiltinPack(pack)) {
            return false;
        }

        if (!hasEntryPostEffect(pack)) {
            return false;
        }

        return !looksLikeDiagnosticOrBrokenPack(pack);
    }

    public static String getActiveEffectKey() {
        if (activePack == null) {
            return PostFxEffectRegistry.DEBUG_INVERT;
        }

        String entryEffectKey = activePack.manifest().entryEffectKey();
        if (entryEffectKey == null || entryEffectKey.isBlank()) {
            return PostFxEffectRegistry.DEBUG_INVERT;
        }

        return entryEffectKey;
    }

    public static String getActiveEntryPostEffect() {
        if (activePack == null) {
            return "";
        }

        return activePack.manifest().entryPostEffect();
    }

    public static boolean hasActiveEntryPostEffect() {
        return hasEntryPostEffect(activePack);
    }

    private static Path getRunDirectory() {
        return Minecraft.getInstance().gameDirectory.toPath();
    }


    private static ActiveShaderPackConfig migrateLegacyBuiltinConfigIfNeeded(
            ActiveShaderPackConfig config,
            List<ShaderPackContainer> externalPacks,
            Path configPath
    ) {
        if (!config.isLegacyBuiltinSelector()) {
            return config;
        }

        List<ShaderPackContainer> autoCandidates = externalPacks.stream()
                .filter(ActiveShaderPackManager::isAutoSelectableExternalPack)
                .toList();
        ShaderPackContainer preferred = selectAutoCandidateForCurrentRuntime(autoCandidates);
        if (preferred == null) {
            VulkanPostFX.LOGGER.info(
                    "[{}] Legacy builtin config detected, but no auto-safe external VPFX pack exists yet; keeping builtin for this run",
                    VulkanPostFX.MOD_ID
            );
            return config;
        }

        ActiveShaderPackConfig migrated = ActiveShaderPackConfig.defaultConfig();
        try {
            ActiveShaderPackConfig.save(configPath, migrated);
            VulkanPostFX.LOGGER.info(
                    "[{}] Migrated legacy builtin shader pack config to auto-external because an auto-safe VPFX pack exists: '{}' (id='{}', nativeCompatible={})",
                    VulkanPostFX.MOD_ID,
                    preferred.manifest().name(),
                    preferred.manifest().id(),
                    isNativeCompatibleExternalPack(preferred)
            );
        } catch (IOException e) {
            VulkanPostFX.LOGGER.warn(
                    "[{}] Failed to persist legacy builtin -> auto-external migration. Using auto for this session only.",
                    VulkanPostFX.MOD_ID,
                    e
            );
        }
        return migrated;
    }

    private static ShaderPackContainer selectActivePack(
            ActiveShaderPackConfig config,
            ShaderPackContainer builtinPack,
            List<ShaderPackContainer> externalPacks
    ) {
        if (config.forcesBuiltinPack()) {
            VulkanPostFX.LOGGER.info(
                    "[{}] Shader pack config forces builtin mode; external pack auto-selection disabled",
                    VulkanPostFX.MOD_ID
            );
            return builtinPack;
        }

        if (config.isLegacyBuiltinSelector()) {
            VulkanPostFX.LOGGER.info(
                    "[{}] Legacy builtin selector is no longer treated as an explicit lock; attempting auto external selection first",
                    VulkanPostFX.MOD_ID
            );
        }

        if (config.hasExplicitExternalPackId()) {
            ShaderPackContainer selectedExternal = externalPacks.stream()
                    .filter(pack -> config.activePackId().equals(pack.manifest().id()))
                    .findFirst()
                    .orElse(null);

            if (selectedExternal != null) {
                VulkanPostFX.LOGGER.info(
                        "[{}] Active external shader pack selected by config: '{}' (id='{}', entryPostEffect='{}')",
                        VulkanPostFX.MOD_ID,
                        selectedExternal.manifest().name(),
                        selectedExternal.manifest().id(),
                        selectedExternal.manifest().entryPostEffect()
                );
                return selectedExternal;
            }

            VulkanPostFX.LOGGER.warn(
                    "[{}] Config requested external pack id='{}', but it was not found. Falling back to builtin pack. Available external ids={}",
                    VulkanPostFX.MOD_ID,
                    config.activePackId(),
                    describePackIds(externalPacks)
            );
            return builtinPack;
        }

        if (config.autoSelectExternalPack()) {
            List<ShaderPackContainer> autoCandidates = externalPacks.stream()
                    .filter(ActiveShaderPackManager::isAutoSelectableExternalPack)
                    .toList();

            if (autoCandidates.size() != externalPacks.size()) {
                VulkanPostFX.LOGGER.warn(
                        "[{}] Auto mode skipped {} diagnostic/broken-looking VPFX pack(s). allExternalIds={}, autoCandidateIds={}",
                        VulkanPostFX.MOD_ID,
                        externalPacks.size() - autoCandidates.size(),
                        describePackIds(externalPacks),
                        describePackIds(autoCandidates)
                );
            }

            ShaderPackContainer autoSelected = selectAutoCandidateForCurrentRuntime(autoCandidates);
            if (autoSelected != null) {
                VulkanPostFX.LOGGER.info(
                        "[{}] Auto-selected external VPFX pack for current runtime: '{}' (id='{}', sourcePath={}, entryPostEffect='{}', nativeCompatible={})",
                        VulkanPostFX.MOD_ID,
                        autoSelected.manifest().name(),
                        autoSelected.manifest().id(),
                        autoSelected.sourcePath(),
                        autoSelected.manifest().entryPostEffect(),
                        isNativeCompatibleExternalPack(autoSelected)
                );
                return autoSelected;
            }

            VulkanPostFX.LOGGER.info(
                    "[{}] Auto external selection enabled, but no auto-safe ZIP pack was found in shaderpacks. Falling back to builtin pack.",
                    VulkanPostFX.MOD_ID
            );
            return builtinPack;
        }

        VulkanPostFX.LOGGER.info(
                "[{}] No external shader pack selected; using builtin pack by default",
                VulkanPostFX.MOD_ID
        );
        return builtinPack;
    }


    private static ShaderPackContainer selectAutoCandidateForCurrentRuntime(List<ShaderPackContainer> autoCandidates) {
        if (autoCandidates.isEmpty()) {
            return null;
        }

        if (VpfxNativeRuntimeSupport.isNativeRuntimeRequested()) {
            List<ShaderPackContainer> nativeCompatible = new ArrayList<>();
            List<String> candidateDiagnostics = new ArrayList<>();

            for (ShaderPackContainer candidate : autoCandidates) {
                VpfxNativeRuntimeSupportResult result = nativeSupportResultForExternalPack(candidate);
                boolean supported = result.isSupported();
                if (supported) {
                    nativeCompatible.add(candidate);
                }

                candidateDiagnostics.add(
                        candidate.manifest().id()
                                + "{"
                                + nativeCandidateGraphSummary(candidate)
                                + "}="
                                + (supported ? "supported" : "unsupported: " + result.reason())
                );
            }

            VulkanPostFX.LOGGER.info(
                    "[{}] Native auto candidate diagnostics: {}",
                    VulkanPostFX.MOD_ID,
                    candidateDiagnostics
            );

            if (!nativeCompatible.isEmpty()) {
                if (nativeCompatible.size() != autoCandidates.size()) {
                    VulkanPostFX.LOGGER.info(
                            "[{}] Native runtime requested; auto mode preferred native-compatible VPFX pack(s). allAutoCandidates={}, nativeCandidates={}",
                            VulkanPostFX.MOD_ID,
                            describePackIds(autoCandidates),
                            describePackIds(nativeCompatible)
                    );
                }
                return nativeCompatible.get(0);
            }

            VulkanPostFX.LOGGER.warn(
                    "[{}] Native runtime requested, but no auto-safe external VPFX pack passed native runtime support check. Falling back to first auto-safe candidate; it may require minecraft_postchain reload.",
                    VulkanPostFX.MOD_ID
            );
        }

        return autoCandidates.get(0);
    }

    private static String nativeCandidateGraphSummary(ShaderPackContainer pack) {
        if (pack == null || !pack.isVpfxNativePack() || pack.vpfxDefinition() == null) {
            return "native=false";
        }

        try {
            return "targets="
                    + pack.vpfxDefinition().getGraph().getTargets().size()
                    + ",passes="
                    + pack.vpfxDefinition().getGraph().getPasses().size();
        } catch (Throwable ignored) {
            return "graph=unavailable";
        }
    }

    private static boolean isNativeCompatibleExternalPack(ShaderPackContainer pack) {
        return nativeSupportResultForExternalPack(pack).isSupported();
    }

    private static VpfxNativeRuntimeSupportResult nativeSupportResultForExternalPack(ShaderPackContainer pack) {
        if (pack == null) {
            return VpfxNativeRuntimeSupportResult.unsupported("candidate is null");
        }
        if (!pack.isVpfxNativePack()) {
            return VpfxNativeRuntimeSupportResult.unsupported("candidate is not a VPFX native pack");
        }
        if (pack.vpfxDefinition() == null) {
            return VpfxNativeRuntimeSupportResult.unsupported("candidate has no parsed VPFX definition");
        }

        try {
            return VpfxNativeRuntimeSupport.check(
                    pack.vpfxDefinition().getGraph(),
                    pack.vpfxDefinition().getManifest()
            );
        } catch (Throwable t) {
            VulkanPostFX.LOGGER.warn(
                    "[{}] Failed to run native compatibility check for auto candidate '{}'",
                    VulkanPostFX.MOD_ID,
                    pack.manifest().id(),
                    t
            );
            return VpfxNativeRuntimeSupportResult.unsupported("native compatibility check threw: " + t.getMessage());
        }
    }

    private static ShaderPackContainer findBuiltinPack(List<ShaderPackContainer> discovered) {
        return discovered.stream()
                .filter(pack -> BuiltinShaderPackSource.SOURCE_ID.equals(pack.sourceId()))
                .findFirst()
                .orElse(null);
    }

    private static List<ShaderPackContainer> findExternalPacks(List<ShaderPackContainer> discovered) {
        return discovered.stream()
                .filter(pack -> !BuiltinShaderPackSource.SOURCE_ID.equals(pack.sourceId()))
                .toList();
    }

    private static boolean hasEntryPostEffect(ShaderPackContainer pack) {
        if (pack == null) {
            return false;
        }

        String entryPostEffect = pack.manifest().entryPostEffect();
        if (entryPostEffect == null || entryPostEffect.isBlank()) {
            return false;
        }

        return pack.resourceIndex().exists(entryPostEffect);
    }

    private static boolean looksLikeDiagnosticOrBrokenPack(ShaderPackContainer pack) {
        StringBuilder haystack = new StringBuilder();
        haystack.append(pack.manifest().id()).append('\n');
        haystack.append(pack.manifest().name()).append('\n');

        if (pack.sourcePath() != null && pack.sourcePath().getFileName() != null) {
            haystack.append(pack.sourcePath().getFileName()).append('\n');
        }

        if (pack.isVpfxNativePack() && pack.vpfxDefinition() != null) {
            var manifest = pack.vpfxDefinition().getManifest();
            haystack.append(manifest.getPackId()).append('\n');
            haystack.append(manifest.getName()).append('\n');
            haystack.append(manifest.getDescription()).append('\n');
            if (manifest.getMetadata() != null) {
                haystack.append(manifest.getMetadata().getTags()).append('\n');
            }
        }

        String normalized = haystack.toString().toLowerCase(Locale.ROOT);
        for (String marker : AUTO_EXCLUDED_MARKERS) {
            if (normalized.contains(marker)) {
                return true;
            }
        }

        return false;
    }

    private static String describePackIds(List<ShaderPackContainer> packs) {
        if (packs.isEmpty()) {
            return "[]";
        }

        List<String> ids = new ArrayList<>();
        for (ShaderPackContainer pack : packs) {
            ids.add(pack.manifest().id());
        }
        return ids.toString();
    }

    private static void logDiscoveredPacks() {
        for (ShaderPackContainer pack : discoveredPacks) {
            VulkanPostFX.LOGGER.info(
                    "[{}] Discovered shader pack: name='{}', id='{}', source='{}', path={}, entryPostEffect={}, resourceCount={}, vpfxNative={}, autoSelectable={}",
                    VulkanPostFX.MOD_ID,
                    pack.manifest().name(),
                    pack.manifest().id(),
                    pack.sourceId(),
                    pack.sourcePath(),
                    pack.manifest().entryPostEffect(),
                    pack.resourceIndex().size(),
                    pack.isVpfxNativePack(),
                    isAutoSelectableExternalPack(pack)
            );
        }

        for (ShaderPackScanIssue issue : discoveredPackIssues) {
            VulkanPostFX.LOGGER.warn(
                    "[{}] Discovered invalid VPFX pack candidate: name='{}', source='{}', path={}, messages={}",
                    VulkanPostFX.MOD_ID,
                    issue.displayName(),
                    issue.sourceId(),
                    issue.sourcePath(),
                    issue.messages()
            );
        }
    }
}
