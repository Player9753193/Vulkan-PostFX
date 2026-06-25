package com.ionhex975.vulkanpostfx.client.diagnostics.exporter;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.depth.VpfxSceneDepthProvider;
import com.ionhex975.vulkanpostfx.client.depth.VpfxSceneDepthState;
import com.ionhex975.vulkanpostfx.client.diagnostics.doctor.VpfxDoctorFormatter;
import com.ionhex975.vulkanpostfx.client.diagnostics.doctor.VpfxDoctorReport;
import com.ionhex975.vulkanpostfx.client.diagnostics.doctor.VpfxDoctorService;
import com.ionhex975.vulkanpostfx.client.light.VpfxHeldLightInfo;
import com.ionhex975.vulkanpostfx.client.light.VpfxHeldLightProvider;
import com.ionhex975.vulkanpostfx.client.light.colored.VpfxColoredLightCollector;
import com.ionhex975.vulkanpostfx.client.light.colored.VpfxColoredLightInfo;
import com.ionhex975.vulkanpostfx.client.light.colored.VpfxColoredLightRegistry;
import com.ionhex975.vulkanpostfx.client.light.colored.VpfxColoredLightSnapshot;
import com.ionhex975.vulkanpostfx.client.light.colored.volume.VpfxColoredLightVolumeAtlas;
import com.ionhex975.vulkanpostfx.client.light.colored.volume.VpfxColoredLightVolumeState;
import com.ionhex975.vulkanpostfx.client.pack.ActiveShaderPackManager;
import com.ionhex975.vulkanpostfx.client.pack.ShaderPackContainer;
import com.ionhex975.vulkanpostfx.client.pack.ShaderPackScanIssue;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxGraphDefinition;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxNativePackDefinition;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxPackManifest;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxPassDefinition;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxPassInput;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxTargetDefinition;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxValidationMessage;
import com.ionhex975.vulkanpostfx.client.runtime.ActivePostEffectBridge;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.framegraph.VpfxNativeRuntimeTextureBindingResult;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.framegraph.VpfxNativeRuntimeTextureResolver;
import com.ionhex975.vulkanpostfx.client.runtime.ActivePostEffectSource;
import com.ionhex975.vulkanpostfx.client.runtime.zip.ActiveZipRuntimeNamespace;
import com.ionhex975.vulkanpostfx.client.runtime.texture.dynamic.VpfxRuntimeTextureBus;
import com.ionhex975.vulkanpostfx.client.runtime.texture.dynamic.VpfxRuntimeTextureHandle;
import com.ionhex975.vulkanpostfx.client.runtime.zip.RuntimeZipPackState;
import com.ionhex975.vulkanpostfx.client.shader.uniform.VpfxBuiltinUniformBuffer;
import com.ionhex975.vulkanpostfx.client.shader.uniform.VpfxBuiltinUniformLayout;
import com.ionhex975.vulkanpostfx.client.state.PostFxRuntimeState;
import com.ionhex975.vulkanpostfx.client.shadow.VpfxShadowDepthProvider;
import com.ionhex975.vulkanpostfx.client.shadow.VpfxShadowDepthState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class VpfxDiagnosticExportService {
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final String EXPORT_DIRECTORY = "vulkanpostfx_runtime/diagnostics/exports";

    private VpfxDiagnosticExportService() {
    }

    public static VpfxDiagnosticExportResult exportNow() throws IOException {
        Minecraft minecraft = Minecraft.getInstance();
        Path gameDir = minecraft.gameDirectory.toPath();
        Path exportDir = gameDir.resolve(EXPORT_DIRECTORY);
        Files.createDirectories(exportDir);

        Path zipPath = uniqueExportPath(exportDir);
        VpfxDoctorReport doctorReport = VpfxDoctorService.createReport();

        try (VpfxDiagnosticZipWriter writer = new VpfxDiagnosticZipWriter(zipPath)) {
            writer.addText("README.txt", readmeText(zipPath));
            writer.addText("vpfx-doctor.txt", VpfxDoctorFormatter.toPlainText(doctorReport));
            writer.addText("runtime-state.txt", runtimeStateText());
            writer.addText("active-pack-info.txt", activePackInfoText());
            writer.addText("active-pack-validation.txt", activePackValidationText());
            writer.addText("invalid-packs.txt", invalidPacksText());
            writer.addText("runtime-texture-bus.txt", runtimeTextureBusText());
            writer.addText("native-runtime-textures.txt", nativeRuntimeTexturesText());
            writer.addText("colored-lights.txt", coloredLightsText());
            writer.addText("colored-light-volume.txt", coloredLightVolumeText());
            writer.addText("system-info.txt", systemInfoText(gameDir));

            writer.addFileIfExists(gameDir.resolve("logs/latest.log"), "logs/latest.log");
            writer.addFileIfExists(gameDir.resolve("logs/debug.log"), "logs/debug.log");
            writer.addFileIfExists(
                    gameDir.resolve("vulkanpostfx_runtime/diagnostics/latest-vpfx-error.txt"),
                    "vulkanpostfx/latest-vpfx-error.txt"
            );

            writer.addText("export-summary.txt", exportSummaryText(zipPath, writer.filesWritten(), writer.skippedFiles()));
            return new VpfxDiagnosticExportResult(zipPath, writer.filesWritten(), writer.skippedFiles());
        }
    }

    private static Path uniqueExportPath(Path exportDir) {
        String baseName = "vpfx-diagnostics-" + FILE_TIMESTAMP.format(LocalDateTime.now());
        Path candidate = exportDir.resolve(baseName + ".zip");
        int index = 1;
        while (Files.exists(candidate)) {
            candidate = exportDir.resolve(baseName + "-" + index + ".zip");
            index++;
        }
        return candidate;
    }

    private static String readmeText(Path zipPath) {
        return """
                === VPFX Diagnostic Export ===

                This archive was generated by /vpfx export-diagnostics.
                It is intended for debugging Vulkan PostFX runtime issues.

                Contents:
                - vpfx-doctor.txt: read-only VPFX Doctor report.
                - runtime-state.txt: current runtime and backend state.
                - active-pack-info.txt: current active shader/VPFX pack metadata.
                - active-pack-validation.txt: active pack validation messages.
                - invalid-packs.txt: scan-time invalid pack diagnostics.
                - runtime-texture-bus.txt: VPFX runtime texture bus handles.
                - native-runtime-textures.txt: native texture binding readiness for active graph texture inputs.
                - colored-lights.txt: CPU-side colored light collector snapshot.
                - colored-light-volume.txt: CPU-built colored light volume atlas state.
                - system-info.txt: Java, OS, Fabric, Minecraft, and VPFX environment.
                - logs/latest.log and logs/debug.log, if present.
                - vulkanpostfx/latest-vpfx-error.txt, if present.

                Notes:
                - The export command does not reload resources, switch packs, clear caches, or modify rendering state.
                - It only snapshots the current client-side diagnostic state.

                Export path:
                %s
                """.formatted(zipPath == null ? "unknown" : zipPath.toAbsolutePath());
    }

    private static String runtimeStateText() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== VPFX Runtime State ===\n");
        sb.append("clientInitialized: ").append(PostFxRuntimeState.isClientInitialized()).append('\n');
        sb.append("worldRenderObserved: ").append(PostFxRuntimeState.isWorldRenderObserved()).append('\n');
        sb.append("postSlotObserved: ").append(PostFxRuntimeState.isPostSlotObserved()).append('\n');
        sb.append("vpfxEnabled: ").append(PostFxRuntimeState.isDebugEffectEnabled()).append('\n');
        sb.append("debugHudVisible: ").append(PostFxRuntimeState.isDebugHudVisible()).append('\n');
        sb.append("shadowDepthDebug: ").append(PostFxRuntimeState.isShadowDepthDebugViewEnabled()).append('\n');
        sb.append("activeEffectKey: ").append(safe(PostFxRuntimeState.getActiveEffectKey())).append('\n');
        sb.append("activeExternalPostEffectId: ").append(id(PostFxRuntimeState.getActiveExternalPostEffectId())).append('\n');
        sb.append("failedExternalPostEffectId: ").append(id(PostFxRuntimeState.getFailedExternalPostEffectId())).append('\n');
        sb.append('\n');

        sb.append("--- Backend ---\n");
        sb.append("backendName: ").append(safe(PostFxRuntimeState.getBackendName())).append('\n');
        sb.append("activeRuntimeBackendId: ").append(safe(PostFxRuntimeState.getActiveRuntimeBackendId())).append('\n');
        sb.append("activeRuntimeBackendDisplayName: ").append(safe(PostFxRuntimeState.getActiveRuntimeBackendDisplayName())).append('\n');
        sb.append("activeRuntimeBackendCapabilities: ").append(String.valueOf(PostFxRuntimeState.getActiveRuntimeBackendCapabilities())).append('\n');
        sb.append("activeNativeRuntimeBackend: ").append(PostFxRuntimeState.isActiveNativeRuntimeBackend()).append('\n');
        sb.append("activeRuntimeBackendUsesPostChain: ").append(PostFxRuntimeState.activeRuntimeBackendUsesPostChain()).append('\n');
        sb.append("nativeRuntimeFallbackActive: ").append(PostFxRuntimeState.isNativeRuntimeFallbackActive()).append('\n');
        sb.append("nativeRuntimeFallbackExternalPostEffectId: ").append(id(PostFxRuntimeState.getNativeRuntimeFallbackExternalPostEffectId())).append('\n');
        sb.append("nativeRuntimeFallbackStage: ").append(safe(PostFxRuntimeState.getNativeRuntimeFallbackStage())).append('\n');
        sb.append("nativeRuntimeFallbackReason: ").append(safe(PostFxRuntimeState.getNativeRuntimeFallbackReason())).append('\n');
        sb.append("nativeRuntimeFallbackClearReason: ").append(safe(PostFxRuntimeState.getNativeRuntimeFallbackClearReason())).append('\n');
        sb.append("nativeRuntimeFallbackStaleForActiveEffect: ").append(PostFxRuntimeState.isNativeRuntimeFallbackStaleFor(PostFxRuntimeState.getActiveExternalPostEffectId())).append('\n');
        sb.append("nativeDrawSuccessCount: ").append(PostFxRuntimeState.nativeDiagnosticDrawSuccessCount()).append('\n');
        sb.append("nativeDrawFailureCount: ").append(PostFxRuntimeState.nativeDiagnosticDrawFailureCount()).append('\n');
        sb.append("postChainSkippedFrameCount: ").append(PostFxRuntimeState.postChainSkippedFrameCount()).append('\n');
        sb.append("lastSkipPostChainReason: ").append(safe(PostFxRuntimeState.skipPostChainReason())).append('\n');
        sb.append('\n');

        sb.append("--- Runtime ZIP Pack ---\n");
        sb.append("active: ").append(RuntimeZipPackState.isActive()).append('\n');
        sb.append("resourcePackInjectionAllowed: ").append(RuntimeZipPackState.isResourcePackInjectionAllowed()).append('\n');
        sb.append("packId: ").append(safe(RuntimeZipPackState.getPackId())).append('\n');
        sb.append("runtimeNamespace: ").append(safe(RuntimeZipPackState.getRuntimeNamespace())).append('\n');
        sb.append("expectedRuntimeNamespace: ").append(ActiveZipRuntimeNamespace.runtimeNamespace()).append('\n');
        sb.append("runtimePackId: ").append(ActiveZipRuntimeNamespace.runtimePackId()).append('\n');
        sb.append("runtimeRoot: ").append(path(RuntimeZipPackState.getRuntimeRoot())).append('\n');
        sb.append("externalPostEffectId: ").append(id(RuntimeZipPackState.getExternalPostEffectId())).append('\n');
        sb.append("minecraftResourceReloadedWithRuntimePack: ").append(RuntimeZipPackState.isMinecraftResourceReloadedWithRuntimePack()).append('\n');
        sb.append("targetDefinitions: ").append(RuntimeZipPackState.getTargetDefinitions().keySet()).append('\n');
        sb.append("hasScaledTargets: ").append(RuntimeZipPackState.hasScaledTargets()).append('\n');
        sb.append('\n');

        VpfxSceneDepthState sceneDepth = VpfxSceneDepthProvider.currentState();
        sb.append("--- Scene Depth ---\n");
        sb.append("available: ").append(sceneDepth.available()).append('\n');
        sb.append("targetReady: ").append(sceneDepth.targetReady()).append('\n');
        sb.append("size: ").append(sceneDepth.sizeString()).append('\n');
        sb.append("source: ").append(sceneDepth.source()).append('\n');
        sb.append("frameEpoch: ").append(sceneDepth.frameEpoch()).append('\n');
        sb.append("reason: ").append(safe(sceneDepth.reason())).append('\n');
        sb.append('\n');

        VpfxShadowDepthState shadowDepth = VpfxShadowDepthProvider.currentState();
        sb.append("--- Shadow Depth ---\n");
        sb.append("available: ").append(shadowDepth.available()).append('\n');
        sb.append("targetReady: ").append(shadowDepth.targetReady()).append('\n');
        sb.append("size: ").append(shadowDepth.sizeString()).append('\n');
        sb.append("source: ").append(shadowDepth.source()).append('\n');
        sb.append("frameEpoch: ").append(shadowDepth.frameEpoch()).append('\n');
        sb.append("primaryLight: ").append(safe(shadowDepth.primaryLight())).append('\n');
        sb.append("lightIntensity: ").append(shadowDepth.lightIntensity()).append('\n');
        sb.append("shadowPassEnabled: ").append(shadowDepth.shadowPassEnabled()).append('\n');
        sb.append("passExecuted: ").append(shadowDepth.passExecuted()).append('\n');
        sb.append("castersRendered: ").append(shadowDepth.castersRendered()).append('\n');
        sb.append("reason: ").append(safe(shadowDepth.reason())).append('\n');
        sb.append('\n');

        appendRuntimeTextureBus(sb);
        sb.append('\n');
        appendNativeRuntimeTextures(sb);
        sb.append('\n');
        appendColoredLights(sb);
        sb.append('\n');
        appendColoredLightVolume(sb);
        sb.append('\n');

        ActivePostEffectSource activeSource = ActivePostEffectBridge.getActiveSource();
        sb.append("--- Active Source ---\n");
        sb.append("sourceKind: ").append(activeSource == null ? "none" : safe(activeSource.sourceKind())).append('\n');
        sb.append("displayPath: ").append(activeSource == null ? "none" : safe(activeSource.displayPath())).append('\n');
        return sb.toString();
    }


    private static String runtimeTextureBusText() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== VPFX Runtime Texture Bus ===\n");
        appendRuntimeTextureBus(sb);
        return sb.toString();
    }

    private static String nativeRuntimeTexturesText() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== VPFX Native Runtime Texture Binding ===\n");
        appendNativeRuntimeTextures(sb);
        return sb.toString();
    }


    private static String coloredLightsText() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== VPFX Colored Lights ===\n");
        appendColoredLights(sb);
        return sb.toString();
    }


    private static String coloredLightVolumeText() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== VPFX Colored Light Volume ===\n");
        appendColoredLightVolume(sb);
        return sb.toString();
    }

    private static String activePackInfoText() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== VPFX Active Pack Info ===\n");
        ShaderPackContainer activePack = ActiveShaderPackManager.getActivePack();
        if (activePack == null) {
            sb.append("activePack: none\n");
            return sb.toString();
        }

        sb.append("manifest.id: ").append(safe(activePack.manifest().id())).append('\n');
        sb.append("manifest.name: ").append(safe(activePack.manifest().name())).append('\n');
        sb.append("manifest.version: ").append(activePack.manifest().version()).append('\n');
        sb.append("manifest.entryEffectKey: ").append(safe(activePack.manifest().entryEffectKey())).append('\n');
        sb.append("manifest.entryPostEffect: ").append(safe(activePack.manifest().entryPostEffect())).append('\n');
        sb.append("sourceId: ").append(safe(activePack.sourceId())).append('\n');
        sb.append("sourcePath: ").append(path(activePack.sourcePath())).append('\n');
        sb.append("isVpfxNativePack: ").append(activePack.isVpfxNativePack()).append('\n');
        sb.append('\n');

        if (!activePack.isVpfxNativePack() || activePack.vpfxDefinition() == null) {
            sb.append("nativeDefinition: none\n");
            return sb.toString();
        }

        VpfxNativePackDefinition definition = activePack.vpfxDefinition();
        VpfxPackManifest manifest = definition.getManifest();
        sb.append("--- VPFX Manifest ---\n");
        sb.append("zipPath: ").append(path(definition.getZipPath())).append('\n');
        sb.append("formatVersion: ").append(manifest.getFormatVersion()).append('\n');
        sb.append("packId: ").append(safe(manifest.getPackId())).append('\n');
        sb.append("name: ").append(safe(manifest.getName())).append('\n');
        sb.append("version: ").append(safe(manifest.getVersion())).append('\n');
        sb.append("author: ").append(safe(manifest.getAuthor())).append('\n');
        sb.append("description: ").append(safe(manifest.getDescription())).append('\n');
        sb.append("entryPostEffect: ").append(safe(manifest.getEntryPostEffect())).append('\n');
        sb.append("capabilities: ").append(String.valueOf(manifest.getCapabilities())).append('\n');
        sb.append("manifest.targets: ").append(manifest.getTargets()).append('\n');
        sb.append("manifest.textures: ").append(manifest.getTextures().keySet()).append('\n');
        sb.append('\n');

        appendGraphInfo(sb, definition.getGraph());
        return sb.toString();
    }

    private static void appendGraphInfo(StringBuilder sb, VpfxGraphDefinition graph) {
        sb.append("--- VPFX Graph ---\n");
        if (graph == null) {
            sb.append("graph: null\n");
            return;
        }
        sb.append("targets.count: ").append(graph.getTargets().size()).append('\n');
        for (Map.Entry<String, VpfxTargetDefinition> entry : graph.getTargets().entrySet()) {
            VpfxTargetDefinition target = entry.getValue();
            sb.append("target[").append(entry.getKey()).append("]: ")
                    .append("id=").append(safe(target.getId()))
                    .append(", scale=").append(target.getScale().map(String::valueOf).orElse("none"))
                    .append(", useDepth=").append(target.isUseDepth())
                    .append(", persistent=").append(target.isPersistent())
                    .append(", history=").append(target.isHistory())
                    .append(", pingPong=").append(target.isPingPong())
                    .append('\n');
        }
        sb.append("passes.count: ").append(graph.getPasses().size()).append('\n');
        int index = 0;
        for (VpfxPassDefinition pass : graph.getPasses()) {
            sb.append("pass[").append(index).append("]: ")
                    .append("identity=").append(pass.identityOrIndex(index))
                    .append(", vertex=").append(safe(pass.getVertexShader()))
                    .append(", fragment=").append(safe(pass.getFragmentShader()))
                    .append(", output=").append(safe(pass.getOutput()))
                    .append('\n');
            int inputIndex = 0;
            for (VpfxPassInput input : pass.getInputs()) {
                sb.append("  input[").append(inputIndex++).append("]: ")
                        .append("sampler=").append(safe(input.getSamplerName()))
                        .append(", target=").append(safe(input.getTarget()))
                        .append(", texture=").append(safe(input.getTexture()))
                        .append(", depth=").append(input.isUseDepthBuffer())
                        .append('\n');
            }
            index++;
        }
    }

    private static String activePackValidationText() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== VPFX Active Pack Validation ===\n");
        ShaderPackContainer activePack = ActiveShaderPackManager.getActivePack();
        if (activePack == null) {
            sb.append("activePack: none\n");
            return sb.toString();
        }
        if (!activePack.isVpfxNativePack() || activePack.vpfxDefinition() == null) {
            sb.append("activePack: builtin/legacy pack\n");
            return sb.toString();
        }

        List<VpfxValidationMessage> messages = activePack.vpfxDefinition().getValidationMessages();
        sb.append("messageCount: ").append(messages.size()).append('\n');
        if (messages.isEmpty()) {
            sb.append("valid: true\n");
            return sb.toString();
        }
        for (VpfxValidationMessage message : messages) {
            appendValidationMessage(sb, message);
        }
        return sb.toString();
    }

    private static String invalidPacksText() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== VPFX Invalid Packs ===\n");
        List<ShaderPackScanIssue> issues = ActiveShaderPackManager.getDiscoveredPackIssues();
        sb.append("invalidPackCount: ").append(issues.size()).append('\n');
        int index = 0;
        for (ShaderPackScanIssue issue : issues) {
            sb.append('\n');
            sb.append("--- Invalid Pack ").append(index++).append(" ---\n");
            sb.append("displayName: ").append(safe(issue.displayName())).append('\n');
            sb.append("sourceId: ").append(safe(issue.sourceId())).append('\n');
            sb.append("sourcePath: ").append(path(issue.sourcePath())).append('\n');
            sb.append("packId: ").append(safe(issue.packId())).append('\n');
            sb.append("version: ").append(safe(issue.version())).append('\n');
            sb.append("messages: ").append(issue.messages().size()).append('\n');
            for (VpfxValidationMessage message : issue.messages()) {
                appendValidationMessage(sb, message);
            }
        }
        return sb.toString();
    }

    private static void appendValidationMessage(StringBuilder sb, VpfxValidationMessage message) {
        if (message == null) {
            return;
        }
        sb.append('[').append(message.getSeverity()).append("] ")
                .append(safe(message.getCode()))
                .append(" | path=").append(safe(message.getPath()))
                .append(" | ").append(safe(message.getMessage()))
                .append('\n');
    }

    private static String systemInfoText(Path gameDir) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== VPFX System Info ===\n");
        sb.append("gameDirectory: ").append(path(gameDir)).append('\n');
        sb.append("minecraftVersion: ").append(modVersion("minecraft")).append('\n');
        sb.append("fabricLoaderVersion: ").append(modVersion("fabricloader")).append('\n');
        sb.append("fabricApiVersion: ").append(modVersion("fabric-api")).append('\n');
        sb.append("vulkanPostFxVersion: ").append(modVersion(VulkanPostFX.MOD_ID)).append('\n');
        sb.append("javaVersion: ").append(System.getProperty("java.version", "unknown")).append('\n');
        sb.append("javaVendor: ").append(System.getProperty("java.vendor", "unknown")).append('\n');
        sb.append("javaVmName: ").append(System.getProperty("java.vm.name", "unknown")).append('\n');
        sb.append("osName: ").append(System.getProperty("os.name", "unknown")).append('\n');
        sb.append("osVersion: ").append(System.getProperty("os.version", "unknown")).append('\n');
        sb.append("osArch: ").append(System.getProperty("os.arch", "unknown")).append('\n');
        sb.append("userLanguage: ").append(System.getProperty("user.language", "unknown")).append('\n');
        sb.append("userCountry: ").append(System.getProperty("user.country", "unknown")).append('\n');
        sb.append("availableProcessors: ").append(Runtime.getRuntime().availableProcessors()).append('\n');
        sb.append("maxMemoryBytes: ").append(Runtime.getRuntime().maxMemory()).append('\n');
        sb.append("totalMemoryBytes: ").append(Runtime.getRuntime().totalMemory()).append('\n');
        sb.append("freeMemoryBytes: ").append(Runtime.getRuntime().freeMemory()).append('\n');
        sb.append('\n');

        sb.append("--- VPFX Builtins ---\n");
        sb.append("vec4Slots: ").append(VpfxBuiltinUniformLayout.VEC4_SLOT_COUNT).append('\n');
        sb.append("mat4Slots: ").append(VpfxBuiltinUniformLayout.MAT4_SLOT_COUNT).append('\n');
        sb.append("totalVec4Slots: ").append(VpfxBuiltinUniformLayout.TOTAL_VEC4_SLOTS).append('\n');
        sb.append("std140ByteSize: ").append(VpfxBuiltinUniformLayout.STD140_BYTE_SIZE).append('\n');
        sb.append("runtimeUboSize: ").append(VpfxBuiltinUniformBuffer.UBO_SIZE).append('\n');
        sb.append("heldLightStorage: ").append(VpfxBuiltinUniformLayout.HELD_LIGHT_STORAGE).append('\n');
        sb.append('\n');

        VpfxHeldLightInfo light = VpfxHeldLightProvider.currentHeldLight();
        sb.append("--- Held Light ---\n");
        if (light == null || !light.enabled()) {
            sb.append("heldLight: none\n");
        } else {
            sb.append("debugName: ").append(safe(light.debugName())).append('\n');
            sb.append("rgb: ").append("%.2f / %.2f / %.2f".formatted(light.red(), light.green(), light.blue())).append('\n');
            sb.append("intensity: ").append("%.2f".formatted(light.intensity())).append('\n');
            sb.append("radius: ").append("%.2f".formatted(light.radius())).append('\n');
            sb.append("enabled: ").append(light.enabled()).append('\n');
        }
        sb.append('\n');

        appendRuntimeTextureBus(sb);
        sb.append('\n');
        appendNativeRuntimeTextures(sb);
        sb.append('\n');
        appendColoredLights(sb);
        sb.append('\n');

        VpfxShadowDepthState shadowDepth = VpfxShadowDepthProvider.currentState();
        sb.append("--- Shadow Depth ---\n");
        sb.append("available: ").append(shadowDepth.available()).append('\n');
        sb.append("targetReady: ").append(shadowDepth.targetReady()).append('\n');
        sb.append("size: ").append(shadowDepth.sizeString()).append('\n');
        sb.append("source: ").append(shadowDepth.source()).append('\n');
        sb.append("frameEpoch: ").append(shadowDepth.frameEpoch()).append('\n');
        sb.append("primaryLight: ").append(safe(shadowDepth.primaryLight())).append('\n');
        sb.append("lightIntensity: ").append(shadowDepth.lightIntensity()).append('\n');
        sb.append("shadowPassEnabled: ").append(shadowDepth.shadowPassEnabled()).append('\n');
        sb.append("passExecuted: ").append(shadowDepth.passExecuted()).append('\n');
        sb.append("castersRendered: ").append(shadowDepth.castersRendered()).append('\n');
        sb.append("reason: ").append(safe(shadowDepth.reason())).append('\n');
        return sb.toString();
    }


    private static void appendRuntimeTextureBus(StringBuilder sb) {
        sb.append("--- Runtime Texture Bus ---\n");
        List<VpfxRuntimeTextureHandle> handles = VpfxRuntimeTextureBus.snapshot();
        sb.append("handleCount: ").append(handles.size()).append('\n');
        for (VpfxRuntimeTextureHandle handle : handles) {
            sb.append(handle.logicalName())
                    .append(": ready=").append(handle.ready())
                    .append(", id=").append(handle.location() == null ? "none" : handle.location())
                    .append(", size=").append(handle.sizeString())
                    .append(", format=").append(handle.format().id())
                    .append(", dynamic=").append(handle.dynamic())
                    .append(", frame=").append(handle.frameEpoch())
                    .append(", reason=").append(safe(handle.reason()))
                    .append('\n');
        }
    }

    private static void appendNativeRuntimeTextures(StringBuilder sb) {
        sb.append("--- Native Runtime Texture Binding ---\n");
        Minecraft minecraft = Minecraft.getInstance();
        ShaderPackContainer activePack = ActiveShaderPackManager.getActivePack();
        String runtimeNamespace = RuntimeZipPackState.getRuntimeNamespace();
        sb.append("runtimeNamespace: ").append(safe(runtimeNamespace)).append('\n');

        if (activePack == null || !activePack.isVpfxNativePack() || activePack.vpfxDefinition() == null || activePack.vpfxDefinition().getGraph() == null) {
            sb.append("activeGraphTextureInputs: skipped\n");
            return;
        }

        int textureInputCount = 0;
        int passIndex = 0;
        for (VpfxPassDefinition pass : activePack.vpfxDefinition().getGraph().getPasses()) {
            String passId = pass.identityOrIndex(passIndex++);
            for (VpfxPassInput input : pass.getInputs()) {
                if (!input.isTextureInput()) {
                    continue;
                }
                textureInputCount++;
                VpfxNativeRuntimeTextureBindingResult result = VpfxNativeRuntimeTextureResolver.probe(
                        minecraft,
                        runtimeNamespace,
                        input.getTexture()
                );
                sb.append("textureInput[").append(textureInputCount).append("]: ")
                        .append("pass=").append(passId)
                        .append(", sampler=").append(safe(input.getSamplerName()))
                        .append(", texture=").append(safe(input.getTexture()))
                        .append(", ").append(result.summary())
                        .append('\n');
            }
        }

        if (textureInputCount == 0) {
            sb.append("activeGraphTextureInputs: 0\n");
        } else {
            sb.append("activeGraphTextureInputs: ").append(textureInputCount).append('\n');
        }
    }


    private static void appendColoredLights(StringBuilder sb) {
        VpfxColoredLightSnapshot snapshot = VpfxColoredLightCollector.currentSnapshot();
        sb.append("--- Colored Lights ---\n");
        sb.append("enabled: ").append(snapshot.enabled()).append('\n');
        sb.append("registeredBlockTypes: ").append(VpfxColoredLightRegistry.registeredBlockCount()).append('\n');
        sb.append("scanRadius: ").append(snapshot.scanRadius()).append('\n');
        sb.append("maxLights: ").append(snapshot.maxLights()).append('\n');
        sb.append("origin: ").append(snapshot.originString()).append('\n');
        sb.append("frameEpoch: ").append(snapshot.frameEpoch()).append('\n');
        sb.append("scanTimeMs: ").append("%.3f".formatted(snapshot.lastScanNanos() / 1_000_000.0D)).append('\n');
        sb.append("rawLightCount: ").append(snapshot.rawLightCount()).append('\n');
        sb.append("lightCount: ").append(snapshot.lightCount()).append('\n');
        sb.append("clippedByLimit: ").append(snapshot.clippedByLimit()).append('\n');
        sb.append("reason: ").append(safe(snapshot.reason())).append('\n');
        int index = 0;
        for (VpfxColoredLightInfo light : snapshot.lights()) {
            if (index >= 32) {
                sb.append("moreLights: ").append(snapshot.lightCount() - index).append('\n');
                break;
            }
            sb.append("light[").append(index++).append("]: ").append(light.shortDebugString()).append('\n');
        }
    }


    private static void appendColoredLightVolume(StringBuilder sb) {
        VpfxColoredLightVolumeState state = VpfxColoredLightVolumeAtlas.currentState();
        sb.append("--- Colored Light Volume ---\n");
        sb.append("enabled: ").append(state.enabled()).append('\n');
        sb.append("atlasReady: ").append(state.atlasReady()).append('\n');
        sb.append("textureId: ").append(state.textureId()).append('\n');
        sb.append("atlasSize: ").append(state.atlasSizeString()).append('\n');
        sb.append("volumeSize: ").append(state.volumeSizeString()).append('\n');
        sb.append("tilesPerRow: ").append(state.tilesPerRow()).append('\n');
        sb.append("voxelWorldSize: ").append(state.voxelWorldSize()).append('\n');
        sb.append("origin: ").append(state.originString()).append('\n');
        sb.append("frameEpoch: ").append(state.frameEpoch()).append('\n');
        sb.append("buildTimeMs: ").append("%.3f".formatted(state.lastBuildNanos() / 1_000_000.0D)).append('\n');
        sb.append("sourceLightCount: ").append(state.sourceLightCount()).append('\n');
        sb.append("contributingVoxels: ").append(state.contributingLightCount()).append('\n');
        sb.append("occlusionEnabled: ").append(state.occlusionEnabled()).append('\n');
        sb.append("occlusionRays: ").append(state.occlusionRayCount()).append('\n');
        sb.append("occludedRays: ").append(state.occlusionBlockedRayCount()).append('\n');
        sb.append("occlusionSamples: ").append(state.occlusionSampleCount()).append('\n');
        sb.append("averageOcclusionTransmission: ").append("%.3f".formatted(state.averageOcclusionTransmission())).append('\n');
        sb.append("occlusionSummary: ").append(state.occlusionSummary()).append('\n');
        sb.append("maxRgba: ").append(state.maxRgbString()).append('\n');
        sb.append("reason: ").append(safe(state.reason())).append('\n');
    }

    private static String exportSummaryText(Path zipPath, int filesWritten, List<String> skippedFiles) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== VPFX Diagnostic Export Summary ===\n");
        sb.append("zipPath: ").append(path(zipPath)).append('\n');
        sb.append("filesWritten: ").append(filesWritten).append('\n');
        sb.append("skippedFiles: ").append(skippedFiles == null ? 0 : skippedFiles.size()).append('\n');
        if (skippedFiles != null) {
            for (String skippedFile : skippedFiles) {
                sb.append("- ").append(skippedFile).append('\n');
            }
        }
        return sb.toString();
    }

    private static String modVersion(String modId) {
        try {
            Optional<String> version = FabricLoader.getInstance()
                    .getModContainer(modId)
                    .map(container -> container.getMetadata().getVersion().getFriendlyString());
            return version.orElse("not installed");
        } catch (Throwable t) {
            return "unknown (" + t.getClass().getSimpleName() + ")";
        }
    }

    private static String id(Identifier id) {
        return id == null ? "none" : id.toString();
    }

    private static String path(Path path) {
        return path == null ? "none" : path.toAbsolutePath().toString();
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }
}
