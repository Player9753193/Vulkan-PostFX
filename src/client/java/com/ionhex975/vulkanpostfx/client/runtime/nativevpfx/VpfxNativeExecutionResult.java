package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx;

public final class VpfxNativeExecutionResult {

	private final boolean attempted;
	private final boolean copySucceeded;
	private final boolean pipelineCreated;
	private final boolean renderPassCreated;
	private final boolean drawExecuted;
	private final boolean nativeSucceeded;
	private final boolean fallbackStillActive;
	private final String fallbackReason;
	private final boolean diagnosticMode;
	private final boolean userShaderNativeExecution;
	private final boolean builtinPassthroughOnly;
	private final boolean userPipelineAvailable;
	private final boolean userPipelineAttempted;
	private final boolean userPipelineCached;
	private final boolean userPipelineFallbackUsed;
	private final String actualPipeline;
	private final String pipelineFallbackReason;
	private final VpfxNativeFailureStage failureStage;
	private final String failureMessage;
	private final boolean postChainFallbackExpected;
	private final boolean builtinFallbackAttempted;
	private final boolean builtinFallbackSucceeded;

	private VpfxNativeExecutionResult(Builder builder) {
		this.attempted = builder.attempted;
		this.copySucceeded = builder.copySucceeded;
		this.pipelineCreated = builder.pipelineCreated;
		this.renderPassCreated = builder.renderPassCreated;
		this.drawExecuted = builder.drawExecuted;
		this.nativeSucceeded = builder.nativeSucceeded;
		this.fallbackStillActive = builder.fallbackStillActive;
		this.fallbackReason = builder.fallbackReason != null ? builder.fallbackReason : "none";
		this.diagnosticMode = builder.diagnosticMode;
		this.userShaderNativeExecution = builder.userShaderNativeExecution;
		this.builtinPassthroughOnly = builder.builtinPassthroughOnly;
		this.userPipelineAvailable = builder.userPipelineAvailable;
		this.userPipelineAttempted = builder.userPipelineAttempted;
		this.userPipelineCached = builder.userPipelineCached;
		this.userPipelineFallbackUsed = builder.userPipelineFallbackUsed;
		this.actualPipeline = builder.actualPipeline != null ? builder.actualPipeline : "builtin passthrough";
		this.pipelineFallbackReason = builder.pipelineFallbackReason != null ? builder.pipelineFallbackReason : "none";
		this.failureStage = builder.failureStage != null ? builder.failureStage : VpfxNativeFailureStage.NONE;
		this.failureMessage = builder.failureMessage != null ? builder.failureMessage : "none";
		this.postChainFallbackExpected = builder.postChainFallbackExpected;
		this.builtinFallbackAttempted = builder.builtinFallbackAttempted;
		this.builtinFallbackSucceeded = builder.builtinFallbackSucceeded;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static VpfxNativeExecutionResult notAttempted() {
		return builder().build();
	}

	public boolean attempted() {
		return attempted;
	}

	public boolean copySucceeded() {
		return copySucceeded;
	}

	public boolean pipelineCreated() {
		return pipelineCreated;
	}

	public boolean renderPassCreated() {
		return renderPassCreated;
	}

	public boolean drawExecuted() {
		return drawExecuted;
	}

	public boolean nativeSucceeded() {
		return nativeSucceeded;
	}

	public boolean fallbackStillActive() {
		return fallbackStillActive;
	}

	public String fallbackReason() {
		return fallbackReason;
	}

	public boolean diagnosticMode() {
		return diagnosticMode;
	}

	public boolean userShaderNativeExecution() {
		return userShaderNativeExecution;
	}

	public boolean builtinPassthroughOnly() {
		return builtinPassthroughOnly;
	}

	public boolean userPipelineAvailable() {
		return userPipelineAvailable;
	}

	public boolean userPipelineAttempted() {
		return userPipelineAttempted;
	}

	public boolean userPipelineCached() {
		return userPipelineCached;
	}

	public boolean userPipelineFallbackUsed() {
		return userPipelineFallbackUsed;
	}

	public String actualPipeline() {
		return actualPipeline;
	}

	public String pipelineFallbackReason() {
		return pipelineFallbackReason;
	}

	public VpfxNativeFailureStage failureStage() {
		return failureStage;
	}

	public String failureMessage() {
		return failureMessage;
	}

	public boolean postChainFallbackExpected() {
		return postChainFallbackExpected;
	}

	public boolean builtinFallbackAttempted() {
		return builtinFallbackAttempted;
	}

	public boolean builtinFallbackSucceeded() {
		return builtinFallbackSucceeded;
	}

	@Override
	public String toString() {
		return "VpfxNativeExecutionResult{" +
				"attempted=" + attempted +
				", copySucceeded=" + copySucceeded +
				", pipelineCreated=" + pipelineCreated +
				", renderPassCreated=" + renderPassCreated +
				", drawExecuted=" + drawExecuted +
				", nativeSucceeded=" + nativeSucceeded +
				", fallbackStillActive=" + fallbackStillActive +
				", diagnosticMode=" + diagnosticMode +
				", builtinPassthroughOnly=" + builtinPassthroughOnly +
				", userShaderNativeExecution=" + userShaderNativeExecution +
				'}';
	}

	public static final class Builder {
		private boolean attempted;
		private boolean copySucceeded;
		private boolean pipelineCreated;
		private boolean renderPassCreated;
		private boolean drawExecuted;
		private boolean nativeSucceeded;
		private boolean fallbackStillActive = true;
		private String fallbackReason = "none";
		private boolean diagnosticMode = true;
		private boolean userShaderNativeExecution;
		private boolean builtinPassthroughOnly = true;
		private boolean userPipelineAvailable;
		private boolean userPipelineAttempted;
		private boolean userPipelineCached;
		private boolean userPipelineFallbackUsed;
		private String actualPipeline = "builtin passthrough";
		private String pipelineFallbackReason = "none";
		VpfxNativeFailureStage failureStage = VpfxNativeFailureStage.NONE;
		private String failureMessage = "none";
		private boolean postChainFallbackExpected;
		private boolean builtinFallbackAttempted;
		private boolean builtinFallbackSucceeded;

		private Builder() {
		}

		public Builder attempted(boolean value) {
			this.attempted = value;
			return this;
		}

		public Builder copySucceeded(boolean value) {
			this.copySucceeded = value;
			return this;
		}

		public Builder pipelineCreated(boolean value) {
			this.pipelineCreated = value;
			return this;
		}

		public Builder renderPassCreated(boolean value) {
			this.renderPassCreated = value;
			return this;
		}

		public Builder drawExecuted(boolean value) {
			this.drawExecuted = value;
			return this;
		}

		public Builder nativeSucceeded(boolean value) {
			this.nativeSucceeded = value;
			return this;
		}

		public Builder fallbackStillActive(boolean value) {
			this.fallbackStillActive = value;
			return this;
		}

		public Builder fallbackReason(String reason) {
			this.fallbackReason = reason;
			return this;
		}

		public Builder diagnosticMode(boolean value) {
			this.diagnosticMode = value;
			return this;
		}

		public Builder userShaderNativeExecution(boolean value) {
			this.userShaderNativeExecution = value;
			return this;
		}

		public Builder builtinPassthroughOnly(boolean value) {
			this.builtinPassthroughOnly = value;
			return this;
		}

		public Builder userPipelineAvailable(boolean value) {
			this.userPipelineAvailable = value;
			return this;
		}

		public Builder userPipelineAttempted(boolean value) {
			this.userPipelineAttempted = value;
			return this;
		}

		public Builder userPipelineCached(boolean value) {
			this.userPipelineCached = value;
			return this;
		}

		public Builder userPipelineFallbackUsed(boolean value) {
			this.userPipelineFallbackUsed = value;
			return this;
		}

		public Builder actualPipeline(String value) {
			this.actualPipeline = value;
			return this;
		}

		public Builder pipelineFallbackReason(String value) {
			this.pipelineFallbackReason = value;
			return this;
		}

		public Builder failureStage(VpfxNativeFailureStage value) {
			this.failureStage = value;
			return this;
		}

		public Builder failureMessage(String value) {
			this.failureMessage = value;
			return this;
		}

		public Builder postChainFallbackExpected(boolean value) {
			this.postChainFallbackExpected = value;
			return this;
		}

		public Builder builtinFallbackAttempted(boolean value) {
			this.builtinFallbackAttempted = value;
			return this;
		}

		public Builder builtinFallbackSucceeded(boolean value) {
			this.builtinFallbackSucceeded = value;
			return this;
		}

		public VpfxNativeFailureStage failureStage() {
			return failureStage;
		}

		public VpfxNativeExecutionResult build() {
			return new VpfxNativeExecutionResult(this);
		}
	}
}
