package com.ionhex975.vulkanpostfx.client.diagnostics;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.state.PostFxRuntimeState;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class VpfxFailureDiagnostics {

	private static final DateTimeFormatter TIMESTAMP_FORMAT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
	private static final String DIAGNOSTICS_DIR = "vulkanpostfx_runtime/diagnostics";
	private static final String DIAGNOSTICS_FILE = "latest-vpfx-error.txt";

	private VpfxFailureDiagnostics() {
	}

	public static void write(
			String failureStage,
			Exception exception
	) {
		write(failureStage, exception, null);
	}

	public static void write(
			String failureStage,
			Exception exception,
			String packName
	) {
		Identifier externalId = PostFxRuntimeState.getActiveExternalPostEffectId();
		Identifier failedId = PostFxRuntimeState.getFailedExternalPostEffectId();
		String activeEffectKey = PostFxRuntimeState.getActiveEffectKey();
		if (externalId != null && "debug_invert".equals(activeEffectKey)) {
			activeEffectKey = "external_zip";
		}

		StringBuilder sb = new StringBuilder();
		sb.append("=== VPFX Failure Diagnostic Report ===\n");
		sb.append("Timestamp: ").append(TIMESTAMP_FORMAT.format(Instant.now())).append("\n");
		sb.append("\n");

		sb.append("--- Pack ---\n");
		sb.append("packName: ").append(packName != null ? packName : "(unknown)").append("\n");
		sb.append("externalPostEffectId: ").append(externalId != null ? externalId : "(null)").append("\n");
		sb.append("runtimeNamespace: ").append(detectRuntimeNamespace(externalId)).append("\n");
		sb.append("\n");

		sb.append("--- Backend ---\n");
		sb.append("backendId: ").append(PostFxRuntimeState.getActiveRuntimeBackendId()).append("\n");
		sb.append("backendDisplay: ").append(PostFxRuntimeState.getActiveRuntimeBackendDisplayName()).append("\n");
		sb.append("backendCapabilities: ").append(PostFxRuntimeState.getActiveRuntimeBackendCapabilities()).append("\n");
		sb.append("nativeFallbackActive: ").append(PostFxRuntimeState.isNativeRuntimeFallbackActive()).append("\n");
		sb.append("nativeFallbackPostEffectId: ").append(PostFxRuntimeState.getNativeRuntimeFallbackExternalPostEffectId() != null ? PostFxRuntimeState.getNativeRuntimeFallbackExternalPostEffectId() : "(null)").append("\n");
		sb.append("nativeFallbackReason: ").append(PostFxRuntimeState.getNativeRuntimeFallbackReason()).append("\n");
		sb.append("\n");

		sb.append("--- Failure ---\n");
		sb.append("failureStage: ").append(failureStage).append("\n");
		sb.append("exceptionClass: ").append(exception.getClass().getName()).append("\n");
		sb.append("exceptionMessage: ").append(exception.getMessage() != null ? exception.getMessage() : "(no message)").append("\n");
		sb.append("markedUnavailable: ").append(failedId != null).append("\n");
		sb.append("failedPostEffectId: ").append(failedId != null ? failedId : "(null)").append("\n");
		sb.append("fallbackTarget: ").append(failedId != null ? "vanilla/disabled" : "minecraft_postchain/session-fallback").append("\n");
		sb.append("\n");

		sb.append("--- Runtime State ---\n");
		sb.append("activeEffectKey: ").append(activeEffectKey != null ? activeEffectKey : "(null)").append("\n");
		sb.append("debugEffectEnabled: ").append(PostFxRuntimeState.isDebugEffectEnabled()).append("\n");
		sb.append("\n");

		sb.append("--- Stack Trace ---\n");
		for (StackTraceElement frame : exception.getStackTrace()) {
			sb.append("  ").append(frame).append("\n");
		}
		sb.append("--- end ---\n");

		String content = sb.toString();

		try {
			Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
			Path diagDir = gameDir.resolve(DIAGNOSTICS_DIR);
			Files.createDirectories(diagDir);

			Path diagFile = diagDir.resolve(DIAGNOSTICS_FILE);
			Files.writeString(diagFile, content, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

			VulkanPostFX.LOGGER.info(
					"[{}] VPFX failure diagnostic written to: {}",
					VulkanPostFX.MOD_ID,
					diagFile.toAbsolutePath()
			);
		} catch (IOException | RuntimeException e) {
			VulkanPostFX.LOGGER.warn(
					"[{}] Failed to write VPFX failure diagnostic: {}",
					VulkanPostFX.MOD_ID,
					e.getMessage()
			);
		}
	}

	private static String detectRuntimeNamespace(Identifier externalId) {
		if (externalId == null) return "(null)";
		String ns = externalId.getNamespace();
		if (ns != null && !ns.isBlank()) return ns;
		return "(unnamed)";
	}
}
