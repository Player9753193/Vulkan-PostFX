package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.graph;

import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.VpfxNativeFailureStage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class VpfxNativeGraphPlan {

	private final boolean supported;
	private final List<VpfxNativePassNode> plannedPasses;
	private final String unsupportedReason;
	private final VpfxNativeFailureStage failureStage;

	private VpfxNativeGraphPlan(Builder builder) {
		this.supported = builder.supported;
		this.plannedPasses = Collections.unmodifiableList(new ArrayList<>(builder.plannedPasses));
		this.unsupportedReason = builder.unsupportedReason != null ? builder.unsupportedReason : "none";
		this.failureStage = builder.failureStage != null ? builder.failureStage : VpfxNativeFailureStage.NONE;
	}

	public static Builder builder() { return new Builder(); }

	public boolean supported() { return supported; }
	public List<VpfxNativePassNode> plannedPasses() { return plannedPasses; }
	public String unsupportedReason() { return unsupportedReason; }
	public VpfxNativeFailureStage failureStage() { return failureStage; }
	public int passCount() { return plannedPasses.size(); }

	public static final class Builder {
		private boolean supported;
		private final List<VpfxNativePassNode> plannedPasses = new ArrayList<>();
		private String unsupportedReason = "none";
		private VpfxNativeFailureStage failureStage = VpfxNativeFailureStage.NONE;

		private Builder() {}

		public Builder supported(boolean v) { this.supported = v; return this; }
		public Builder addPlannedPass(VpfxNativePassNode v) { this.plannedPasses.add(v); return this; }
		public Builder unsupportedReason(String v) { this.unsupportedReason = v; return this; }
		public Builder failureStage(VpfxNativeFailureStage v) { this.failureStage = v; return this; }

		public VpfxNativeGraphPlan build() { return new VpfxNativeGraphPlan(this); }
	}
}
