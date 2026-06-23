package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.graph;

import java.util.Objects;

public final class VpfxNativeOutputBinding {

	private final String targetId;

	public VpfxNativeOutputBinding(String targetId) {
		this.targetId = targetId != null ? targetId : "minecraft:main";
	}

	public String targetId() {
		return targetId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof VpfxNativeOutputBinding that)) return false;
		return targetId.equals(that.targetId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(targetId);
	}

	@Override
	public String toString() {
		return "OutputBinding{→ " + targetId + '}';
	}
}
