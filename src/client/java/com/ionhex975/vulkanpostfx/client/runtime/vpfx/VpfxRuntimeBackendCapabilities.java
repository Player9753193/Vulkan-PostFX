package com.ionhex975.vulkanpostfx.client.runtime.vpfx;

public final class VpfxRuntimeBackendCapabilities {
	private final boolean usesPostChain;
	private final boolean nativeRuntime;
	private final boolean supportsCompute;
	private final boolean supportsShadowDepth;
	private final boolean supportsCustomTargets;

	public VpfxRuntimeBackendCapabilities(
			boolean usesPostChain,
			boolean nativeRuntime,
			boolean supportsCompute,
			boolean supportsShadowDepth,
			boolean supportsCustomTargets
	) {
		this.usesPostChain = usesPostChain;
		this.nativeRuntime = nativeRuntime;
		this.supportsCompute = supportsCompute;
		this.supportsShadowDepth = supportsShadowDepth;
		this.supportsCustomTargets = supportsCustomTargets;
	}

	public boolean usesPostChain() {
		return usesPostChain;
	}

	public boolean nativeRuntime() {
		return nativeRuntime;
	}

	public boolean supportsCompute() {
		return supportsCompute;
	}

	public boolean supportsShadowDepth() {
		return supportsShadowDepth;
	}

	public boolean supportsCustomTargets() {
		return supportsCustomTargets;
	}

	@Override
	public String toString() {
		return "VpfxRuntimeBackendCapabilities{" +
				"usesPostChain=" + usesPostChain +
				", nativeRuntime=" + nativeRuntime +
				", supportsCompute=" + supportsCompute +
				", supportsShadowDepth=" + supportsShadowDepth +
				", supportsCustomTargets=" + supportsCustomTargets +
				'}';
	}
}
