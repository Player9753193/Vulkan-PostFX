package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class VpfxNativeResolvedPass {

	private final String identity;
	private final VpfxPassType passType;
	private final List<VpfxNativeResolvedResource> resolvedInputs;
	private final VpfxNativeResolvedResource resolvedOutput;
	private final boolean readsSceneColor;
	private final boolean writesMain;
	private final boolean sameTargetHazard;
	private final boolean transientTempRequired;
	private final String vertexShader;
	private final String fragmentShader;

	public VpfxNativeResolvedPass(
			String identity,
			VpfxPassType passType,
			List<VpfxNativeResolvedResource> resolvedInputs,
			VpfxNativeResolvedResource resolvedOutput,
			boolean readsSceneColor,
			boolean writesMain,
			boolean sameTargetHazard,
			boolean transientTempRequired,
			String vertexShader,
			String fragmentShader
	) {
		this.identity = identity != null ? identity : "(anonymous)";
		this.passType = passType != null ? passType : VpfxPassType.FULLSCREEN;
		this.resolvedInputs = Collections.unmodifiableList(new ArrayList<>(resolvedInputs));
		this.resolvedOutput = resolvedOutput;
		this.readsSceneColor = readsSceneColor;
		this.writesMain = writesMain;
		this.sameTargetHazard = sameTargetHazard;
		this.transientTempRequired = transientTempRequired;
		this.vertexShader = vertexShader;
		this.fragmentShader = fragmentShader;
	}

	public String identity() {
		return identity;
	}

	public VpfxPassType passType() {
		return passType;
	}

	public List<VpfxNativeResolvedResource> resolvedInputs() {
		return resolvedInputs;
	}

	public VpfxNativeResolvedResource resolvedOutput() {
		return resolvedOutput;
	}

	public boolean readsSceneColor() {
		return readsSceneColor;
	}

	public boolean writesMain() {
		return writesMain;
	}

	public boolean sameTargetHazard() {
		return sameTargetHazard;
	}

	public boolean transientTempRequired() {
		return transientTempRequired;
	}

	public String vertexShader() {
		return vertexShader;
	}

	public String fragmentShader() {
		return fragmentShader;
	}

	public int tempResourceCount() {
		return transientTempRequired ? 1 : 0;
	}

	@Override
	public String toString() {
		return "ResolvedPass{" + "identity='" + identity + '\'' + ", type=" + passType
				+ ", inputs=" + resolvedInputs.size() + ", readsSceneColor=" + readsSceneColor
				+ ", writesMain=" + writesMain + ", hazard=" + sameTargetHazard
				+ ", transient=" + transientTempRequired + '}';
	}
}
