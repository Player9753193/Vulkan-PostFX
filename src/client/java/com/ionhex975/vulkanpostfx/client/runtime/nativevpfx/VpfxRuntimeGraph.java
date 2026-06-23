package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx;

import java.util.List;

public final class VpfxRuntimeGraph {

	private final List<VpfxRuntimePass> passes;

	public VpfxRuntimeGraph(List<VpfxRuntimePass> passes) {
		this.passes = List.copyOf(passes);
	}

	public List<VpfxRuntimePass> passes() {
		return passes;
	}

	public int passCount() {
		return passes.size();
	}

	public boolean isEmpty() {
		return passes.isEmpty();
	}
}
