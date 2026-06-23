package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.resources.Identifier;

public final class VpfxNativeUserPipelineResolveResult {

	private final boolean attempted;
	private final boolean available;
	private final boolean successCached;
	private final boolean failureCached;
	private final RenderPipeline pipeline;
	private final VpfxNativePipelineKey key;
	private final String fallbackReason;
	private final Identifier vertexShaderId;
	private final Identifier fragmentShaderId;
	private final VpfxNativeFailureStage failureStage;
	private final String failureMessage;

	private VpfxNativeUserPipelineResolveResult(Builder builder) {
		this.attempted = builder.attempted;
		this.available = builder.available;
		this.successCached = builder.successCached;
		this.failureCached = builder.failureCached;
		this.pipeline = builder.pipeline;
		this.key = builder.key;
		this.fallbackReason = builder.fallbackReason != null ? builder.fallbackReason : "none";
		this.vertexShaderId = builder.vertexShaderId;
		this.fragmentShaderId = builder.fragmentShaderId;
		this.failureStage = builder.failureStage != null ? builder.failureStage : VpfxNativeFailureStage.NONE;
		this.failureMessage = builder.failureMessage != null ? builder.failureMessage : "none";
	}

	public static Builder builder() {
		return new Builder();
	}

	public boolean attempted() {
		return attempted;
	}

	public boolean available() {
		return available;
	}

	public boolean successCached() {
		return successCached;
	}

	public boolean failureCached() {
		return failureCached;
	}

	public RenderPipeline pipeline() {
		return pipeline;
	}

	public VpfxNativePipelineKey key() {
		return key;
	}

	public String fallbackReason() {
		return fallbackReason;
	}

	public Identifier vertexShaderId() {
		return vertexShaderId;
	}

	public Identifier fragmentShaderId() {
		return fragmentShaderId;
	}

	public VpfxNativeFailureStage failureStage() {
		return failureStage;
	}

	public String failureMessage() {
		return failureMessage;
	}

	public static final class Builder {
		private boolean attempted;
		private boolean available;
		private boolean successCached;
		private boolean failureCached;
		private RenderPipeline pipeline;
		private VpfxNativePipelineKey key;
		private String fallbackReason = "none";
		private Identifier vertexShaderId;
		private Identifier fragmentShaderId;
		private VpfxNativeFailureStage failureStage = VpfxNativeFailureStage.NONE;
		private String failureMessage = "none";

		private Builder() {
		}

		public Builder attempted(boolean value) {
			this.attempted = value;
			return this;
		}

		public Builder available(boolean value) {
			this.available = value;
			return this;
		}

		public Builder successCached(boolean value) {
			this.successCached = value;
			return this;
		}

		public Builder failureCached(boolean value) {
			this.failureCached = value;
			return this;
		}

		public Builder pipeline(RenderPipeline value) {
			this.pipeline = value;
			return this;
		}

		public Builder key(VpfxNativePipelineKey value) {
			this.key = value;
			return this;
		}

		public Builder fallbackReason(String value) {
			this.fallbackReason = value;
			return this;
		}

		public Builder vertexShaderId(Identifier value) {
			this.vertexShaderId = value;
			return this;
		}

		public Builder fragmentShaderId(Identifier value) {
			this.fragmentShaderId = value;
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

		public VpfxNativeUserPipelineResolveResult build() {
			return new VpfxNativeUserPipelineResolveResult(this);
		}
	}
}
