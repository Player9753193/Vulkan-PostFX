package com.ionhex975.vulkanpostfx.client.debug;

import com.ionhex975.vulkanpostfx.client.pack.ActiveShaderPackManager;
import com.ionhex975.vulkanpostfx.client.pack.ShaderPackContainer;
import com.ionhex975.vulkanpostfx.client.runtime.ActivePostEffectBridge;
import com.ionhex975.vulkanpostfx.client.runtime.ActivePostEffectSource;
import com.ionhex975.vulkanpostfx.client.shadow.ShadowFrameState;
import com.ionhex975.vulkanpostfx.client.shadow.ShadowRenderTargetsLite;
import com.ionhex975.vulkanpostfx.client.state.PostFxRuntimeState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Lightweight VPFX runtime HUD.
 *
 * 1.15.x role:
 * - make pack/backend/fallback state visible without opening logs;
 * - keep the overlay compact enough for normal development use;
 * - do not change rendering behavior.
 */
public final class VpfxDebugHud {
	private static final int BASE_X = 4;
	private static final int LINE_HEIGHT = 10;
	private static final int COLOR_HEADER = 0xFF66CCFF;
	private static final int COLOR_ON = 0xFF55FF55;
	private static final int COLOR_OFF = 0xFFFF5555;
	private static final int COLOR_WARN = 0xFFFFFF55;
	private static final int COLOR_INFO = 0xFFCCCCCC;
	private static final int COLOR_LABEL = 0xFFAAAAAA;
	private static final int MAX_VALUE_LENGTH = 96;

	private VpfxDebugHud() {
	}

	public static void render(GuiGraphicsExtractor graphics) {
		if (!shouldRender()) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}

		int y = 4;

		String stateLabel = runtimeStateLabel();
		int stateColor = runtimeStateColor();
		graphics.text(client.font, "[VPFX] " + stateLabel, BASE_X, y, stateColor);
		y += LINE_HEIGHT;

		graphics.text(client.font, "Pack: " + activePackLabel(), BASE_X, y, COLOR_INFO);
		y += LINE_HEIGHT;

		graphics.text(client.font, "Backend: " + PostFxRuntimeState.getBackendName()
				+ " / " + runtimeBackendLabel(), BASE_X, y, COLOR_INFO);
		y += LINE_HEIGHT;

		graphics.text(client.font, "Effect: " + activeEffectLabel(), BASE_X, y, COLOR_INFO);
		y += LINE_HEIGHT;

		if (PostFxRuntimeState.isNativeRuntimeFallbackActive()) {
			graphics.text(client.font, "Native fallback: " + fallbackLabel(), BASE_X, y, COLOR_WARN);
			y += LINE_HEIGHT;
		}

		String sourceLabel = activeSourceLabel();
		if (!sourceLabel.isBlank()) {
			graphics.text(client.font, "Source: " + sourceLabel, BASE_X, y, COLOR_LABEL);
			y += LINE_HEIGHT;
		}

		ShadowFrameState shadowState = ShadowFrameState.get();
		ShadowRenderTargetsLite shadowTargets = ShadowRenderTargetsLite.get();

		int shadowColor = shadowState.isShadowPassEnabled() ? COLOR_ON : COLOR_OFF;
		String shadowStatus = shadowState.isShadowPassEnabled()
				? "Shadow: ON (pass=" + shadowState.wasShadowPassExecuted()
						+ " casters=" + shadowState.wereShadowCastersRendered()
						+ ")"
				: "Shadow: OFF";
		graphics.text(client.font, shadowStatus, BASE_X, y, shadowColor);
		y += LINE_HEIGHT;

		if (shadowState.isShadowPassEnabled() && shadowState.isShadowTargetReady()) {
			graphics.text(client.font, "  size=" + shadowState.getShadowMapSize()
							+ " terrainDist=" + formatDist(shadowState.getTerrainShadowDistance())
							+ " entityDist=" + formatDist(shadowState.getEntityShadowDistance()),
					BASE_X, y, COLOR_LABEL);
			y += LINE_HEIGHT;

			String targetInfo = "  targetReady=" + shadowTargets.isReady()
					+ " executed=" + shadowState.wasShadowPassExecuted()
					+ " casters=" + shadowState.wereShadowCastersRendered();
			graphics.text(client.font, targetInfo, BASE_X, y, COLOR_LABEL);
			y += LINE_HEIGHT;
		}
	}

	private static boolean shouldRender() {
		// The VPFX Status HUD overlay must obey the explicit HUD switch.
		// Shader enabled / shadow debug / failed-pack state should not force
		// the overlay visible after the user turns the HUD off in the GUI.
		return PostFxRuntimeState.isDebugHudVisible();
	}

	private static String runtimeStateLabel() {
		if (PostFxRuntimeState.isShadowDepthDebugViewEnabled()) {
			return "DEBUG";
		}
		if (PostFxRuntimeState.isExternalPackMarkedFailed()) {
			return "FAILED -> VANILLA";
		}
		if (PostFxRuntimeState.isNativeRuntimeFallbackActive()) {
			return "NATIVE -> POSTCHAIN";
		}
		if (!PostFxRuntimeState.isDebugEffectEnabled()) {
			return "OFF";
		}
		if (PostFxRuntimeState.getActiveExternalPostEffectId() != null) {
			return "ON";
		}
		return "ON (BUILTIN)";
	}

	private static int runtimeStateColor() {
		if (PostFxRuntimeState.isExternalPackMarkedFailed()) {
			return COLOR_OFF;
		}
		if (PostFxRuntimeState.isNativeRuntimeFallbackActive()) {
			return COLOR_WARN;
		}
		if (PostFxRuntimeState.isShadowDepthDebugViewEnabled()) {
			return COLOR_WARN;
		}
		return PostFxRuntimeState.isDebugEffectEnabled() ? COLOR_ON : COLOR_OFF;
	}

	private static String activePackLabel() {
		ShaderPackContainer pack = ActiveShaderPackManager.getActivePack();
		if (pack == null) {
			return "none";
		}

		String label = pack.manifest().name()
				+ " [" + pack.manifest().id() + "]"
				+ " <" + pack.sourceId() + ">";

		if (PostFxRuntimeState.isExternalPackMarkedFailed()) {
			Identifier failedId = PostFxRuntimeState.getFailedExternalPostEffectId();
			label += " FAILED" + (failedId == null ? "" : " (" + failedId + ")");
		} else if (PostFxRuntimeState.isNativeRuntimeFallbackActive()) {
			label += " NATIVE_FALLBACK";
		}

		return clamp(label);
	}

	private static String runtimeBackendLabel() {
		String backendId = PostFxRuntimeState.getActiveRuntimeBackendId();
		String displayName = PostFxRuntimeState.getActiveRuntimeBackendDisplayName();

		if (backendId == null || backendId.isBlank()) {
			return "unknown";
		}

		String mode;
		if (PostFxRuntimeState.isExternalPackMarkedFailed()) {
			mode = "vanilla fallback";
		} else if (PostFxRuntimeState.isNativeRuntimeFallbackActive()) {
			mode = "postchain fallback";
		} else if (PostFxRuntimeState.isActiveNativeRuntimeBackend()) {
			mode = "native";
		} else if (PostFxRuntimeState.activeRuntimeBackendUsesPostChain()) {
			mode = "postchain";
		} else {
			mode = "custom";
		}

		String label = backendId + " (" + mode + ")";
		if (displayName != null && !displayName.isBlank() && !displayName.equals(backendId)) {
			label += " / " + displayName;
		}
		return clamp(label);
	}

	private static String activeEffectLabel() {
		if (PostFxRuntimeState.isShadowDepthDebugViewEnabled()) {
			return "SHADOW_DEPTH_DEBUG";
		}

		Identifier externalId = PostFxRuntimeState.getActiveExternalPostEffectId();
		if (externalId != null) {
			return clamp(externalId.toString());
		}

		String effectKey = PostFxRuntimeState.getActiveEffectKey();
		return effectKey == null || effectKey.isBlank() ? "none" : clamp(effectKey);
	}

	private static String fallbackLabel() {
		return clamp(PostFxRuntimeState.getNativeRuntimeFallbackSummary());
	}

	private static String activeSourceLabel() {
		ActivePostEffectSource source = ActivePostEffectBridge.getActiveSource();
		if (source == null || !source.isPresent()) {
			return "";
		}

		String label = source.sourceKind();
		if (source.parsedConfig() != null) {
			label += " targets=" + source.parsedConfig().targets().size()
					+ " passes=" + source.parsedConfig().passes().size();
		}
		return clamp(label);
	}

	private static String formatDist(float value) {
		return String.format("%.0f", value);
	}

	private static String clamp(String value) {
		if (value == null) {
			return "";
		}
		if (value.length() <= MAX_VALUE_LENGTH) {
			return value;
		}
		return value.substring(0, Math.max(0, MAX_VALUE_LENGTH - 3)) + "...";
	}
}
