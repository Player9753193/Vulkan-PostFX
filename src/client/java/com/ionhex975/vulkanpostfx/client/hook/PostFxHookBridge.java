package com.ionhex975.vulkanpostfx.client.hook;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.diagnostics.VpfxFailureDiagnostics;
import com.ionhex975.vulkanpostfx.client.effect.PostFxEffectDefinition;
import com.ionhex975.vulkanpostfx.client.effect.PostFxEffectRegistry;
import com.ionhex975.vulkanpostfx.client.mixin.GameRendererAccessor;
import com.ionhex975.vulkanpostfx.client.mixin.PostChainAccessor;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxCapabilityResolver;
import com.ionhex975.vulkanpostfx.client.postfx.PostFxExternalTargetIds;
import com.ionhex975.vulkanpostfx.client.postfx.SceneDepthCaptureTargets;
import com.ionhex975.vulkanpostfx.client.shadow.ShadowFrameCoordinator;
import com.ionhex975.vulkanpostfx.client.shadow.ShadowFrameState;
import com.ionhex975.vulkanpostfx.client.shadow.ShadowRendererLite;
import com.ionhex975.vulkanpostfx.client.shadow.WorldShadowUniformBuffer;
import com.ionhex975.vulkanpostfx.client.state.PostFxRuntimeState;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.VpfxBackendSelectionResult;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.VpfxNativeExecutionResult;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.VpfxNativeFullscreenExecutor;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.VpfxNativeRuntimeSupport;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.VpfxNativeTransientTargetDryRun;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.VpfxNativeUserShaderDryRun;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

import java.util.Set;

public final class PostFxHookBridge {
    private static boolean firstWorldFrameLogged;
    private static boolean firstPostSlotLogged;
    private static Boolean lastAppliedDebugState;
    private static boolean firstWorldTailLogged;
    private static boolean firstPreHandExternalLogged;
    private static int nativeDiagnosticFrameCounter;
    private static int nativeDiagnosticLastSummaryFrame = -1;
    private static boolean externalChainUnavailableLogged;
    private static final int NATIVE_DIAGNOSTIC_SUMMARY_INTERVAL = 60;

    private PostFxHookBridge() {
    }

    public static void onWorldRenderHead(
            Minecraft minecraft,
            LevelRenderer levelRenderer,
            DeltaTracker deltaTracker,
            CameraRenderState cameraState,
            boolean renderOutline,
            boolean shouldRenderSky
    ) {
        PostFxRuntimeState.markWorldRenderObserved();
        PostFxRuntimeState.resetWorldStageExternalEffectApplied();
        PostFxRuntimeState.resetPerFrameState();

        ShadowRendererLite.prepareFrame(minecraft, cameraState);
        ShadowFrameCoordinator.syncFrame(minecraft, deltaTracker, cameraState);
        ShadowRendererLite.executeShadowPassLite(minecraft, levelRenderer);

        if (!firstWorldFrameLogged) {
            firstWorldFrameLogged = true;

            String backend = detectBackendName();
            PostFxRuntimeState.setBackendName(backend);

            RenderTarget mainTarget = minecraft.gameRenderer.mainRenderTarget();
            int width = mainTarget.width;
            int height = mainTarget.height;
            boolean improvedTransparency = minecraft.options.improvedTransparency().get();

            ShadowFrameState shadowState = ShadowFrameState.get();
            Vector3f sunDir = shadowState.getSunDirection();

            VulkanPostFX.LOGGER.info(
                    "[{}] World render observed (HEAD), backend={}, size={}x{}, improvedTransparency={}, renderOutline={}, shouldRenderSky={}, shadowStateValid={}, shadowPassEnabled={}, shadowTargetReady={}, shadowPassExecuted={}, shadowCastersRendered={}, shadowMapSize={}, terrainShadowDistance={}, entityShadowDistance={}, shadowAngle={}, sunDir=({}, {}, {})",
                    VulkanPostFX.MOD_ID,
                    backend,
                    width,
                    height,
                    improvedTransparency,
                    renderOutline,
                    shouldRenderSky,
                    shadowState.isValid(),
                    shadowState.isShadowPassEnabled(),
                    shadowState.isShadowTargetReady(),
                    shadowState.wasShadowPassExecuted(),
                    shadowState.wereShadowCastersRendered(),
                    shadowState.getShadowMapSize(),
                    shadowState.getTerrainShadowDistance(),
                    shadowState.getEntityShadowDistance(),
                    Math.round(shadowState.getShadowAngle() * 1000.0F) / 1000.0F,
                    Math.round(sunDir.x * 1000.0F) / 1000.0F,
                    Math.round(sunDir.y * 1000.0F) / 1000.0F,
                    Math.round(sunDir.z * 1000.0F) / 1000.0F
            );
        }
    }

    public static void onWorldRenderTail(Minecraft minecraft) {
        SceneDepthCaptureTargets.get().captureFromMainTarget(minecraft);

        if (PostFxRuntimeState.consumePendingTransientAllocationCheck()) {
            VpfxNativeTransientTargetDryRun.runOnRenderThread(minecraft);
        }

        if (PostFxRuntimeState.consumePendingUserShaderPipelineCreate()) {
            VpfxNativeUserShaderDryRun.runPendingPipelineCreateOnRenderThread();
        }

        if (!firstWorldTailLogged) {
            firstWorldTailLogged = true;

            RenderTarget mainTarget = minecraft.gameRenderer.mainRenderTarget();
            ShadowFrameState shadowState = ShadowFrameState.get();

            VulkanPostFX.LOGGER.info(
                    "[{}] World render finished (TAIL), mainTarget={}x{}, shadowPassExecuted={}, shadowCastersRendered={}, shadowDepthMirrored={}",
                    VulkanPostFX.MOD_ID,
                    mainTarget.width,
                    mainTarget.height,
                    shadowState.wasShadowPassExecuted(),
                    shadowState.wereShadowCastersRendered(),
                    shadowState.wasShadowDepthMirrored()
            );
        }
    }

    public static void onWorldPostEffectBeforeHand(
            Minecraft minecraft,
            GraphicsResourceAllocator resourceAllocator
    ) {
        if (!PostFxRuntimeState.isDebugEffectEnabled()) {
            return;
        }

        Identifier externalId = PostFxRuntimeState.getActiveExternalPostEffectId();
        if (externalId == null) {
            return;
        }

        if (PostFxRuntimeState.isExternalPackMarkedFailed()) {
            return;
        }

        // Native direct does not need the generated Minecraft PostChain to be loaded.
        // Run it before any PostChain lookup. Otherwise hiding/deferred runtime resource
        // packs would make every native-compatible pack look unavailable and force vanilla.
        if (VpfxNativeRuntimeSupport.isExecuteEnabled()) {
            VpfxNativeExecutionResult nativeResult = null;
            RuntimeException nativeException = null;

            try {
                nativeResult = VpfxNativeTransientTargetDryRun.attemptDiagnosticDraw(minecraft);
            } catch (Throwable t) {
                nativeException = new RuntimeException("native direct runtime threw before PostChain fallback", t);
            }

            if (nativeResult != null && nativeResult.nativeSucceeded()) {
                PostFxRuntimeState.clearNativeRuntimeFallback();
                PostFxRuntimeState.markWorldStageExternalEffectApplied();
                PostFxRuntimeState.markSkipPostChainThisFrame(true,
                        "native diagnostic passthrough succeeded");
                PostFxRuntimeState.incrementNativeDiagnosticDrawSuccess();
                PostFxRuntimeState.incrementPostChainSkipped();

                int frame = ++nativeDiagnosticFrameCounter;
                boolean firstSuccess = nativeDiagnosticLastSummaryFrame < 0;
                boolean intervalElapsed =
                        frame - nativeDiagnosticLastSummaryFrame >= NATIVE_DIAGNOSTIC_SUMMARY_INTERVAL;

                if (firstSuccess || intervalElapsed) {
                    if (firstSuccess) {
                        VulkanPostFX.LOGGER.info(
                                "[{}] VPFX NR-1F-D: attempting native diagnostic draw before external PostChain",
                                VulkanPostFX.MOD_ID
                        );
                    }

                    nativeDiagnosticLastSummaryFrame = frame;

                    VulkanPostFX.LOGGER.info(
                            "[{}] VPFX NR-1F-D summary (frame {}):"
                                    + " native diagnostic draw succeeded=true"
                                    + " | postchain skipped frames={}"
                                    + " | user shader native execution={}"
                                    + " | builtin passthrough only={}"
                                    + " | actual pipeline={}"
                                    + " | failureStage={}"
                                    + " | failureMessage={}"
                                    + " | builtinFallbackAttempted={}"
                                    + " | builtinFallbackSucceeded={}"
                                    + " | postChainFallbackExpected={}"
                                    + " | {}",
                            VulkanPostFX.MOD_ID,
                            frame,
                            PostFxRuntimeState.postChainSkippedFrameCount(),
                            nativeResult.userShaderNativeExecution(),
                            nativeResult.builtinPassthroughOnly(),
                            nativeResult.actualPipeline(),
                            nativeResult.failureStage(),
                            nativeResult.failureMessage(),
                            nativeResult.builtinFallbackAttempted(),
                            nativeResult.builtinFallbackSucceeded(),
                            nativeResult.postChainFallbackExpected(),
                            PostFxRuntimeState.nativeDiagnosticSummary()
                    );
                }

                VpfxNativeFullscreenExecutor.logDiagnosticSummaryOnce(nativeResult);
                return;
            }

            if (!PostFxRuntimeState.isSkipPostChainThisFrame()) {
                String fallbackReason;
                if (nativeException != null) {
                    fallbackReason = nativeException.getMessage();
                } else if (nativeResult != null) {
                    fallbackReason = "native diagnostic draw failed: stage="
                            + nativeResult.failureStage()
                            + ", message="
                            + nativeResult.failureMessage()
                            + ", fallbackReason="
                            + nativeResult.fallbackReason();
                } else {
                    fallbackReason = "native diagnostic draw failed before producing a result";
                }

                PostFxRuntimeState.fallbackActiveRuntimeBackendToPostChain(
                        externalId,
                        VpfxBackendSelectionResult.STAGE_FRAME_EXECUTION,
                        fallbackReason
                );
                PostFxRuntimeState.markSkipPostChainThisFrame(false,
                        "native diagnostic did not succeed; continuing through minecraft_postchain");
                PostFxRuntimeState.incrementNativeDiagnosticDrawFailure();

                VulkanPostFX.LOGGER.warn(
                        "[{}] VPFX native direct runtime failed and was disabled for this active pack. "
                                + "The pack remains active through minecraft_postchain fallback. externalPostEffectId={}, stage={}, reason={}",
                        VulkanPostFX.MOD_ID,
                        externalId,
                        VpfxBackendSelectionResult.STAGE_FRAME_EXECUTION,
                        fallbackReason
                );

                if (nativeResult != null) {
                    VulkanPostFX.LOGGER.info(
                            "[{}] VPFX NR-1F-D: native diagnostic draw succeeded = false",
                            VulkanPostFX.MOD_ID
                    );
                    VulkanPostFX.LOGGER.info(
                            "[{}] VPFX NR-1F-D: fallback used                       = true",
                            VulkanPostFX.MOD_ID
                    );
                    VulkanPostFX.LOGGER.info(
                            "[{}] VPFX NR-1F-D: native backend replacement           = false",
                            VulkanPostFX.MOD_ID
                    );
                    VulkanPostFX.LOGGER.info(
                            "[{}] VPFX NR-1F-D: user shader native execution         = {}",
                            VulkanPostFX.MOD_ID,
                            nativeResult.userShaderNativeExecution()
                    );
                    VulkanPostFX.LOGGER.info(
                            "[{}] VPFX NR-1F-D: builtin passthrough only             = {}",
                            VulkanPostFX.MOD_ID,
                            nativeResult.builtinPassthroughOnly()
                    );
                    VulkanPostFX.LOGGER.info(
                            "[{}] VPFX NR-1F-D: actual pipeline                      = {}",
                            VulkanPostFX.MOD_ID,
                            nativeResult.actualPipeline()
                    );
                    VulkanPostFX.LOGGER.info(
                            "[{}] VPFX NR-1F-D: failure stage                         = {}",
                            VulkanPostFX.MOD_ID,
                            nativeResult.failureStage()
                    );
                    VulkanPostFX.LOGGER.info(
                            "[{}] VPFX NR-1F-D: failure message                       = {}",
                            VulkanPostFX.MOD_ID,
                            nativeResult.failureMessage()
                    );
                    VulkanPostFX.LOGGER.info(
                            "[{}] VPFX NR-1F-D: pipeline fallback reason              = {}",
                            VulkanPostFX.MOD_ID,
                            nativeResult.pipelineFallbackReason()
                    );
                    VulkanPostFX.LOGGER.info(
                            "[{}] VPFX NR-1F-D: builtin fallback attempted            = {}",
                            VulkanPostFX.MOD_ID,
                            nativeResult.builtinFallbackAttempted()
                    );
                    VulkanPostFX.LOGGER.info(
                            "[{}] VPFX NR-1F-D: builtin fallback succeeded            = {}",
                            VulkanPostFX.MOD_ID,
                            nativeResult.builtinFallbackSucceeded()
                    );
                    VulkanPostFX.LOGGER.info(
                            "[{}] VPFX NR-1F-D: postChain fallback expected           = {}",
                            VulkanPostFX.MOD_ID,
                            nativeResult.postChainFallbackExpected()
                    );
                    VulkanPostFX.LOGGER.info(
                            "[{}] VPFX NR-1F-D: {}",
                            VulkanPostFX.MOD_ID,
                            PostFxRuntimeState.nativeDiagnosticSummary()
                    );

                    VpfxNativeFullscreenExecutor.logDiagnosticSummaryOnce(nativeResult);
                } else {
                    VulkanPostFX.LOGGER.warn(
                            "[{}] VPFX native direct runtime threw; continuing through Minecraft PostChain fallback. externalPostEffectId={}, reason={}",
                            VulkanPostFX.MOD_ID,
                            externalId,
                            fallbackReason,
                            nativeException
                    );
                }
            }
        }

        if (PostFxRuntimeState.isSkipPostChainThisFrame()) {
            PostFxRuntimeState.markWorldStageExternalEffectApplied();

            VulkanPostFX.LOGGER.info(
                    "[{}] VPFX NR-1F-D: external PostChain skipped this frame = true",
                    VulkanPostFX.MOD_ID
            );
            VulkanPostFX.LOGGER.info(
                    "[{}] VPFX NR-1F-D: skip reason                           = {}",
                    VulkanPostFX.MOD_ID,
                    PostFxRuntimeState.skipPostChainReasonForLog()
            );
            return;
        }

        PostChain external = getExternalPostChainOrNull(
                minecraft,
                externalId,
                "world-stage pre-hand lookup"
        );
        if (external == null) {
            return;
        }

        if (shouldSuppressShadowDepthDrivenPost(external)) {
            PostFxRuntimeState.markWorldStageExternalEffectApplied();
            return;
        }

        try {
            external.process(minecraft.gameRenderer.mainRenderTarget(), resourceAllocator);

            if (PostFxRuntimeState.isSkipPostChainThisFrame()
                    || PostFxRuntimeState.nativeDiagnosticDrawSucceededThisFrame()) {
                PostFxRuntimeState.markWorldStageExternalEffectApplied();

                VulkanPostFX.LOGGER.info(
                        "[{}] VPFX NR-1F-D: external PostChain skipped this frame = true",
                        VulkanPostFX.MOD_ID
                );
                VulkanPostFX.LOGGER.info(
                        "[{}] VPFX NR-1F-D: skip reason                           = {}",
                        VulkanPostFX.MOD_ID,
                        PostFxRuntimeState.skipPostChainReasonForLog()
                );
                return;
            }

            if (VpfxNativeRuntimeSupport.isExecuteEnabled()) {
                VulkanPostFX.LOGGER.info(
                        "[{}] VPFX NR-1F-D: external PostChain executed          = true",
                        VulkanPostFX.MOD_ID
                );
            }

            if (PostFxRuntimeState.isExternalPackMarkedFailed()) {
                VulkanPostFX.LOGGER.info(
                        "[{}] External VPFX process returned after internal failure; "
                                + "skipping world-stage applied mark. externalPostEffectId={}",
                        VulkanPostFX.MOD_ID,
                        externalId
                );
            } else {
                PostFxRuntimeState.markWorldStageExternalEffectApplied();
            }
        } catch (Exception e) {
            disableExternalPackAndReturnToVanilla(
                    minecraft,
                    externalId,
                    "world-stage pre-hand process",
                    e
            );
        }

        if (!firstPreHandExternalLogged && !PostFxRuntimeState.isExternalPackMarkedFailed()) {
            firstPreHandExternalLogged = true;
            VulkanPostFX.LOGGER.info(
                    "[{}] Applied external world-stage post chain before hand rendering: {}",
                    VulkanPostFX.MOD_ID,
                    externalId
            );
        }
    }

    public static void onPostEffectSlot(Minecraft minecraft, GameRenderer gameRenderer) {
        PostFxRuntimeState.markPostSlotObserved();

        if (!firstPostSlotLogged) {
            firstPostSlotLogged = true;

            RenderTarget mainTarget = minecraft.gameRenderer.mainRenderTarget();
            Identifier currentPostEffect = gameRenderer.currentPostEffect();

            VulkanPostFX.LOGGER.info(
                    "[{}] PostFX slot observed, backend={}, mainTarget={}x{}, currentPostEffect={}",
                    VulkanPostFX.MOD_ID,
                    PostFxRuntimeState.getBackendName(),
                    mainTarget.width,
                    mainTarget.height,
                    currentPostEffect == null ? "none" : currentPostEffect
            );
        }

        applyDesiredDebugEffect(minecraft, gameRenderer);
    }

    private static void applyDesiredDebugEffect(Minecraft minecraft, GameRenderer gameRenderer) {
        boolean desiredEnabled = PostFxRuntimeState.isDebugEffectEnabled();
        boolean reapplyRequested = PostFxRuntimeState.consumeReapplyRequest();

        if (desiredEnabled && PostFxRuntimeState.isExternalPackMarkedFailed()) {
            GameRendererAccessor accessor = (GameRendererAccessor) gameRenderer;
            accessor.vulkanpostfx$setPostEffectId(null);
            accessor.vulkanpostfx$setEffectActive(false);
            lastAppliedDebugState = false;

            Identifier failedId = PostFxRuntimeState.getFailedExternalPostEffectId();
            VulkanPostFX.LOGGER.warn(
                    "[{}] External VPFX pack is marked as failed ({}). "
                            + "Staying in vanilla rendering. Fix and reload the pack, then toggle F8 twice to retry.",
                    VulkanPostFX.MOD_ID,
                    failedId
            );
            return;
        }

        if (!reapplyRequested && lastAppliedDebugState != null && lastAppliedDebugState == desiredEnabled) {
            return;
        }

        GameRendererAccessor accessor = (GameRendererAccessor) gameRenderer;

        if (!desiredEnabled) {
            accessor.vulkanpostfx$setPostEffectId(null);
            accessor.vulkanpostfx$setEffectActive(false);
            lastAppliedDebugState = false;

            if (reapplyRequested) {
                VulkanPostFX.LOGGER.info(
                        "[{}] Reapplied PostFX state after resource reload: disabled",
                        VulkanPostFX.MOD_ID
                );
            } else {
                VulkanPostFX.LOGGER.info("[{}] Debug post effect disabled", VulkanPostFX.MOD_ID);
            }
            return;
        }

        if (PostFxRuntimeState.isWorldStageExternalEffectApplied()) {
            accessor.vulkanpostfx$setPostEffectId(null);
            accessor.vulkanpostfx$setEffectActive(false);
            lastAppliedDebugState = true;

            if (reapplyRequested) {
                VulkanPostFX.LOGGER.info(
                        "[{}] Reapplied PostFX state after resource reload: external world-stage effect handled before hand render",
                        VulkanPostFX.MOD_ID
                );
            }
            return;
        }

        Identifier externalId = PostFxRuntimeState.getActiveExternalPostEffectId();
        if (externalId != null && !PostFxRuntimeState.isExternalPackMarkedFailed()) {
            PostChain external = getExternalPostChainOrNull(
                    minecraft,
                    externalId,
                    "post-slot suppression lookup"
            );
            if (external == null) {
                lastAppliedDebugState = desiredEnabled;
                return;
            }

            if (shouldSuppressShadowDepthDrivenPost(external)) {
                accessor.vulkanpostfx$setPostEffectId(null);
                accessor.vulkanpostfx$setEffectActive(false);
                lastAppliedDebugState = true;
                return;
            }
        }

        Identifier chosenEffect = chooseCurrentEffect(minecraft);
        if (chosenEffect == null) {
            disableRendererPostEffect(gameRenderer);
            lastAppliedDebugState = desiredEnabled;

            VulkanPostFX.LOGGER.warn(
                    "[{}] No safe PostFX chain is currently available. VPFX remains selected, but vanilla rendering is used until a resource reload exposes the generated chain.",
                    VulkanPostFX.MOD_ID
            );
            return;
        }

        accessor.vulkanpostfx$setPostEffectId(chosenEffect);
        accessor.vulkanpostfx$setEffectActive(true);
        lastAppliedDebugState = true;

        if (reapplyRequested) {
            VulkanPostFX.LOGGER.info(
                    "[{}] Reapplied PostFX state after resource reload: {}",
                    VulkanPostFX.MOD_ID,
                    chosenEffect
            );
        } else {
            VulkanPostFX.LOGGER.info(
                    "[{}] Debug post effect enabled: {}",
                    VulkanPostFX.MOD_ID,
                    chosenEffect
            );
        }
    }

    private static Identifier chooseCurrentEffect(Minecraft minecraft) {
        Identifier externalId = PostFxRuntimeState.getActiveExternalPostEffectId();

        VulkanPostFX.LOGGER.info(
                "[{}] chooseCurrentEffect: activeExternalPostEffectId={}, activeEffectKey={}",
                VulkanPostFX.MOD_ID,
                externalId,
                PostFxRuntimeState.getActiveEffectKey()
        );

        if (externalId != null && !PostFxRuntimeState.isExternalPackMarkedFailed()) {
            PostChain external = getExternalPostChainOrNull(
                    minecraft,
                    externalId,
                    "post-slot effect selection"
            );

            VulkanPostFX.LOGGER.info(
                    "[{}] External post chain lookup: id={}, found={}",
                    VulkanPostFX.MOD_ID,
                    externalId,
                    external != null
            );

            if (external != null) {
                VulkanPostFX.LOGGER.info(
                        "[{}] External ZIP post chain is available: {}",
                        VulkanPostFX.MOD_ID,
                        externalId
                );
                return externalId;
            }

            VulkanPostFX.LOGGER.warn(
                    "[{}] External ZIP post chain is not available yet; keeping VPFX pack active and using vanilla for now: {}",
                    VulkanPostFX.MOD_ID,
                    externalId
            );
            return null;
        }

        String effectKey = PostFxRuntimeState.getActiveEffectKey();
        PostFxEffectDefinition definition = PostFxEffectRegistry.get(effectKey);

        if (definition == null) {
            VulkanPostFX.LOGGER.warn(
                    "[{}] Effect key '{}' is not registered, falling back to minecraft:invert",
                    VulkanPostFX.MOD_ID,
                    effectKey
            );
            return Identifier.withDefaultNamespace("invert");
        }

        PostChain custom = getPostChainOrNull(
                minecraft,
                definition.primaryId(),
                "builtin primary lookup"
        );

        VulkanPostFX.LOGGER.info(
                "[{}] Builtin post chain lookup: effectKey={}, id={}, found={}",
                VulkanPostFX.MOD_ID,
                effectKey,
                definition.primaryId(),
                custom != null
        );

        if (custom != null) {
            VulkanPostFX.LOGGER.info(
                    "[{}] Builtin effect '{}' is available: {}",
                    VulkanPostFX.MOD_ID,
                    definition.displayName(),
                    definition.primaryId()
            );
            return definition.primaryId();
        }

        VulkanPostFX.LOGGER.warn(
                "[{}] Builtin effect '{}' failed to load primary chain {}, falling back to {}",
                VulkanPostFX.MOD_ID,
                definition.displayName(),
                definition.primaryId(),
                definition.fallbackId()
        );
        return definition.fallbackId();
    }

    private static PostChain getExternalPostChainOrNull(
            Minecraft minecraft,
            Identifier externalId,
            String stage
    ) {
        if (externalId == null) {
            return null;
        }

        try {
            PostChain chain = minecraft.getShaderManager().getPostChain(externalId, LevelTargetBundle.MAIN_TARGETS);
            if (chain != null) {
                externalChainUnavailableLogged = false;
                return chain;
            }

            if (!externalChainUnavailableLogged) {
                externalChainUnavailableLogged = true;
                VulkanPostFX.LOGGER.warn(
                        "[{}] External VPFX PostChain is not available yet at stage '{}'. "
                                + "Keeping the pack active instead of marking it failed. "
                                + "This usually means the generated runtime resource pack has not been loaded by Minecraft's resource manager yet. "
                                + "externalPostEffectId={}, backend={}, nativeFallbackActive={}, runtimeFallback={}",
                        VulkanPostFX.MOD_ID,
                        stage,
                        externalId,
                        PostFxRuntimeState.getActiveRuntimeBackendId(),
                        PostFxRuntimeState.isNativeRuntimeFallbackActive(),
                        PostFxRuntimeState.getNativeRuntimeFallbackSummary()
                );
            }
            return null;
        } catch (Throwable t) {
            Exception diagnosticException = asException(t);
            VpfxFailureDiagnostics.write(stage, diagnosticException);

            VulkanPostFX.LOGGER.error(
                    "[{}] Failed to query external VPFX PostChain safely. "
                            + "Keeping the pack active; vanilla rendering is used for this frame. stage={}, postEffectId={}",
                    VulkanPostFX.MOD_ID,
                    stage,
                    externalId,
                    t
            );
            return null;
        }
    }

    private static PostChain getPostChainOrNull(
            Minecraft minecraft,
            Identifier postEffectId,
            String stage
    ) {
        if (postEffectId == null) {
            return null;
        }

        try {
            return minecraft.getShaderManager().getPostChain(postEffectId, LevelTargetBundle.MAIN_TARGETS);
        } catch (Throwable t) {
            Exception diagnosticException = asException(t);
            VpfxFailureDiagnostics.write(stage, diagnosticException);

            VulkanPostFX.LOGGER.error(
                    "[{}] Failed to query PostChain safely. stage={}, postEffectId={}",
                    VulkanPostFX.MOD_ID,
                    stage,
                    postEffectId,
                    t
            );
            return null;
        }
    }

    private static void disableExternalPackAndReturnToVanilla(
            Minecraft minecraft,
            Identifier externalId,
            String stage,
            Exception exception
    ) {
        if (externalId != null) {
            PostFxRuntimeState.setFailedExternalPostEffectId(externalId);
        }

        PostFxRuntimeState.setDebugEffectEnabled(false);
        PostFxRuntimeState.requestReapply();
        PostFxRuntimeState.markSkipPostChainThisFrame(false,
                "external VPFX chain unavailable; vanilla fallback");

        disableRendererPostEffect(minecraft.gameRenderer);
        lastAppliedDebugState = false;

        if (exception != null) {
            VpfxFailureDiagnostics.write(stage, exception);
            VulkanPostFX.LOGGER.error(
                    "[{}] External VPFX chain failed at stage '{}'. VPFX has been disabled and vanilla rendering remains active. externalPostEffectId={}",
                    VulkanPostFX.MOD_ID,
                    stage,
                    externalId,
                    exception
            );
        } else {
            VulkanPostFX.LOGGER.warn(
                    "[{}] External VPFX chain is unavailable at stage '{}'. VPFX has been disabled and vanilla rendering remains active. externalPostEffectId={}",
                    VulkanPostFX.MOD_ID,
                    stage,
                    externalId
            );
        }
    }

    private static void disableRendererPostEffect(GameRenderer gameRenderer) {
        GameRendererAccessor accessor = (GameRendererAccessor) gameRenderer;
        accessor.vulkanpostfx$setPostEffectId(null);
        accessor.vulkanpostfx$setEffectActive(false);
    }

    private static Exception asException(Throwable throwable) {
        if (throwable instanceof Exception exception) {
            return exception;
        }
        return new RuntimeException(throwable);
    }

    private static String detectBackendName() {
        try {
            GpuDevice device = RenderSystem.tryGetDevice();
            if (device == null) {
                return "device-not-ready";
            }

            return device.getDeviceInfo().backendName();
        } catch (Throwable t) {
            return "unresolved";
        }
    }

    private static boolean shouldSuppressShadowDepthDrivenPost(PostChain chain) {
        Set<Identifier> externalTargets = ((PostChainAccessor) chain).vulkanpostfx$getExternalTargets();
        if (externalTargets == null || !externalTargets.contains(PostFxExternalTargetIds.SHADOW_DEPTH)) {
            return false;
        }

        if (!(new VpfxCapabilityResolver().resolve().isShadowDepth())) {
            return true;
        }

        return WorldShadowUniformBuffer.isWorldShadowEnabled();
    }
}