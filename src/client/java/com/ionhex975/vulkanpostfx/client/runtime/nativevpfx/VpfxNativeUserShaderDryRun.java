package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.pack.ActiveShaderPackManager;
import com.ionhex975.vulkanpostfx.client.pack.ShaderPackContainer;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxNativePackDefinition;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxPackManifest;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxPassDefinition;
import com.ionhex975.vulkanpostfx.client.runtime.zip.RuntimeZipPackState;
import com.ionhex975.vulkanpostfx.client.shader.VpfxShaderSourcePreprocessor;
import com.ionhex975.vulkanpostfx.client.shader.include.VpfxShaderIncludeException;
import com.ionhex975.vulkanpostfx.client.state.PostFxRuntimeState;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.ZipFile;

public final class VpfxNativeUserShaderDryRun {

	private static final BindGroupLayout BUILTIN_SAMPLER_BIND_GROUP =
			BindGroupLayout.builder().withSampler("Sampler0").build();

	private static final BindGroupLayout USER_SAMPLER_BIND_GROUP =
			BindGroupLayout.builder().withSampler("InSampler").build();

	private static String pendingShaderLocator;
	private static String pendingVertexHash;
	private static String pendingFragmentHash;
	private static VpfxNativePipelineKey pendingUserKey;
	private static Identifier pendingVertexShaderId;
	private static Identifier pendingFragmentShaderId;

	private VpfxNativeUserShaderDryRun() {
	}

	public static void run(VpfxPackManifest manifest, VpfxNativeResolvedGraph resolvedGraph) {
		if (!VpfxNativeRuntimeSupport.isExecuteEnabled()) {
			return;
		}

		ShaderPackContainer activePack = ActiveShaderPackManager.getActivePack();
		if (activePack == null || !activePack.isVpfxNativePack()) {
			return;
		}

		VpfxNativePackDefinition vpfxDef = activePack.vpfxDefinition();
		if (vpfxDef == null || vpfxDef.getGraph() == null) {
			return;
		}

		if (vpfxDef.getGraph().getPasses().isEmpty()) {
			return;
		}

		VpfxPassDefinition passDef = vpfxDef.getGraph().getPasses().get(0);
		String vertexRef = passDef.getVertexShader();
		String fragmentRef = passDef.getFragmentShader();

		if (vertexRef == null || vertexRef.isBlank() || fragmentRef == null || fragmentRef.isBlank()) {
			VulkanPostFX.LOGGER.warn(
					"[{}] VPFX NR-1F-A: user shader resolve skipped — vertex or fragment shader reference is blank. "
							+ "vertex={}, fragment={}",
					VulkanPostFX.MOD_ID,
					vertexRef,
					fragmentRef
			);
			return;
		}

		String vertexPath = toShaderZipPath(vertexRef, true);
		String fragmentPath = toShaderZipPath(fragmentRef, false);

		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-A: user shader resolve dry-run starting",
				VulkanPostFX.MOD_ID
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-A: pack id                             = {}",
				VulkanPostFX.MOD_ID,
				manifest.getPackId()
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-A: pass id                             = {}",
				VulkanPostFX.MOD_ID,
				passDef.identityOrIndex(0)
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-A: vertex shader reference             = {}",
				VulkanPostFX.MOD_ID,
				vertexRef
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-A: fragment shader reference           = {}",
				VulkanPostFX.MOD_ID,
				fragmentRef
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-A: resolved vertex shader path         = {}",
				VulkanPostFX.MOD_ID,
				vertexPath
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-A: resolved fragment shader path       = {}",
				VulkanPostFX.MOD_ID,
				fragmentPath
		);

		String vertexSource = null;
		String fragmentSource = null;
		boolean vertexLoaded = false;
		boolean fragmentLoaded = false;
		boolean includePreprocessApplied = false;

		try (ZipFile zipFile = new ZipFile(vpfxDef.getZipPath().toFile())) {
			if (zipFile.getEntry(vertexPath) == null) {
				VulkanPostFX.LOGGER.warn(
						"[{}] VPFX NR-1F-A: vertex shader file not found in zip: {}",
						VulkanPostFX.MOD_ID,
						vertexPath
				);
			} else {
				VpfxShaderSourcePreprocessor preprocessor = new VpfxShaderSourcePreprocessor(zipFile);
				try {
					vertexSource = preprocessor.preprocess(vertexPath);
					vertexLoaded = true;
					includePreprocessApplied = true;
				} catch (VpfxShaderIncludeException e) {
					VulkanPostFX.LOGGER.warn(
							"[{}] VPFX NR-1F-A: vertex shader preprocess failed [{}][{}]: {}",
							VulkanPostFX.MOD_ID,
							e.getCode(),
							e.getPath(),
							e.getMessage()
					);
				}
			}

			if (zipFile.getEntry(fragmentPath) == null) {
				VulkanPostFX.LOGGER.warn(
						"[{}] VPFX NR-1F-A: fragment shader file not found in zip: {}",
						VulkanPostFX.MOD_ID,
						fragmentPath
				);
			} else {
				VpfxShaderSourcePreprocessor preprocessor = new VpfxShaderSourcePreprocessor(zipFile);
				try {
					fragmentSource = preprocessor.preprocess(fragmentPath);
					fragmentLoaded = true;
					includePreprocessApplied = includePreprocessApplied || true;
				} catch (VpfxShaderIncludeException e) {
					VulkanPostFX.LOGGER.warn(
							"[{}] VPFX NR-1F-A: fragment shader preprocess failed [{}][{}]: {}",
							VulkanPostFX.MOD_ID,
							e.getCode(),
							e.getPath(),
							e.getMessage()
					);
				}
			}
		} catch (IOException e) {
			VulkanPostFX.LOGGER.warn(
					"[{}] VPFX NR-1F-A: user shader resolve dry-run failed: zip I/O error: {}",
					VulkanPostFX.MOD_ID,
					e.getMessage()
			);
		}

		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-A: vertex shader source loaded         = {}",
				VulkanPostFX.MOD_ID,
				vertexLoaded
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-A: fragment shader source loaded       = {}",
				VulkanPostFX.MOD_ID,
				fragmentLoaded
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-A: include preprocessing applied        = {}",
				VulkanPostFX.MOD_ID,
				includePreprocessApplied
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-A: vertex shader source length          = {}",
				VulkanPostFX.MOD_ID,
				vertexSource != null ? vertexSource.length() : 0
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-A: fragment shader source length        = {}",
				VulkanPostFX.MOD_ID,
				fragmentSource != null ? fragmentSource.length() : 0
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-A: user shader native execution          = false",
				VulkanPostFX.MOD_ID
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-A: dry-run path does not execute draw",
				VulkanPostFX.MOD_ID
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-A: actual frame draw status reported by NR-1F-D summary",
				VulkanPostFX.MOD_ID
		);

		runPipelinePlanning(vertexSource, fragmentSource, vertexRef, fragmentRef, manifest, passDef);
		runCreatePipelineDryRun(vertexSource, fragmentSource, vertexRef, fragmentRef, manifest, passDef);
	}

	static void runPipelinePlanning(
			String vertexSource,
			String fragmentSource,
			String vertexRef,
			String fragmentRef,
			VpfxPackManifest manifest,
			VpfxPassDefinition passDef
	) {
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-B: user shader pipeline planning dry-run starting",
				VulkanPostFX.MOD_ID
		);

		boolean sourcesAvailable = vertexSource != null && fragmentSource != null;
		String vertexHash = sourcesAvailable ? sha256Hex(vertexSource) : "unknown";
		String fragmentHash = sourcesAvailable ? sha256Hex(fragmentSource) : "unknown";

		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-B: vertex shader source hash              = {}",
				VulkanPostFX.MOD_ID,
				vertexHash
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-B: fragment shader source hash            = {}",
				VulkanPostFX.MOD_ID,
				fragmentHash
		);

		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-B: output format                          = RGBA8_UNORM",
				VulkanPostFX.MOD_ID
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-B: sampler convention                     = InSampler",
				VulkanPostFX.MOD_ID
		);

		boolean wouldCreate = sourcesAvailable;

		if (wouldCreate) {
			VpfxNativePipelineKey userKey = new VpfxNativePipelineKey(
					manifest.getPackId(),
					passDef.identityOrIndex(0),
					VpfxPassType.FULLSCREEN,
					vertexRef,
					fragmentRef,
					"RGBA8_UNORM",
					vertexHash,
					fragmentHash,
					"InSampler"
			);

			VulkanPostFX.LOGGER.info(
					"[{}] VPFX NR-1F-B: user shader pipeline key planned      = {}",
					VulkanPostFX.MOD_ID,
					userKey
			);
		} else {
			VulkanPostFX.LOGGER.warn(
					"[{}] VPFX NR-1F-B: user shader pipeline key not planned — "
							+ "vertex or fragment shader source unavailable",
					VulkanPostFX.MOD_ID
			);
		}

		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-B: would create user shader pipeline       = {}",
				VulkanPostFX.MOD_ID,
				wouldCreate
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-B: user shader RenderPipeline created      = false",
				VulkanPostFX.MOD_ID
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-B: user shader native draw executed        = false",
				VulkanPostFX.MOD_ID
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-B: dry-run planning does not determine draw pipeline",
				VulkanPostFX.MOD_ID
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-B: actual frame draw status reported by NR-1F-D summary",
				VulkanPostFX.MOD_ID
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-B: user shader pipeline planning dry-run complete",
				VulkanPostFX.MOD_ID
		);
	}

	public static void runPendingPipelineCreateOnRenderThread() {
		if (!VpfxNativeRuntimeSupport.isExecuteEnabled()) {
			return;
		}

		if (!RenderSystem.isOnRenderThread()) {
			return;
		}

		String shaderLocator = pendingShaderLocator;
		Identifier vertexShaderId = pendingVertexShaderId;
		Identifier fragmentShaderId = pendingFragmentShaderId;
		VpfxNativePipelineKey userKey = pendingUserKey;

		if (shaderLocator == null || userKey == null || vertexShaderId == null || fragmentShaderId == null) {
			return;
		}

		pendingShaderLocator = null;
		pendingVertexShaderId = null;
		pendingFragmentShaderId = null;
		pendingVertexHash = null;
		pendingFragmentHash = null;
		pendingUserKey = null;

		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C.2: user shader pipeline cache check",
				VulkanPostFX.MOD_ID
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C.2: pack id                                = {}",
				VulkanPostFX.MOD_ID,
				userKey.packId()
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C.2: pass id                                = {}",
				VulkanPostFX.MOD_ID,
				userKey.passId()
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C.2: pipeline key                            = {}",
				VulkanPostFX.MOD_ID,
				userKey
		);

		boolean cacheHit = VpfxNativeUserPipelineCache.hasAnyEntry(userKey);
		boolean cachedFailure = VpfxNativeUserPipelineCache.isFailedCached(userKey);

		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C.2: cache size before lookup              = {}",
				VulkanPostFX.MOD_ID,
				VpfxNativeUserPipelineCache.cacheDiagnostics(userKey)
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C.2: key hashCode                           = {}",
				VulkanPostFX.MOD_ID,
				userKey.hashCode()
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C.2: cache contains key                      = {}",
				VulkanPostFX.MOD_ID,
				cacheHit
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C.2: cache hit                               = {}",
				VulkanPostFX.MOD_ID,
				cacheHit
		);

		if (VpfxNativeUserPipelineCache.isSuccessCached(userKey)) {
			VulkanPostFX.LOGGER.info(
					"[{}] VPFX NR-1F-C.2: cached success — skipping RenderPipeline creation",
					VulkanPostFX.MOD_ID
			);
			logCacheAwareResult(false, true, true, false, false);
			return;
		}

		if (cachedFailure) {
			String reason = VpfxNativeUserPipelineCache.getFailureReason(userKey);
			VulkanPostFX.LOGGER.info(
					"[{}] VPFX NR-1F-C.2: cached failure — skipping RenderPipeline creation. reason={}",
					VulkanPostFX.MOD_ID,
					reason
			);
			logCacheAwareResult(false, false, true, true, true);
			return;
		}

		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C.2: cache miss — attempting RenderPipeline creation",
				VulkanPostFX.MOD_ID
		);

		boolean created = false;

		try {
			RenderPipeline pipeline = RenderPipeline.builder()
					.withBindGroupLayout(BindGroupLayouts.GLOBALS)
					.withBindGroupLayout(USER_SAMPLER_BIND_GROUP)
					.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
					.withCull(false)
					.withVertexShader(vertexShaderId)
					.withFragmentShader(fragmentShaderId)
					.withLocation("pipeline/vpfx_native_user_shader_dryrun")
					.build();

			created = true;
			VpfxNativeUserPipelineCache.putSuccess(userKey, pipeline);

			VulkanPostFX.LOGGER.info(
					"[{}] VPFX NR-1F-C.2: RenderPipeline created                 = true",
					VulkanPostFX.MOD_ID
			);
			VulkanPostFX.LOGGER.info(
					"[{}] VPFX NR-1F-C.2: cache size after put                   = {}",
					VulkanPostFX.MOD_ID,
					VpfxNativeUserPipelineCache.postPutDiagnostics(userKey)
			);
		} catch (Throwable t) {
			VpfxNativeUserPipelineCache.putFailure(userKey, t.getMessage());

			VulkanPostFX.LOGGER.warn(
					"[{}] VPFX NR-1F-C.2: RenderPipeline creation failed on Render thread: {}. "
							+ "Cached as failure. Falling back to builtin passthrough.",
					VulkanPostFX.MOD_ID,
					t.getMessage()
			);
			VulkanPostFX.LOGGER.info(
					"[{}] VPFX NR-1F-C.2: cache size after put (failure)         = {}",
					VulkanPostFX.MOD_ID,
					VpfxNativeUserPipelineCache.postPutDiagnostics(userKey)
			);
		}

		logCacheAwareResult(true, created, true, !created, !created);
	}

	private static void logCacheAwareResult(boolean attempted, boolean created,
			boolean pipelineCached, boolean cachedFailure, boolean pipelineCreationFallbackUsed) {
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C.2: RenderPipeline create attempted         = {}",
				VulkanPostFX.MOD_ID,
				attempted
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C.2: RenderPipeline created                  = {}",
				VulkanPostFX.MOD_ID,
				created
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C.2: pipeline cached                         = {}",
				VulkanPostFX.MOD_ID,
				pipelineCached
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C.2: cached failure                          = {}",
				VulkanPostFX.MOD_ID,
				cachedFailure
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C.2: pipeline creation fallback used          = {}",
				VulkanPostFX.MOD_ID,
				pipelineCreationFallbackUsed
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C.2: user shader native draw executed        = false",
				VulkanPostFX.MOD_ID
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C.2: dry-run path does not execute draw",
				VulkanPostFX.MOD_ID
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C.2: user shader RenderPipeline create dry-run complete",
				VulkanPostFX.MOD_ID
		);
	}

	static void runCreatePipelineDryRun(
			String vertexSource,
			String fragmentSource,
			String vertexRef,
			String fragmentRef,
			VpfxPackManifest manifest,
			VpfxPassDefinition passDef
	) {
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C: user shader RenderPipeline create dry-run starting",
				VulkanPostFX.MOD_ID
		);

		if (vertexSource == null || fragmentSource == null) {
			VulkanPostFX.LOGGER.warn(
					"[{}] VPFX NR-1F-C: user shader RenderPipeline create skipped — "
							+ "vertex or fragment shader source unavailable",
					VulkanPostFX.MOD_ID
			);
			logPipelineCreateResult(false, true);
			return;
		}

		String runtimeNamespace = RuntimeZipPackState.getRuntimeNamespace();
		if (runtimeNamespace == null || runtimeNamespace.isEmpty()) {
			VulkanPostFX.LOGGER.warn(
					"[{}] VPFX NR-1F-C: user shader RenderPipeline create skipped — "
							+ "runtime namespace unavailable",
					VulkanPostFX.MOD_ID
			);
			logPipelineCreateResult(false, true);
			return;
		}

		String vertexHash = sha256Hex(vertexSource);
		String fragmentHash = sha256Hex(fragmentSource);

		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C: vertex shader ref                       = {}",
				VulkanPostFX.MOD_ID,
				vertexRef
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C: vertex source hash                       = {}",
				VulkanPostFX.MOD_ID,
				vertexHash
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C: fragment source hash                     = {}",
				VulkanPostFX.MOD_ID,
				fragmentHash
		);

		String vertexShaderPath = extractShaderPath(vertexRef);
		String fragmentShaderPath = extractShaderPath(fragmentRef);

		if (vertexShaderPath == null || fragmentShaderPath == null) {
			VulkanPostFX.LOGGER.warn(
					"[{}] VPFX NR-1F-C: could not extract shader path. "
							+ "vertex='{}', fragment='{}'",
					VulkanPostFX.MOD_ID,
					vertexRef,
					fragmentRef
			);
			logPipelineCreateResult(false, true);
			return;
		}

		Identifier vertexShaderId = Identifier.fromNamespaceAndPath(runtimeNamespace, vertexShaderPath);
		Identifier fragmentShaderId = Identifier.fromNamespaceAndPath(runtimeNamespace, fragmentShaderPath);
		boolean vertexLocatorValid = isShaderLocatorValid(vertexShaderId);
		boolean fragmentLocatorValid = isShaderLocatorValid(fragmentShaderId);

		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C.2: original vertex shader id               = {}",
				VulkanPostFX.MOD_ID,
				vertexRef
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C.2: original fragment shader id             = {}",
				VulkanPostFX.MOD_ID,
				fragmentRef
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C.2: runtime vertex shader location          = {}",
				VulkanPostFX.MOD_ID,
				vertexShaderId
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C.2: runtime fragment shader location        = {}",
				VulkanPostFX.MOD_ID,
				fragmentShaderId
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C.2: normalized vertex shader location valid  = {}",
				VulkanPostFX.MOD_ID,
				vertexLocatorValid
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C.2: normalized fragment shader location valid= {}",
				VulkanPostFX.MOD_ID,
				fragmentLocatorValid
		);

		if (!vertexLocatorValid || !fragmentLocatorValid) {
			VulkanPostFX.LOGGER.warn(
					"[{}] VPFX NR-1F-C.2: shader location normalization failed. "
							+ "namespace={}, vertexPath={}, fragmentPath={}",
					VulkanPostFX.MOD_ID,
					runtimeNamespace,
					vertexShaderPath,
					fragmentShaderPath
			);
			logCacheAwareResult(false, false, false, true, true);
			return;
		}

		if (!runtimeShaderSourceAvailable(vertexShaderId, true)
				|| !runtimeShaderSourceAvailable(fragmentShaderId, false)) {
			VulkanPostFX.LOGGER.warn(
					"[{}] VPFX NR-1F-C.2: user shader RenderPipeline create skipped — "
							+ "runtime shader source is not loaded in Minecraft ResourceManager yet. "
							+ "This is a transient resource-reload state and will not be cached as a pipeline failure. vs={}, fs={}",
					VulkanPostFX.MOD_ID,
					vertexShaderId,
					fragmentShaderId
			);
			logCacheAwareResult(false, false, false, false, true);
			return;
		}

		VpfxNativePipelineKey userKey = new VpfxNativePipelineKey(
				manifest.getPackId(),
				passDef.identityOrIndex(0),
				VpfxPassType.FULLSCREEN,
				vertexRef,
				fragmentRef,
				"RGBA8_UNORM",
				vertexHash,
				fragmentHash,
				"InSampler"
		);

		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C: pipeline key                              = {}",
				VulkanPostFX.MOD_ID,
				userKey
		);

		if (!RenderSystem.isOnRenderThread()) {
			pendingShaderLocator = vertexShaderId.toString();
			pendingVertexShaderId = vertexShaderId;
			pendingFragmentShaderId = fragmentShaderId;
			pendingVertexHash = vertexHash;
			pendingFragmentHash = fragmentHash;
			pendingUserKey = userKey;
			PostFxRuntimeState.markPendingUserShaderPipelineCreate();

			VulkanPostFX.LOGGER.info(
					"[{}] VPFX NR-1F-C.1: user shader RenderPipeline create marked as pending for Render thread execution",
					VulkanPostFX.MOD_ID
			);
			return;
		}

		boolean cacheHit = VpfxNativeUserPipelineCache.hasAnyEntry(userKey);
		boolean cachedFailure = VpfxNativeUserPipelineCache.isFailedCached(userKey);

		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C.2: cache size before lookup (sync)         = {}",
				VulkanPostFX.MOD_ID,
				VpfxNativeUserPipelineCache.cacheDiagnostics(userKey)
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C.2: key hashCode (sync)                     = {}",
				VulkanPostFX.MOD_ID,
				userKey.hashCode()
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C.2: cache contains key (sync)                = {}",
				VulkanPostFX.MOD_ID,
				cacheHit
		);

		if (VpfxNativeUserPipelineCache.isSuccessCached(userKey)) {
			VulkanPostFX.LOGGER.info(
					"[{}] VPFX NR-1F-C.2: cache hit (success) — skipping RenderPipeline creation on sync path",
					VulkanPostFX.MOD_ID
			);
			logCacheAwareResult(false, true, true, false, false);
			return;
		}

		if (cachedFailure) {
			VulkanPostFX.LOGGER.info(
					"[{}] VPFX NR-1F-C.2: cache hit (failure) — skipping RenderPipeline creation on sync path",
					VulkanPostFX.MOD_ID
			);
			logCacheAwareResult(false, false, true, true, true);
			return;
		}

		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C.2: cache miss — attempting RenderPipeline creation on sync path",
				VulkanPostFX.MOD_ID
		);

		boolean created = false;

		try {
			RenderPipeline pipeline = RenderPipeline.builder()
					.withBindGroupLayout(BindGroupLayouts.GLOBALS)
					.withBindGroupLayout(USER_SAMPLER_BIND_GROUP)
					.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
					.withCull(false)
					.withVertexShader(vertexShaderId)
					.withFragmentShader(fragmentShaderId)
					.withLocation("pipeline/vpfx_native_user_shader_dryrun")
					.build();

			created = true;
			VpfxNativeUserPipelineCache.putSuccess(userKey, pipeline);

			VulkanPostFX.LOGGER.info(
					"[{}] VPFX NR-1F-C.2: RenderPipeline created                 = true (sync path)",
					VulkanPostFX.MOD_ID
			);
			VulkanPostFX.LOGGER.info(
					"[{}] VPFX NR-1F-C.2: cache size after put (sync)            = {}",
					VulkanPostFX.MOD_ID,
					VpfxNativeUserPipelineCache.postPutDiagnostics(userKey)
			);
		} catch (Throwable t) {
			VpfxNativeUserPipelineCache.putFailure(userKey, t.getMessage());

			VulkanPostFX.LOGGER.warn(
					"[{}] VPFX NR-1F-C.2: RenderPipeline creation failed on sync path: {}. "
							+ "Cached as failure. Falling back to builtin passthrough.",
					VulkanPostFX.MOD_ID,
					t.getMessage()
			);
			VulkanPostFX.LOGGER.info(
					"[{}] VPFX NR-1F-C.2: cache size after put (sync failure)    = {}",
					VulkanPostFX.MOD_ID,
					VpfxNativeUserPipelineCache.postPutDiagnostics(userKey)
			);
		}

		logCacheAwareResult(true, created, true, !created, !created);
	}

	private static void logPipelineCreateResult(boolean created, boolean fallback) {
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C: user shader RenderPipeline created      = {}",
				VulkanPostFX.MOD_ID,
				created
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C: user shader native draw executed        = false",
				VulkanPostFX.MOD_ID
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C: dry-run path does not execute draw",
				VulkanPostFX.MOD_ID
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C: actual frame draw status reported by NR-1F-D summary",
				VulkanPostFX.MOD_ID
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C: fallback used                            = {}",
				VulkanPostFX.MOD_ID,
				fallback
		);
		VulkanPostFX.LOGGER.info(
				"[{}] VPFX NR-1F-C: user shader RenderPipeline create dry-run complete",
				VulkanPostFX.MOD_ID
		);
	}

	public static VpfxNativeUserPipelineResolveResult resolveOrCreateForActivePackOnRenderThread() {
		if (!VpfxNativeRuntimeSupport.isExecuteEnabled()) {
			return VpfxNativeUserPipelineResolveResult.builder()
					.attempted(false)
					.fallbackReason("execute flag not enabled")
					.failureStage(VpfxNativeFailureStage.EXECUTE_FLAG_DISABLED)
					.failureMessage("nativeRuntime.execute flag not set")
					.build();
		}

		if (!RenderSystem.isOnRenderThread()) {
			return VpfxNativeUserPipelineResolveResult.builder()
					.attempted(true)
					.fallbackReason("not on Render thread")
					.failureStage(VpfxNativeFailureStage.NOT_RENDER_THREAD)
					.failureMessage("resolveOrCreateForActivePackOnRenderThread must be called on Render thread")
					.build();
		}

		ShaderPackContainer activePack = ActiveShaderPackManager.getActivePack();
		if (activePack == null || !activePack.isVpfxNativePack()) {
			return VpfxNativeUserPipelineResolveResult.builder()
					.attempted(true)
					.fallbackReason("active pack is not a VPFX native pack")
					.failureStage(VpfxNativeFailureStage.USER_PIPELINE_RESOLVE)
					.failureMessage("active pack is null or not a VPFX native pack")
					.build();
		}

		VpfxNativePackDefinition vpfxDef = activePack.vpfxDefinition();
		if (vpfxDef == null || vpfxDef.getGraph() == null || vpfxDef.getGraph().getPasses().isEmpty()) {
			return VpfxNativeUserPipelineResolveResult.builder()
					.attempted(true)
					.fallbackReason("VPFX native pack graph is empty")
					.failureStage(VpfxNativeFailureStage.USER_PIPELINE_RESOLVE)
					.failureMessage("vpfxDef or graph is null, or passes list is empty")
					.build();
		}

		String runtimeNamespace = RuntimeZipPackState.getRuntimeNamespace();
		if (runtimeNamespace == null || runtimeNamespace.isEmpty()) {
			return VpfxNativeUserPipelineResolveResult.builder()
					.attempted(true)
					.fallbackReason("runtime namespace unavailable")
					.failureStage(VpfxNativeFailureStage.USER_PIPELINE_RESOLVE)
					.failureMessage("RuntimeZipPackState.getRuntimeNamespace() returned null or empty")
					.build();
		}

		VpfxPassDefinition passDef = vpfxDef.getGraph().getPasses().get(0);
		String vertexRef = passDef.getVertexShader();
		String fragmentRef = passDef.getFragmentShader();

		String vertexShaderPath = extractShaderPath(vertexRef);
		String fragmentShaderPath = extractShaderPath(fragmentRef);

		if (vertexShaderPath == null || fragmentShaderPath == null) {
			return VpfxNativeUserPipelineResolveResult.builder()
					.attempted(true)
					.fallbackReason("could not extract shader path from ref")
					.failureStage(VpfxNativeFailureStage.USER_PIPELINE_RESOLVE)
					.failureMessage("vertexRef or fragmentRef has no colon-delimited path")
					.build();
		}

		Identifier vertexShaderId = Identifier.fromNamespaceAndPath(runtimeNamespace, vertexShaderPath);
		Identifier fragmentShaderId = Identifier.fromNamespaceAndPath(runtimeNamespace, fragmentShaderPath);

		if (!runtimeShaderSourceAvailable(vertexShaderId, true)
				|| !runtimeShaderSourceAvailable(fragmentShaderId, false)) {
			String message = "runtime shader source unavailable; Minecraft resource reload required before native pipeline creation: "
					+ "vs=" + vertexShaderId + ", fs=" + fragmentShaderId;
			return VpfxNativeUserPipelineResolveResult.builder()
					.attempted(true)
					.fallbackReason(message)
					.failureStage(VpfxNativeFailureStage.USER_PIPELINE_RESOLVE)
					.failureMessage(message)
					.vertexShaderId(vertexShaderId)
					.fragmentShaderId(fragmentShaderId)
					.build();
		}

		String vertexHash = "";
		String fragmentHash = "";

		try (ZipFile zipFile = new ZipFile(vpfxDef.getZipPath().toFile())) {
			String vertexZipPath = toShaderZipPath(vertexRef, true);
			String fragmentZipPath = toShaderZipPath(fragmentRef, false);

			VpfxShaderSourcePreprocessor preprocessor = new VpfxShaderSourcePreprocessor(zipFile);
			String vertexSource = preprocessor.preprocess(vertexZipPath);
			String fragmentSource = preprocessor.preprocess(fragmentZipPath);
			vertexHash = sha256Hex(vertexSource);
			fragmentHash = sha256Hex(fragmentSource);
		} catch (Exception e) {
			return VpfxNativeUserPipelineResolveResult.builder()
					.attempted(true)
					.fallbackReason("shader resolve failed: " + e.getMessage())
					.failureStage(VpfxNativeFailureStage.USER_PIPELINE_RESOLVE)
					.failureMessage("shader resolve threw: " + e.getMessage())
					.build();
		}

		VpfxNativePipelineKey userKey = new VpfxNativePipelineKey(
				vpfxDef.getManifest().getPackId(),
				passDef.identityOrIndex(0),
				VpfxPassType.FULLSCREEN,
				vertexRef,
				fragmentRef,
				"RGBA8_UNORM",
				vertexHash,
				fragmentHash,
				"InSampler"
		);

		if (VpfxNativeUserPipelineCache.isSuccessCached(userKey)) {
			return VpfxNativeUserPipelineResolveResult.builder()
					.attempted(true)
					.available(true)
					.successCached(true)
					.pipeline(VpfxNativeUserPipelineCache.getSuccess(userKey))
					.key(userKey)
					.vertexShaderId(vertexShaderId)
					.fragmentShaderId(fragmentShaderId)
					.build();
		}

		if (VpfxNativeUserPipelineCache.isFailedCached(userKey)) {
			return VpfxNativeUserPipelineResolveResult.builder()
					.attempted(true)
					.failureCached(true)
					.key(userKey)
					.vertexShaderId(vertexShaderId)
					.fragmentShaderId(fragmentShaderId)
					.fallbackReason("cached failure: " + VpfxNativeUserPipelineCache.getFailureReason(userKey))
					.failureStage(VpfxNativeFailureStage.USER_PIPELINE_CREATE)
					.failureMessage("cached failure: " + VpfxNativeUserPipelineCache.getFailureReason(userKey))
					.build();
		}

		try {
			RenderPipeline pipeline = RenderPipeline.builder()
					.withBindGroupLayout(BindGroupLayouts.GLOBALS)
					.withBindGroupLayout(USER_SAMPLER_BIND_GROUP)
					.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
					.withCull(false)
					.withVertexShader(vertexShaderId)
					.withFragmentShader(fragmentShaderId)
					.withLocation("pipeline/vpfx_native_user_shader_draw")
					.build();

			VpfxNativeUserPipelineCache.putSuccess(userKey, pipeline);

			return VpfxNativeUserPipelineResolveResult.builder()
					.attempted(true)
					.available(true)
					.pipeline(pipeline)
					.key(userKey)
					.vertexShaderId(vertexShaderId)
					.fragmentShaderId(fragmentShaderId)
					.build();
		} catch (Throwable t) {
			VpfxNativeUserPipelineCache.putFailure(userKey, t.getMessage());

			return VpfxNativeUserPipelineResolveResult.builder()
					.attempted(true)
					.failureCached(true)
					.key(userKey)
					.vertexShaderId(vertexShaderId)
					.fragmentShaderId(fragmentShaderId)
					.fallbackReason("pipeline creation failed: " + t.getMessage())
					.failureStage(VpfxNativeFailureStage.USER_PIPELINE_CREATE)
					.failureMessage("RenderPipeline.builder().build() threw: " + t.getMessage())
					.build();
		}
	}

	private static boolean runtimeShaderSourceAvailable(Identifier shaderId, boolean vertex) {
		if (shaderId == null) {
			return false;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.getResourceManager() == null) {
			return false;
		}

		Identifier sourceId = Identifier.fromNamespaceAndPath(
				shaderId.getNamespace(),
				"shaders/" + shaderId.getPath() + (vertex ? ".vsh" : ".fsh")
		);
		return minecraft.getResourceManager().getResource(sourceId).isPresent();
	}

	/**
	 * Validates that a shader Identifier is well-formed for ShaderManager lookup.
	 * Colons are only allowed between namespace and path (the first colon), never inside the path.
	 */
	private static boolean isShaderLocatorValid(Identifier id) {
		if (id == null) {
			return false;
		}
		String path = id.getPath();
		return path.indexOf(':') < 0;
	}

	/**
	 * "namespace:path/to/shader" → "path/to/shader"
	 */
	private static String extractShaderPath(String ref) {
		int colon = ref.indexOf(':');
		if (colon < 0 || colon == ref.length() - 1) {
			return null;
		}
		return ref.substring(colon + 1);
	}

	private static String sha256Hex(String input) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder();
			for (byte b : hash) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException e) {
			return "hash-error:" + e.getMessage();
		}
	}

	/**
	 * resourceId = "namespace:path/to/shader"
	 * vertex=true  → zip path "shaders/path/to/shader.vsh"
	 * vertex=false → zip path "shaders/path/to/shader.fsh"
	 */
	private static String toShaderZipPath(String resourceId, boolean vertex) {
		int colon = resourceId.indexOf(':');
		if (colon < 0 || colon == resourceId.length() - 1) {
			return vertex ? "shaders/" + resourceId + ".vsh" : "shaders/" + resourceId + ".fsh";
		}

		String path = resourceId.substring(colon + 1);
		String extension = vertex ? ".vsh" : ".fsh";
		return "shaders/" + path + extension;
	}

	public static void clearPendingPipelineCreate() {
		pendingShaderLocator = null;
		pendingVertexShaderId = null;
		pendingFragmentShaderId = null;
		pendingVertexHash = null;
		pendingFragmentHash = null;
		pendingUserKey = null;
	}
}
