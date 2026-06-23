package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.graph;

import com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.VpfxPassType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class VpfxNativePassNode {

	private final String passId;
	private final VpfxPassType passType;
	private final String vertexShaderRef;
	private final String fragmentShaderRef;
	private final List<VpfxNativeInputBinding> inputs;
	private final List<VpfxNativeOutputBinding> outputs;
	private final String samplerConvention;

	private VpfxNativePassNode(Builder builder) {
		this.passId = builder.passId != null ? builder.passId : "(unnamed)";
		this.passType = builder.passType != null ? builder.passType : VpfxPassType.FULLSCREEN;
		this.vertexShaderRef = builder.vertexShaderRef != null ? builder.vertexShaderRef : "";
		this.fragmentShaderRef = builder.fragmentShaderRef != null ? builder.fragmentShaderRef : "";
		this.inputs = Collections.unmodifiableList(new ArrayList<>(builder.inputs));
		this.outputs = Collections.unmodifiableList(new ArrayList<>(builder.outputs));
		this.samplerConvention = builder.samplerConvention != null ? builder.samplerConvention : "InSampler";
	}

	public static Builder builder() {
		return new Builder();
	}

	public String passId() { return passId; }
	public VpfxPassType passType() { return passType; }
	public String vertexShaderRef() { return vertexShaderRef; }
	public String fragmentShaderRef() { return fragmentShaderRef; }
	public List<VpfxNativeInputBinding> inputs() { return inputs; }
	public List<VpfxNativeOutputBinding> outputs() { return outputs; }
	public String samplerConvention() { return samplerConvention; }

	public boolean isFullscreen() {
		return passType == VpfxPassType.FULLSCREEN;
	}

	@Override
	public String toString() {
		return "PassNode{id='" + passId + "', type=" + passType
				+ ", vs='" + vertexShaderRef + "', fs='" + fragmentShaderRef
				+ "', inputs=" + inputs + ", outputs=" + outputs
				+ ", sampler=" + samplerConvention + '}';
	}

	public static final class Builder {
		private String passId;
		private VpfxPassType passType = VpfxPassType.FULLSCREEN;
		private String vertexShaderRef;
		private String fragmentShaderRef;
		private final List<VpfxNativeInputBinding> inputs = new ArrayList<>();
		private final List<VpfxNativeOutputBinding> outputs = new ArrayList<>();
		private String samplerConvention = "InSampler";

		private Builder() {}

		public Builder passId(String v) { this.passId = v; return this; }
		public Builder passType(VpfxPassType v) { this.passType = v; return this; }
		public Builder vertexShaderRef(String v) { this.vertexShaderRef = v; return this; }
		public Builder fragmentShaderRef(String v) { this.fragmentShaderRef = v; return this; }
		public Builder addInput(VpfxNativeInputBinding v) { this.inputs.add(v); return this; }
		public Builder addOutput(VpfxNativeOutputBinding v) { this.outputs.add(v); return this; }
		public Builder samplerConvention(String v) { this.samplerConvention = v; return this; }

		public VpfxNativePassNode build() { return new VpfxNativePassNode(this); }
	}
}
