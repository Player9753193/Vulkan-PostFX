package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx;

import java.util.Objects;

public final class VpfxNativeResolvedResource {

	public enum Role {
		SCENE_COLOR_INPUT,
		MAIN_OUTPUT,
		SCENE_DEPTH_INPUT,
		SHADOW_DEPTH_INPUT,
		SAME_TARGET_ALIAS,
		TRANSIENT_TEMP
	}

	private final String reference;
	private final Role role;
	private final String renderTargetClass;
	private final int width;
	private final int height;

	public VpfxNativeResolvedResource(String reference, Role role, String renderTargetClass, int width, int height) {
		this.reference = Objects.requireNonNull(reference, "reference");
		this.role = Objects.requireNonNull(role, "role");
		this.renderTargetClass = renderTargetClass != null ? renderTargetClass : "unknown";
		this.width = width;
		this.height = height;
	}

	public String reference() {
		return reference;
	}

	public Role role() {
		return role;
	}

	public String renderTargetClass() {
		return renderTargetClass;
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	public boolean isSameTargetAs(VpfxNativeResolvedResource other) {
		if (other == null) {
			return false;
		}
		return this.renderTargetClass.equals(other.renderTargetClass)
				&& this.width == other.width
				&& this.height == other.height;
	}

	@Override
	public String toString() {
		return "ResolvedResource{" + "ref='" + reference + '\'' + ", role=" + role
				+ ", rt=" + renderTargetClass + ", " + width + "x" + height + '}';
	}
}
