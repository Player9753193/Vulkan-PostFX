package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.framegraph;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxTargetDefinition;
import com.ionhex975.vulkanpostfx.client.postfx.SceneDepthCaptureTargets;
import com.ionhex975.vulkanpostfx.client.runtime.zip.RuntimeZipPackState;
import com.ionhex975.vulkanpostfx.client.shadow.ShadowRenderTargetsLite;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-frame native VPFX target pool.
 *
 * NR-2.2 policy:
 * - minecraft:main is the real main target and can be used as final output;
 * - minecraft:scene_color is a per-frame copy of main color before VPFX passes;
 * - vulkanpostfx:scene_depth is input-only and samples the captured depth texture view;
 * - graph-declared targets are transient by default;
 * - targets with persistent/history/ping_pong survive across frames through VpfxNativeHistoryTargetStore;
 * - history:<target_id> samples the previous-frame target when history or ping_pong is enabled.
 */
public final class VpfxNativeTargetPool implements AutoCloseable {
    private static final GpuFormat DEFAULT_COLOR_FORMAT = GpuFormat.RGBA8_UNORM;
    private static boolean firstSceneDepthAliasLogged;
    private static boolean firstShadowDepthInputLogged;

    private final RenderTarget mainTarget;
    private final int mainWidth;
    private final int mainHeight;
    private final String runtimeNamespace;
    private final Map<String, VpfxNativeFrameTarget> targets = new LinkedHashMap<>();
    private final List<VpfxNativeHistoryTargetStore.Binding> persistentBindings = new ArrayList<>();
    private boolean committed;

    private VpfxNativeTargetPool(RenderTarget mainTarget, String runtimeNamespace) {
        this.mainTarget = mainTarget;
        this.mainWidth = mainTarget.width;
        this.mainHeight = mainTarget.height;
        this.runtimeNamespace = runtimeNamespace == null ? "" : runtimeNamespace;
        targets.put("minecraft:main", new VpfxNativeFrameTarget("minecraft:main", mainTarget, false, true, null));
    }

    public static VpfxNativeTargetPool create(
            RenderTarget mainTarget,
            Map<String, VpfxTargetDefinition> declaredTargets
    ) {
        VpfxNativeTargetPool pool = new VpfxNativeTargetPool(mainTarget, RuntimeZipPackState.getRuntimeNamespace());
        pool.createSceneColorSnapshot();
        pool.registerSceneDepthInputIfReady();
        pool.registerShadowDepthInputIfReady();
        pool.createDeclaredTargets(declaredTargets);
        return pool;
    }

    public VpfxNativeFrameTarget resolve(String targetId) {
        if (targetId == null || targetId.isBlank()) {
            return null;
        }
        return targets.get(targetId);
    }

    public VpfxNativeFrameTarget resolveInput(String targetId) {
        return resolveInput(targetId, false);
    }

    public VpfxNativeFrameTarget resolveInput(String targetId, boolean depthInput) {
        if (targetId == null || targetId.isBlank()) {
            return null;
        }

        if (depthInput) {
            if (isSceneDepthTarget(targetId)) {
                return targets.get("vulkanpostfx:scene_depth");
            }

            if (isShadowDepthTarget(targetId)) {
                return targets.get("vulkanpostfx:shadow_depth");
            }

            if ("minecraft:main".equals(targetId) || "minecraft:scene_color".equals(targetId)) {
                VpfxNativeFrameTarget capturedSceneDepth = targets.get("vulkanpostfx:scene_depth");
                if (capturedSceneDepth != null && capturedSceneDepth.depthView() != null) {
                    return capturedSceneDepth;
                }
                return targets.get("minecraft:main");
            }

            return targets.get(targetId);
        }

        if ("minecraft:main".equals(targetId) || "minecraft:scene_color".equals(targetId)) {
            return targets.get("minecraft:scene_color");
        }

        if (isSceneDepthTarget(targetId)) {
            return targets.get("vulkanpostfx:scene_depth");
        }

        if (isShadowDepthTarget(targetId)) {
            return targets.get("vulkanpostfx:shadow_depth");
        }

        return targets.get(targetId);
    }

    public VpfxNativeFrameTarget resolveOutput(String targetId) {
        VpfxNativeFrameTarget target = resolve(targetId);
        if (target == null || !target.outputCapable()) {
            return null;
        }
        return target;
    }

    public int targetCount() {
        return targets.size();
    }

    public void commitFrame() {
        if (committed) {
            return;
        }
        committed = true;
        for (VpfxNativeHistoryTargetStore.Binding binding : persistentBindings) {
            binding.commit();
        }
    }

    private void createSceneColorSnapshot() {
        TextureTarget snapshot = new TextureTarget(
                "VPFX native scene_color snapshot",
                mainWidth,
                mainHeight,
                false,
                DEFAULT_COLOR_FORMAT
        );
        targets.put("minecraft:scene_color", new VpfxNativeFrameTarget("minecraft:scene_color", snapshot, true, false, null));
        copyMainColorTo(snapshot);
    }

    private void registerSceneDepthInputIfReady() {
        SceneDepthCaptureTargets sceneDepth = SceneDepthCaptureTargets.get();
        if (!sceneDepth.isReady() || sceneDepth.getSceneDepthTarget() == null) {
            return;
        }

        RenderTarget depthTarget = sceneDepth.getSceneDepthTarget();
        if (depthTarget.getDepthTextureView() == null) {
            return;
        }

        targets.put(
                "vulkanpostfx:scene_depth",
                new VpfxNativeFrameTarget(
                        "vulkanpostfx:scene_depth",
                        depthTarget,
                        false,
                        false,
                        depthTarget.getDepthTextureView()
                )
        );
        targets.put(
                "minecraft:scene_depth",
                new VpfxNativeFrameTarget(
                        "minecraft:scene_depth",
                        depthTarget,
                        false,
                        false,
                        depthTarget.getDepthTextureView()
                )
        );

        if (!firstSceneDepthAliasLogged) {
            firstSceneDepthAliasLogged = true;
            VulkanPostFX.LOGGER.info(
                    "[{}] VPFX NR-2.4: registered native scene depth input aliases: vulkanpostfx:scene_depth, minecraft:scene_depth, size={}x{}",
                    VulkanPostFX.MOD_ID,
                    depthTarget.width,
                    depthTarget.height
            );
        }
    }

    private void registerShadowDepthInputIfReady() {
        ShadowRenderTargetsLite shadowTargets = ShadowRenderTargetsLite.get();
        if (!shadowTargets.isReady() || shadowTargets.getShadowDepthTarget() == null) {
            return;
        }

        RenderTarget shadowTarget = shadowTargets.getShadowDepthTarget();
        if (shadowTarget.getDepthTextureView() == null) {
            return;
        }

        targets.put(
                "vulkanpostfx:shadow_depth",
                new VpfxNativeFrameTarget(
                        "vulkanpostfx:shadow_depth",
                        shadowTarget,
                        false,
                        false,
                        shadowTarget.getDepthTextureView()
                )
        );
        targets.put(
                "minecraft:shadow_depth",
                new VpfxNativeFrameTarget(
                        "minecraft:shadow_depth",
                        shadowTarget,
                        false,
                        false,
                        shadowTarget.getDepthTextureView()
                )
        );

        if (!firstShadowDepthInputLogged) {
            firstShadowDepthInputLogged = true;
            VulkanPostFX.LOGGER.info(
                    "[{}] VPFX NR-2.4: registered native shadow depth input aliases: vulkanpostfx:shadow_depth, minecraft:shadow_depth, size={}x{}",
                    VulkanPostFX.MOD_ID,
                    shadowTarget.width,
                    shadowTarget.height
            );
        }
    }

    private static boolean isSceneDepthTarget(String targetId) {
        return "vulkanpostfx:scene_depth".equals(targetId)
                || "minecraft:scene_depth".equals(targetId);
    }

    private static boolean isShadowDepthTarget(String targetId) {
        return "vulkanpostfx:shadow_depth".equals(targetId)
                || "minecraft:shadow_depth".equals(targetId);
    }

    private void createDeclaredTargets(Map<String, VpfxTargetDefinition> declaredTargets) {
        if (declaredTargets == null || declaredTargets.isEmpty()) {
            return;
        }

        for (VpfxTargetDefinition definition : declaredTargets.values()) {
            String id = definition.getId();
            if (id == null || id.isBlank() || targets.containsKey(id)) {
                continue;
            }

            double scale = definition.getScale().orElse(1.0D);
            int width = Math.max(1, (int) Math.round(mainWidth * scale));
            int height = Math.max(1, (int) Math.round(mainHeight * scale));

            if (definition.isPersistent()) {
                createPersistentTarget(definition, id, width, height, scale);
            } else {
                createTransientTarget(definition, id, width, height, scale);
            }
        }
    }

    private void createTransientTarget(
            VpfxTargetDefinition definition,
            String id,
            int width,
            int height,
            double scale
    ) {
        TextureTarget target = new TextureTarget(
                "VPFX native target " + id,
                width,
                height,
                definition.isUseDepth(),
                DEFAULT_COLOR_FORMAT
        );
        targets.put(id, new VpfxNativeFrameTarget(id, target, true, true, null));

        VulkanPostFX.LOGGER.info(
                "[{}] VPFX NR-2.2: allocated transient native frame target: id={}, size={}x{}, scale={}, useDepth={}",
                VulkanPostFX.MOD_ID,
                id,
                width,
                height,
                scale,
                definition.isUseDepth()
        );
    }

    private void createPersistentTarget(
            VpfxTargetDefinition definition,
            String id,
            int width,
            int height,
            double scale
    ) {
        VpfxNativeHistoryTargetStore.Binding binding = VpfxNativeHistoryTargetStore.acquire(
                runtimeNamespace,
                id,
                width,
                height,
                definition.isUseDepth(),
                DEFAULT_COLOR_FORMAT,
                definition.isHistory(),
                definition.isPingPong()
        );
        persistentBindings.add(binding);

        targets.put(id, new VpfxNativeFrameTarget(id, binding.writeTarget(), false, true, null));

        if (binding.hasHistoryInput()) {
            String historyAlias = VpfxNativeHistoryTargetStore.historyAlias(id);
            targets.put(historyAlias, new VpfxNativeFrameTarget(
                    historyAlias,
                    binding.readTarget(),
                    false,
                    false,
                    binding.readTarget().getColorTextureView()
            ));
        }

        VulkanPostFX.LOGGER.info(
                "[{}] VPFX NR-2.2: attached persistent native frame target: id={}, size={}x{}, scale={}, useDepth={}, history={}, pingPong={}",
                VulkanPostFX.MOD_ID,
                id,
                width,
                height,
                scale,
                definition.isUseDepth(),
                definition.isHistory(),
                definition.isPingPong()
        );
    }

    private void copyMainColorTo(RenderTarget destination) {
        GpuTexture sourceTexture = mainTarget.getColorTexture();
        GpuTexture destinationTexture = destination.getColorTexture();

        if (sourceTexture == null || destinationTexture == null
                || sourceTexture.isClosed() || destinationTexture.isClosed()) {
            throw new IllegalStateException("main or scene_color texture is unavailable");
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(
                sourceTexture,
                destinationTexture,
                0, 0, 0, 0, 0,
                Math.min(mainWidth, destination.width),
                Math.min(mainHeight, destination.height)
        );
    }

    @Override
    public void close() {
        for (VpfxNativeFrameTarget target : targets.values()) {
            target.destroyIfOwned();
        }
        targets.clear();
        persistentBindings.clear();
    }
}
