package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class VpfxNativeRuntimeGraph {

	private final String packId;
	private final List<VpfxNativeTargetNode> targets;
	private final List<VpfxNativePassNode> passes;

	private VpfxNativeRuntimeGraph(Builder builder) {
		this.packId = builder.packId != null ? builder.packId : "unknown";
		this.targets = Collections.unmodifiableList(new ArrayList<>(builder.targets));
		this.passes = Collections.unmodifiableList(new ArrayList<>(builder.passes));
	}

	public static Builder builder() {
		return new Builder();
	}

	public String packId() { return packId; }
	public List<VpfxNativeTargetNode> targets() { return targets; }
	public List<VpfxNativePassNode> passes() { return passes; }

	public int passCount() { return passes.size(); }
	public int targetCount() { return targets.size(); }

	public boolean isSinglePass() { return passes.size() == 1; }

	@Override
	public String toString() {
		return "NativeRuntimeGraph{pack='" + packId + "', passes=" + passCount() + ", targets=" + targetCount() + '}';
	}

	public static final class Builder {
		private String packId;
		private final List<VpfxNativeTargetNode> targets = new ArrayList<>();
		private final List<VpfxNativePassNode> passes = new ArrayList<>();

		private Builder() {}

		public Builder packId(String v) { this.packId = v; return this; }
		public Builder addTarget(VpfxNativeTargetNode v) { this.targets.add(v); return this; }
		public Builder addPass(VpfxNativePassNode v) { this.passes.add(v); return this; }

		public VpfxNativeRuntimeGraph build() { return new VpfxNativeRuntimeGraph(this); }
	}
}
