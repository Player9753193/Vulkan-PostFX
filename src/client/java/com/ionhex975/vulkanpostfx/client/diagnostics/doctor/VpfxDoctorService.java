package com.ionhex975.vulkanpostfx.client.diagnostics.doctor;

import com.ionhex975.vulkanpostfx.client.depth.VpfxSceneDepthProvider;
import com.ionhex975.vulkanpostfx.client.depth.VpfxSceneDepthState;
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
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxNativePackDefinition;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.framegraph.VpfxNativeRuntimeTextureResolver;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.framegraph.VpfxNativeRuntimeTextureBindingResult;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxPassInput;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxPassDefinition;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxValidationMessage;
import com.ionhex975.vulkanpostfx.client.runtime.ActivePostEffectBridge;
import com.ionhex975.vulkanpostfx.client.runtime.ActivePostEffectSource;
import com.ionhex975.vulkanpostfx.client.runtime.vpfx.VpfxRuntimeBackendCapabilities;
import com.ionhex975.vulkanpostfx.client.runtime.zip.RuntimeZipPackState;
import com.ionhex975.vulkanpostfx.client.runtime.texture.dynamic.VpfxRuntimeTextureBus;
import com.ionhex975.vulkanpostfx.client.runtime.texture.dynamic.VpfxRuntimeTextureHandle;
import com.ionhex975.vulkanpostfx.client.shader.uniform.VpfxBuiltinUniformBuffer;
import com.ionhex975.vulkanpostfx.client.shadow.VpfxShadowDepthProvider;
import com.ionhex975.vulkanpostfx.client.shadow.VpfxShadowDepthState;
import com.ionhex975.vulkanpostfx.client.shader.uniform.VpfxBuiltinUniformLayout;
import com.ionhex975.vulkanpostfx.client.state.PostFxRuntimeState;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class VpfxDoctorService {
    private VpfxDoctorService() {
    }

    public static VpfxDoctorReport createReport() {
        List<VpfxDoctorSection> sections = new ArrayList<>();
        VpfxRuntimeResourceProbe resourceProbe = new VpfxRuntimeResourceProbe();

        sections.add(runtimeSection());
        sections.add(backendSection());
        sections.add(new VpfxDoctorSection("Runtime Pack", resourceProbe.checkRuntimePack()));
        sections.add(new VpfxDoctorSection("Shader Sources", resourceProbe.checkActiveShaderSources()));
        sections.add(packValidationSection());
        sections.add(sceneDepthSection());
        sections.add(shadowDepthSection());
        sections.add(runtimeTextureBusSection());
        sections.add(nativeRuntimeTextureBindingSection());
        sections.add(coloredLightsSection());
        sections.add(coloredLightVolumeSection());
        sections.add(builtinsSection());
        sections.add(heldLightSection());

        return new VpfxDoctorReport(Instant.now(), sections);
    }

    private static VpfxDoctorSection runtimeSection() {
        List<VpfxDoctorCheck> checks = new ArrayList<>();
        Minecraft minecraft = Minecraft.getInstance();
        ShaderPackContainer activePack = ActiveShaderPackManager.getActivePack();
        ActivePostEffectSource activeSource = ActivePostEffectBridge.getActiveSource();
        Identifier activeExternal = PostFxRuntimeState.getActiveExternalPostEffectId();
        Identifier failedExternal = PostFxRuntimeState.getFailedExternalPostEffectId();

        checks.add(VpfxDoctorCheck.info("minecraft_present", "Minecraft client", minecraft != null));
        checks.add(VpfxDoctorCheck.info("level_present", "Client level loaded", minecraft != null && minecraft.level != null));
        checks.add(VpfxDoctorCheck.info("player_present", "Client player loaded", minecraft != null && minecraft.player != null));
        checks.add(VpfxDoctorCheck.info("client_initialized", "Client initialized", PostFxRuntimeState.isClientInitialized()));
        checks.add(VpfxDoctorCheck.info("world_render_observed", "World render observed", PostFxRuntimeState.isWorldRenderObserved()));
        checks.add(VpfxDoctorCheck.info("post_slot_observed", "Post slot observed", PostFxRuntimeState.isPostSlotObserved()));
        checks.add(activePack == null
                ? VpfxDoctorCheck.warn("active_pack", "Active pack", "none", "No active shader pack selected.")
                : VpfxDoctorCheck.ok("active_pack", "Active pack", activePack.manifest().name() + " [id=" + activePack.manifest().id() + ", source=" + activePack.sourceId() + "]"));
        checks.add(VpfxDoctorCheck.info("active_effect_key", "Active effect key", PostFxRuntimeState.getActiveEffectKey()));
        checks.add(activeExternal == null
                ? VpfxDoctorCheck.info("active_external_post_effect", "Active external post effect", "none")
                : VpfxDoctorCheck.ok("active_external_post_effect", "Active external post effect", activeExternal));
        checks.add(failedExternal == null
                ? VpfxDoctorCheck.ok("failed_external_post_effect", "Failed external post effect", "none")
                : VpfxDoctorCheck.error("failed_external_post_effect", "Failed external post effect", failedExternal, "Current external post effect is marked failed; VPFX may be falling back to vanilla."));
        checks.add(VpfxDoctorCheck.info("vpfx_enabled", "VPFX enabled", PostFxRuntimeState.isDebugEffectEnabled()));
        checks.add(VpfxDoctorCheck.info("debug_hud_visible", "Debug HUD visible", PostFxRuntimeState.isDebugHudVisible()));
        checks.add(VpfxDoctorCheck.info("shadow_depth_debug", "Shadow depth debug", PostFxRuntimeState.isShadowDepthDebugViewEnabled()));
        checks.add(VpfxDoctorCheck.info("active_source_kind", "Active source kind", activeSource == null ? "none" : activeSource.sourceKind()));
        checks.add(VpfxDoctorCheck.info("active_source_display", "Active source display path", activeSource == null ? "none" : activeSource.displayPath()));
        return new VpfxDoctorSection("Runtime", checks);
    }

    private static VpfxDoctorSection backendSection() {
        List<VpfxDoctorCheck> checks = new ArrayList<>();
        VpfxRuntimeBackendCapabilities capabilities = PostFxRuntimeState.getActiveRuntimeBackendCapabilities();

        checks.add(VpfxDoctorCheck.info("backend_strategy", "Backend strategy", "native-first safe fallback"));
        checks.add(VpfxDoctorCheck.info("active_backend_id", "Active backend id", PostFxRuntimeState.getActiveRuntimeBackendId()));
        checks.add(VpfxDoctorCheck.info("active_backend_display", "Active backend display", PostFxRuntimeState.getActiveRuntimeBackendDisplayName()));
        checks.add(VpfxDoctorCheck.info("backend_capabilities", "Backend capabilities", capabilities == null ? "unknown" : capabilities.toString()));
        checks.add(VpfxDoctorCheck.info("backend_is_native", "Backend is native", PostFxRuntimeState.isActiveNativeRuntimeBackend()));
        checks.add(VpfxDoctorCheck.info("backend_uses_postchain", "Backend uses PostChain", PostFxRuntimeState.activeRuntimeBackendUsesPostChain()));

        Identifier activeExternal = PostFxRuntimeState.getActiveExternalPostEffectId();
        if (PostFxRuntimeState.isNativeRuntimeFallbackActive()) {
            boolean staleFallback = PostFxRuntimeState.isNativeRuntimeFallbackStaleFor(activeExternal);
            checks.add((staleFallback ? VpfxDoctorCheck.error(
                    "native_fallback_stale",
                    "Native fallback scope",
                    String.valueOf(PostFxRuntimeState.getNativeRuntimeFallbackExternalPostEffectId()),
                    "Fallback belongs to a different external post effect. Current external post effect is " + activeExternal
            ) : VpfxDoctorCheck.warn(
                    "native_fallback",
                    "Native fallback",
                    PostFxRuntimeState.getNativeRuntimeFallbackStage(),
                    PostFxRuntimeState.getNativeRuntimeFallbackReason()
            )));
            checks.add(VpfxDoctorCheck.info(
                    "native_fallback_effect",
                    "Native fallback external post effect",
                    String.valueOf(PostFxRuntimeState.getNativeRuntimeFallbackExternalPostEffectId())
            ));
        } else {
            checks.add(VpfxDoctorCheck.ok("native_fallback", "Native fallback", "none"));
            checks.add(VpfxDoctorCheck.info("native_fallback_last_clear", "Native fallback last clear", PostFxRuntimeState.getNativeRuntimeFallbackClearReason()));
        }

        checks.add(VpfxDoctorCheck.info("native_draw_success", "Native draw success count", PostFxRuntimeState.nativeDiagnosticDrawSuccessCount()));
        checks.add(VpfxDoctorCheck.info("native_draw_failure", "Native draw failure count", PostFxRuntimeState.nativeDiagnosticDrawFailureCount()));
        checks.add(VpfxDoctorCheck.info("postchain_skipped", "PostChain skipped frame count", PostFxRuntimeState.postChainSkippedFrameCount()));
        checks.add(VpfxDoctorCheck.info("last_skip_postchain_reason", "Last skip PostChain reason", PostFxRuntimeState.skipPostChainReason()));
        return new VpfxDoctorSection("Backend", checks);
    }

    private static VpfxDoctorSection packValidationSection() {
        List<VpfxDoctorCheck> checks = new ArrayList<>();
        ShaderPackContainer activePack = ActiveShaderPackManager.getActivePack();
        List<ShaderPackScanIssue> issues = ActiveShaderPackManager.getDiscoveredPackIssues();

        checks.add(VpfxDoctorCheck.info("discovered_valid_packs", "Discovered valid packs", ActiveShaderPackManager.getDiscoveredPacks().size()));
        checks.add(issues.isEmpty()
                ? VpfxDoctorCheck.ok("discovered_invalid_packs", "Discovered invalid packs", 0)
                : VpfxDoctorCheck.warn("discovered_invalid_packs", "Discovered invalid packs", issues.size(), "Invalid packs are visible in the UI and cannot be activated."));

        if (activePack == null) {
            checks.add(VpfxDoctorCheck.skipped("active_pack_validation", "Active pack validation", "No active pack."));
        } else if (activePack.isVpfxNativePack() && activePack.vpfxDefinition() != null) {
            VpfxNativePackDefinition definition = activePack.vpfxDefinition();
            List<VpfxValidationMessage> messages = definition.getValidationMessages();
            long fatalCount = messages.stream().filter(message -> message.getSeverity() == VpfxValidationMessage.Severity.FATAL).count();
            long warningCount = messages.stream().filter(message -> message.getSeverity() == VpfxValidationMessage.Severity.WARNING).count();
            if (fatalCount > 0) {
                checks.add(VpfxDoctorCheck.error("active_pack_validation", "Active pack validation", "fatal=" + fatalCount + ", warning=" + warningCount, "Active VPFX graph has fatal validation messages."));
            } else if (warningCount > 0) {
                checks.add(VpfxDoctorCheck.warn("active_pack_validation", "Active pack validation", "warning=" + warningCount, "Active VPFX graph has warnings."));
            } else {
                checks.add(VpfxDoctorCheck.ok("active_pack_validation", "Active pack validation", "valid"));
            }
            checks.add(VpfxDoctorCheck.info("active_graph_pass_count", "Active graph pass count", definition.getGraph().getPasses().size()));
            checks.add(VpfxDoctorCheck.info("active_graph_target_count", "Active graph target count", definition.getGraph().getTargets().size()));
        } else {
            checks.add(VpfxDoctorCheck.info("active_pack_validation", "Active pack validation", "builtin/legacy pack"));
        }

        int index = 0;
        for (ShaderPackScanIssue issue : issues) {
            if (index >= 8) {
                checks.add(VpfxDoctorCheck.info("invalid_pack_more", "More invalid packs", issues.size() - index));
                break;
            }
            String code = "invalid_pack_" + index;
            String firstMessage = issue.messages().isEmpty()
                    ? "no message"
                    : issue.messages().get(0).toString();
            checks.add(VpfxDoctorCheck.warn(
                    code,
                    "Invalid pack: " + issue.displayName(),
                    issue.sourcePath() == null ? "unknown path" : issue.sourcePath().toString(),
                    firstMessage
            ));
            index++;
        }

        return new VpfxDoctorSection("Pack Validation", checks);
    }


    private static VpfxDoctorSection sceneDepthSection() {
        List<VpfxDoctorCheck> checks = new ArrayList<>();
        ShaderPackContainer activePack = ActiveShaderPackManager.getActivePack();
        boolean requiredByManifest = false;
        boolean requiredByGraph = false;

        if (activePack != null && activePack.isVpfxNativePack() && activePack.vpfxDefinition() != null) {
            VpfxNativePackDefinition definition = activePack.vpfxDefinition();
            requiredByManifest = definition.getManifest().getCapabilities().isSceneDepth();
            requiredByGraph = definition.getGraph().getPasses().stream()
                    .flatMap(pass -> pass.getInputs().stream())
                    .anyMatch(input -> input.isUseDepthBuffer()
                            || "minecraft:scene_depth".equals(input.getTarget())
                            || "vulkanpostfx:scene_depth".equals(input.getTarget()));
        }

        VpfxSceneDepthState state = VpfxSceneDepthProvider.currentState();
        checks.add(requiredByManifest
                ? VpfxDoctorCheck.ok("scene_depth_required_manifest", "Required by manifest", true)
                : VpfxDoctorCheck.info("scene_depth_required_manifest", "Required by manifest", false));
        checks.add(requiredByGraph
                ? VpfxDoctorCheck.ok("scene_depth_required_graph", "Required by active graph", true)
                : VpfxDoctorCheck.info("scene_depth_required_graph", "Required by active graph", false));
        boolean depthRequired = requiredByManifest || requiredByGraph;
        checks.add(state.available()
                ? VpfxDoctorCheck.ok("scene_depth_available", "Scene depth available this frame", true)
                : depthRequired
                        ? VpfxDoctorCheck.warn("scene_depth_available", "Scene depth available this frame", false, state.reason())
                        : VpfxDoctorCheck.info("scene_depth_available", "Scene depth available this frame", false));
        checks.add(state.targetReady()
                ? VpfxDoctorCheck.ok("scene_depth_target_ready", "Scene depth target ready", true)
                : VpfxDoctorCheck.info("scene_depth_target_ready", "Scene depth target ready", false));
        checks.add(VpfxDoctorCheck.info("scene_depth_size", "Scene depth size", state.sizeString()));
        checks.add(VpfxDoctorCheck.info("scene_depth_source", "Scene depth source", state.source()));
        checks.add(VpfxDoctorCheck.info("scene_depth_frame_epoch", "Scene depth captured frame", state.frameEpoch()));
        checks.add(VpfxDoctorCheck.info("scene_depth_reason", "Scene depth reason", state.reason()));
        return new VpfxDoctorSection("Scene Depth", checks);
    }


    private static VpfxDoctorSection shadowDepthSection() {
        List<VpfxDoctorCheck> checks = new ArrayList<>();
        ShaderPackContainer activePack = ActiveShaderPackManager.getActivePack();
        boolean requiredByManifest = false;
        boolean requiredByGraph = false;

        if (activePack != null && activePack.isVpfxNativePack() && activePack.vpfxDefinition() != null) {
            VpfxNativePackDefinition definition = activePack.vpfxDefinition();
            requiredByManifest = definition.getManifest().getCapabilities().isShadowDepth();
            requiredByGraph = definition.getGraph().getPasses().stream()
                    .flatMap(pass -> pass.getInputs().stream())
                    .anyMatch(input -> "minecraft:shadow_depth".equals(input.getTarget())
                            || "vulkanpostfx:shadow_depth".equals(input.getTarget()));
        }

        VpfxShadowDepthState state = VpfxShadowDepthProvider.currentState();
        checks.add(requiredByManifest
                ? VpfxDoctorCheck.ok("shadow_depth_required_manifest", "Required by manifest", true)
                : VpfxDoctorCheck.info("shadow_depth_required_manifest", "Required by manifest", false));
        checks.add(requiredByGraph
                ? VpfxDoctorCheck.ok("shadow_depth_required_graph", "Required by active graph", true)
                : VpfxDoctorCheck.info("shadow_depth_required_graph", "Required by active graph", false));
        boolean depthRequired = requiredByManifest || requiredByGraph;
        checks.add(state.available()
                ? VpfxDoctorCheck.ok("shadow_depth_available", "Shadow depth available this frame", true)
                : depthRequired
                        ? VpfxDoctorCheck.warn("shadow_depth_available", "Shadow depth available this frame", false, state.reason())
                        : VpfxDoctorCheck.info("shadow_depth_available", "Shadow depth available this frame", false));
        checks.add(state.targetReady()
                ? VpfxDoctorCheck.ok("shadow_depth_target_ready", "Shadow depth target ready", true)
                : VpfxDoctorCheck.info("shadow_depth_target_ready", "Shadow depth target ready", false));
        checks.add(VpfxDoctorCheck.info("shadow_depth_size", "Shadow depth size", state.sizeString()));
        checks.add(VpfxDoctorCheck.info("shadow_depth_source", "Shadow depth source", state.source()));
        checks.add(VpfxDoctorCheck.info("shadow_depth_frame_epoch", "Shadow depth captured frame", state.frameEpoch()));
        checks.add(VpfxDoctorCheck.info("shadow_depth_primary_light", "Shadow primary light", state.primaryLight()));
        checks.add(VpfxDoctorCheck.info("shadow_depth_light_intensity", "Shadow light intensity", "%.3f".formatted(state.lightIntensity())));
        checks.add(VpfxDoctorCheck.info("shadow_depth_pass_enabled", "Shadow pass enabled", state.shadowPassEnabled()));
        checks.add(VpfxDoctorCheck.info("shadow_depth_pass_executed", "Shadow pass executed", state.passExecuted()));
        checks.add(VpfxDoctorCheck.info("shadow_depth_casters_rendered", "Shadow casters rendered", state.castersRendered()));
        checks.add(VpfxDoctorCheck.info("shadow_depth_reason", "Shadow depth reason", state.reason()));
        return new VpfxDoctorSection("Shadow Depth", checks);
    }


    private static VpfxDoctorSection runtimeTextureBusSection() {
        List<VpfxDoctorCheck> checks = new ArrayList<>();
        List<VpfxRuntimeTextureHandle> handles = VpfxRuntimeTextureBus.snapshot();
        checks.add(VpfxDoctorCheck.ok("runtime_texture_count", "Runtime texture handles", handles.size()));
        for (VpfxRuntimeTextureHandle handle : handles) {
            String value = (handle.ready() ? "ready" : "pending")
                    + ", id=" + (handle.location() == null ? "none" : handle.location().toString())
                    + ", size=" + handle.sizeString()
                    + ", format=" + handle.format().id()
                    + ", dynamic=" + handle.dynamic()
                    + ", frame=" + handle.frameEpoch();
            VpfxDoctorCheck check = handle.ready()
                    ? VpfxDoctorCheck.ok("runtime_texture_" + handle.logicalName(), "Runtime texture: " + handle.logicalName(), value)
                    : VpfxDoctorCheck.info("runtime_texture_" + handle.logicalName(), "Runtime texture: " + handle.logicalName(), value, handle.reason());
            checks.add(check);
        }
        return new VpfxDoctorSection("Runtime Texture Bus", checks);
    }

    private static VpfxDoctorSection nativeRuntimeTextureBindingSection() {
        List<VpfxDoctorCheck> checks = new ArrayList<>();
        Minecraft minecraft = Minecraft.getInstance();
        ShaderPackContainer activePack = ActiveShaderPackManager.getActivePack();
        String runtimeNamespace = RuntimeZipPackState.getRuntimeNamespace();

        if (activePack == null || !activePack.isVpfxNativePack() || activePack.vpfxDefinition() == null) {
            checks.add(VpfxDoctorCheck.info(
                    "native_runtime_texture_binding",
                    "Native runtime texture binding",
                    "skipped",
                    "No active VPFX native pack."
            ));
            return new VpfxDoctorSection("Native Runtime Texture Binding", checks);
        }

        VpfxNativePackDefinition definition = activePack.vpfxDefinition();
        if (definition.getGraph() == null) {
            checks.add(VpfxDoctorCheck.warn(
                    "native_runtime_texture_binding",
                    "Native runtime texture binding",
                    "skipped",
                    "Active VPFX definition has no graph."
            ));
            return new VpfxDoctorSection("Native Runtime Texture Binding", checks);
        }

        checks.add(VpfxDoctorCheck.info("native_runtime_texture_namespace", "Runtime namespace", runtimeNamespace == null || runtimeNamespace.isBlank() ? "none" : runtimeNamespace));

        int textureInputCount = 0;
        int passIndex = 0;
        for (VpfxPassDefinition pass : definition.getGraph().getPasses()) {
            String passId = pass.identityOrIndex(passIndex++);
            for (VpfxPassInput input : pass.getInputs()) {
                if (!input.isTextureInput()) {
                    continue;
                }

                textureInputCount++;
                String logicalName = input.getTexture();
                VpfxNativeRuntimeTextureBindingResult result = VpfxNativeRuntimeTextureResolver.probe(
                        minecraft,
                        runtimeNamespace,
                        logicalName
                );
                String label = "Pass " + passId + " texture " + logicalName;
                if (result.available()) {
                    checks.add(VpfxDoctorCheck.ok(
                            "native_texture_" + textureInputCount,
                            label,
                            result.summary()
                    ));
                } else if (result.transientFailure()) {
                    checks.add(VpfxDoctorCheck.warn(
                            "native_texture_" + textureInputCount,
                            label,
                            result.summary(),
                            "Native can retry this binding on a later frame without sticky fallback."
                    ));
                } else {
                    checks.add(VpfxDoctorCheck.error(
                            "native_texture_" + textureInputCount,
                            label,
                            result.summary(),
                            "Native cannot bind this texture input until the descriptor/resource issue is fixed."
                    ));
                }
            }
        }

        if (textureInputCount == 0) {
            checks.add(VpfxDoctorCheck.ok(
                    "native_runtime_texture_inputs",
                    "Texture inputs in active graph",
                    0,
                    "Active graph does not use logical texture inputs."
            ));
        } else {
            checks.add(VpfxDoctorCheck.info("native_runtime_texture_input_count", "Texture inputs in active graph", textureInputCount));
        }

        return new VpfxDoctorSection("Native Runtime Texture Binding", checks);
    }


    private static VpfxDoctorSection coloredLightsSection() {
        List<VpfxDoctorCheck> checks = new ArrayList<>();
        VpfxColoredLightSnapshot snapshot = VpfxColoredLightCollector.currentSnapshot();
        checks.add(snapshot.enabled()
                ? VpfxDoctorCheck.ok("colored_lights_enabled", "Colored light collector", true)
                : VpfxDoctorCheck.warn("colored_lights_enabled", "Colored light collector", false, snapshot.reason()));
        checks.add(VpfxDoctorCheck.info("colored_lights_registered_blocks", "Registered colored light block types", VpfxColoredLightRegistry.registeredBlockCount()));
        checks.add(VpfxDoctorCheck.info("colored_lights_scan_radius", "Scan radius", snapshot.scanRadius()));
        checks.add(VpfxDoctorCheck.info("colored_lights_max", "Max lights", snapshot.maxLights()));
        checks.add(VpfxDoctorCheck.info("colored_lights_origin", "Scan origin", snapshot.originString()));
        checks.add(VpfxDoctorCheck.info("colored_lights_frame", "Last scan frame", snapshot.frameEpoch()));
        checks.add(VpfxDoctorCheck.info("colored_lights_scan_time_ms", "Last scan time ms", "%.3f".formatted(snapshot.lastScanNanos() / 1_000_000.0D)));
        checks.add(snapshot.lightCount() > 0
                ? VpfxDoctorCheck.ok("colored_lights_count", "Collected colored lights", snapshot.lightCount())
                : VpfxDoctorCheck.info("colored_lights_count", "Collected colored lights", 0, snapshot.reason()));
        checks.add(snapshot.clippedByLimit()
                ? VpfxDoctorCheck.warn("colored_lights_clipped", "Clipped by max light limit", true, "raw=" + snapshot.rawLightCount() + ", kept=" + snapshot.lightCount())
                : VpfxDoctorCheck.ok("colored_lights_clipped", "Clipped by max light limit", false));

        int index = 0;
        for (VpfxColoredLightInfo light : snapshot.lights()) {
            if (index >= 8) {
                checks.add(VpfxDoctorCheck.info("colored_light_more", "More colored lights", snapshot.lightCount() - index));
                break;
            }
            checks.add(VpfxDoctorCheck.info(
                    "colored_light_" + index,
                    "Colored light " + index,
                    light.shortDebugString()
            ));
            index++;
        }
        return new VpfxDoctorSection("Colored Lights", checks);
    }


    private static VpfxDoctorSection coloredLightVolumeSection() {
        List<VpfxDoctorCheck> checks = new ArrayList<>();
        VpfxColoredLightVolumeState state = VpfxColoredLightVolumeAtlas.currentState();
        checks.add(state.enabled()
                ? VpfxDoctorCheck.ok("colored_light_volume_enabled", "Colored light volume", true)
                : VpfxDoctorCheck.warn("colored_light_volume_enabled", "Colored light volume", false, state.reason()));
        checks.add(state.atlasReady()
                ? VpfxDoctorCheck.ok("colored_light_volume_atlas_ready", "Atlas ready", true)
                : VpfxDoctorCheck.warn("colored_light_volume_atlas_ready", "Atlas ready", false, state.reason()));
        checks.add(VpfxDoctorCheck.info("colored_light_volume_texture", "Texture id", state.textureId()));
        checks.add(VpfxDoctorCheck.info("colored_light_volume_atlas_size", "Atlas size", state.atlasSizeString()));
        checks.add(VpfxDoctorCheck.info("colored_light_volume_size", "Volume size", state.volumeSizeString()));
        checks.add(VpfxDoctorCheck.info("colored_light_volume_tiles_per_row", "Tiles per row", state.tilesPerRow()));
        checks.add(VpfxDoctorCheck.info("colored_light_volume_voxel_size", "Voxel world size", "%.2f".formatted(state.voxelWorldSize())));
        checks.add(VpfxDoctorCheck.info("colored_light_volume_origin", "Volume origin", state.originString()));
        checks.add(VpfxDoctorCheck.info("colored_light_volume_frame", "Last build frame", state.frameEpoch()));
        checks.add(VpfxDoctorCheck.info("colored_light_volume_build_time_ms", "Last build time ms", "%.3f".formatted(state.lastBuildNanos() / 1_000_000.0D)));
        checks.add(VpfxDoctorCheck.info("colored_light_volume_source_lights", "Source light count", state.sourceLightCount()));
        checks.add(VpfxDoctorCheck.info("colored_light_volume_contributing_voxels", "Contributing voxels", state.contributingLightCount()));
        checks.add(state.occlusionEnabled()
                ? VpfxDoctorCheck.ok("colored_light_volume_occlusion", "Occlusion", state.occlusionSummary())
                : VpfxDoctorCheck.info("colored_light_volume_occlusion", "Occlusion", "disabled"));
        checks.add(VpfxDoctorCheck.info("colored_light_volume_occlusion_rays", "Occlusion rays", state.occlusionRayCount()));
        checks.add(VpfxDoctorCheck.info("colored_light_volume_occlusion_blocked", "Occluded rays", state.occlusionBlockedRayCount()));
        checks.add(VpfxDoctorCheck.info("colored_light_volume_occlusion_samples", "Occlusion samples", state.occlusionSampleCount()));
        checks.add(VpfxDoctorCheck.info("colored_light_volume_occlusion_avg", "Average transmission", "%.3f".formatted(state.averageOcclusionTransmission())));
        checks.add(VpfxDoctorCheck.info("colored_light_volume_max_rgba", "Max atlas RGBA", state.maxRgbString()));
        checks.add(VpfxDoctorCheck.info("colored_light_volume_reason", "Colored light volume reason", state.reason()));
        return new VpfxDoctorSection("Colored Light Volume", checks);
    }

    private static VpfxDoctorSection builtinsSection() {
        List<VpfxDoctorCheck> checks = new ArrayList<>();
        checks.add(VpfxDoctorCheck.ok("builtins_vec4_slots", "Builtins vec4 slots", VpfxBuiltinUniformLayout.VEC4_SLOT_COUNT));
        checks.add(VpfxDoctorCheck.ok("builtins_mat4_slots", "Builtins mat4 slots", VpfxBuiltinUniformLayout.MAT4_SLOT_COUNT));
        checks.add(VpfxDoctorCheck.ok("builtins_total_vec4_slots", "Builtins total vec4 slots", VpfxBuiltinUniformLayout.TOTAL_VEC4_SLOTS));
        checks.add(VpfxDoctorCheck.ok("builtins_std140_bytes", "Builtins std140 bytes", VpfxBuiltinUniformLayout.STD140_BYTE_SIZE));
        checks.add(VpfxDoctorCheck.info("builtins_runtime_ubo_size", "Runtime UBO byte size", VpfxBuiltinUniformBuffer.UBO_SIZE));
        checks.add(VpfxDoctorCheck.ok("held_light_storage", "Held-light storage", VpfxBuiltinUniformLayout.HELD_LIGHT_STORAGE));
        if (VpfxBuiltinUniformBuffer.UBO_SIZE != VpfxBuiltinUniformLayout.STD140_BYTE_SIZE) {
            checks.add(VpfxDoctorCheck.error(
                    "builtins_layout_mismatch",
                    "Builtins layout mismatch",
                    VpfxBuiltinUniformBuffer.UBO_SIZE,
                    "Expected " + VpfxBuiltinUniformLayout.STD140_BYTE_SIZE + " bytes. Native and PostChain UBO layouts may diverge."
            ));
        } else {
            checks.add(VpfxDoctorCheck.ok("builtins_layout_match", "Builtins layout match", true));
        }
        return new VpfxDoctorSection("Builtins", checks);
    }

    private static VpfxDoctorSection heldLightSection() {
        List<VpfxDoctorCheck> checks = new ArrayList<>();
        VpfxHeldLightInfo light = VpfxHeldLightProvider.currentHeldLight();
        if (light == null || !light.enabled()) {
            checks.add(VpfxDoctorCheck.info("held_light", "Held light", "none"));
            return new VpfxDoctorSection("Held Light", checks);
        }

        checks.add(VpfxDoctorCheck.ok("held_light", "Held light", light.debugName()));
        checks.add(VpfxDoctorCheck.ok("held_light_rgb", "Held light RGB", "%.2f / %.2f / %.2f".formatted(light.red(), light.green(), light.blue())));
        checks.add(VpfxDoctorCheck.ok("held_light_intensity", "Held light intensity", "%.2f".formatted(light.intensity())));
        checks.add(VpfxDoctorCheck.ok("held_light_radius", "Held light radius", "%.2f".formatted(light.radius())));
        checks.add(VpfxDoctorCheck.ok("held_light_enabled", "Held light enabled", light.enabled()));
        return new VpfxDoctorSection("Held Light", checks);
    }
}
