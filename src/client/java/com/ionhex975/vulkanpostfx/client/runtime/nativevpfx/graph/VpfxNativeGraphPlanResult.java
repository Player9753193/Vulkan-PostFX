package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.graph;

import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.VpfxNativeFailureStage;
import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.VpfxNativePipelineKey;

import java.util.List;
import java.util.stream.Collectors;

public final class VpfxNativeGraphPlanResult {

	private final boolean planAttempted;
	private final boolean planSupported;
	private final int passCount;
	private final int targetCount;
	private final String firstPassName;
	private final String inputBindings;
	private final String outputBindings;
	private final String samplerConvention;
	private final String unsupportedReason;
	private final VpfxNativeFailureStage failureStage;
	private final boolean fallbackExpected;
	private final VpfxNativePipelineKey requiredPipelineKey;
	private final VpfxNativeRuntimeGraph runtimeGraph;
	private final VpfxNativeGraphPlan graphPlan;

	private VpfxNativeGraphPlanResult(Builder builder) {
		this.planAttempted = builder.planAttempted;
		this.planSupported = builder.planSupported;
		this.passCount = builder.passCount;
		this.targetCount = builder.targetCount;
		this.firstPassName = builder.firstPassName != null ? builder.firstPassName : "";
		this.inputBindings = builder.inputBindings != null ? builder.inputBindings : "";
		this.outputBindings = builder.outputBindings != null ? builder.outputBindings : "";
		this.samplerConvention = builder.samplerConvention != null ? builder.samplerConvention : "InSampler";
		this.unsupportedReason = builder.unsupportedReason != null ? builder.unsupportedReason : "none";
		this.failureStage = builder.failureStage != null ? builder.failureStage : VpfxNativeFailureStage.NONE;
		this.fallbackExpected = builder.fallbackExpected;
		this.requiredPipelineKey = builder.requiredPipelineKey;
		this.runtimeGraph = builder.runtimeGraph;
		this.graphPlan = builder.graphPlan;
	}

	public static Builder builder() { return new Builder(); }

	public boolean planAttempted() { return planAttempted; }
	public boolean planSupported() { return planSupported; }
	public int passCount() { return passCount; }
	public int targetCount() { return targetCount; }
	public String firstPassName() { return firstPassName; }
	public String inputBindings() { return inputBindings; }
	public String outputBindings() { return outputBindings; }
	public String samplerConvention() { return samplerConvention; }
	public String unsupportedReason() { return unsupportedReason; }
	public VpfxNativeFailureStage failureStage() { return failureStage; }
	public boolean fallbackExpected() { return fallbackExpected; }
	public VpfxNativePipelineKey requiredPipelineKey() { return requiredPipelineKey; }
	public VpfxNativeRuntimeGraph runtimeGraph() { return runtimeGraph; }
	public VpfxNativeGraphPlan graphPlan() { return graphPlan; }

	public static final class Builder {
		private boolean planAttempted;
		private boolean planSupported;
		private int passCount;
		private int targetCount;
		private String firstPassName;
		private String inputBindings;
		private String outputBindings;
		private String samplerConvention = "InSampler";
		private String unsupportedReason = "none";
		private VpfxNativeFailureStage failureStage = VpfxNativeFailureStage.NONE;
		private boolean fallbackExpected;
		private VpfxNativePipelineKey requiredPipelineKey;
		private VpfxNativeRuntimeGraph runtimeGraph;
		private VpfxNativeGraphPlan graphPlan;

		private Builder() {}

		public Builder planAttempted(boolean v) { this.planAttempted = v; return this; }
		public Builder planSupported(boolean v) { this.planSupported = v; return this; }
		public Builder passCount(int v) { this.passCount = v; return this; }
		public Builder targetCount(int v) { this.targetCount = v; return this; }
		public Builder firstPassName(String v) { this.firstPassName = v; return this; }
		public Builder inputBindings(String v) { this.inputBindings = v; return this; }
		public Builder outputBindings(String v) { this.outputBindings = v; return this; }
		public Builder samplerConvention(String v) { this.samplerConvention = v; return this; }
		public Builder unsupportedReason(String v) { this.unsupportedReason = v; return this; }
		public Builder failureStage(VpfxNativeFailureStage v) { this.failureStage = v; return this; }
		public Builder fallbackExpected(boolean v) { this.fallbackExpected = v; return this; }
		public Builder requiredPipelineKey(VpfxNativePipelineKey v) { this.requiredPipelineKey = v; return this; }
		public Builder runtimeGraph(VpfxNativeRuntimeGraph v) { this.runtimeGraph = v; return this; }
		public Builder graphPlan(VpfxNativeGraphPlan v) { this.graphPlan = v; return this; }

		public VpfxNativeGraphPlanResult build() { return new VpfxNativeGraphPlanResult(this); }
	}
}
