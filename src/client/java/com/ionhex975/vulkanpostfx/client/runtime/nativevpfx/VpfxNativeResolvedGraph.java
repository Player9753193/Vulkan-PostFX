package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class VpfxNativeResolvedGraph {

	private final List<VpfxNativeResolvedPass> resolvedPasses;
	private final int tempResourceCount;

	public VpfxNativeResolvedGraph(List<VpfxNativeResolvedPass> resolvedPasses) {
		this.resolvedPasses = Collections.unmodifiableList(new ArrayList<>(resolvedPasses));
		int count = 0;
		for (VpfxNativeResolvedPass pass : this.resolvedPasses) {
			count += pass.tempResourceCount();
		}
		this.tempResourceCount = count;
	}

	public List<VpfxNativeResolvedPass> resolvedPasses() {
		return resolvedPasses;
	}

	public int resolvedPassCount() {
		return resolvedPasses.size();
	}

	public int tempResourceCount() {
		return tempResourceCount;
	}

	public boolean isEmpty() {
		return resolvedPasses.isEmpty();
	}

	@Override
	public String toString() {
		return "ResolvedGraph{" + "passes=" + resolvedPasses.size()
				+ ", tempResources=" + tempResourceCount + '}';
	}
}
