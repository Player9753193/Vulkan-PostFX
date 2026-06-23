package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.graph;

import java.util.Objects;

public final class VpfxNativeTargetNode {

	private final String targetId;
	private final String type;

	public VpfxNativeTargetNode(String targetId, String type) {
		this.targetId = targetId != null ? targetId : "minecraft:main";
		this.type = type != null ? type : "COLOR";
	}

	public String targetId() {
		return targetId;
	}

	public String type() {
		return type;
	}

	public boolean isMinecraftMain() {
		return "minecraft:main".equals(targetId);
	}

	public boolean isMinecraftSceneColor() {
		return "minecraft:scene_color".equals(targetId) || isMinecraftMain();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof VpfxNativeTargetNode that)) return false;
		return targetId.equals(that.targetId) && type.equals(that.type);
	}

	@Override
	public int hashCode() {
		return Objects.hash(targetId, type);
	}

	@Override
	public String toString() {
		return "TargetNode{id='" + targetId + "', type=" + type + '}';
	}
}
