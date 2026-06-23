package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx;

import java.util.List;

public final class VpfxRuntimePass {

	private final VpfxPassType passType;
	private final String id;
	private final String debugLabel;
	private final List<VpfxResourceRef> inputs;
	private final VpfxResourceRef output;
	private final String vertexShader;
	private final String fragmentShader;

	public VpfxRuntimePass(
			VpfxPassType passType,
			String id,
			String debugLabel,
			List<VpfxResourceRef> inputs,
			VpfxResourceRef output,
			String vertexShader,
			String fragmentShader
	) {
		this.passType = passType;
		this.id = id != null ? id : "";
		this.debugLabel = debugLabel != null ? debugLabel : "";
		this.inputs = List.copyOf(inputs);
		this.output = output;
		this.vertexShader = vertexShader;
		this.fragmentShader = fragmentShader;
	}

	public VpfxPassType passType() {
		return passType;
	}

	public String id() {
		return id;
	}

	public String debugLabel() {
		return debugLabel;
	}

	public String identity() {
		if (!id.isBlank()) {
			return id;
		}
		if (!debugLabel.isBlank()) {
			return debugLabel;
		}
		return "(anonymous)";
	}

	public List<VpfxResourceRef> inputs() {
		return inputs;
	}

	public VpfxResourceRef output() {
		return output;
	}

	public String vertexShader() {
		return vertexShader;
	}

	public String fragmentShader() {
		return fragmentShader;
	}

	public int inputCount() {
		return inputs.size();
	}

	public boolean hasAnyTextureInput() {
		return false;
	}

	@Override
	public String toString() {
		return "VpfxRuntimePass{" + "passType=" + passType + ", identity='" + identity() + '\'' + '}';
	}
}
