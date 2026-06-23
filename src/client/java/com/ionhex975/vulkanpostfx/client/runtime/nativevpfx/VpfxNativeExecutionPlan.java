package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx;

public final class VpfxNativeExecutionPlan {

	private final String packId;
	private final String packName;
	private final VpfxNativeResolvedGraph resolvedGraph;
	private final VpfxNativeResolvedResource mainTargetResource;
	private final boolean sameTargetHazard;
	private final boolean transientTempRequired;
	private final boolean wouldExecute;
	private final String fallbackBackend;

	public VpfxNativeExecutionPlan(
			String packId,
			String packName,
			VpfxNativeResolvedGraph resolvedGraph,
			VpfxNativeResolvedResource mainTargetResource,
			boolean sameTargetHazard,
			boolean transientTempRequired,
			boolean wouldExecute,
			String fallbackBackend
	) {
		this.packId = packId != null ? packId : "unknown";
		this.packName = packName != null ? packName : "unknown";
		this.resolvedGraph = resolvedGraph;
		this.mainTargetResource = mainTargetResource;
		this.sameTargetHazard = sameTargetHazard;
		this.transientTempRequired = transientTempRequired;
		this.wouldExecute = wouldExecute;
		this.fallbackBackend = fallbackBackend != null ? fallbackBackend : "minecraft_postchain";
	}

	public String packId() {
		return packId;
	}

	public String packName() {
		return packName;
	}

	public VpfxNativeResolvedGraph resolvedGraph() {
		return resolvedGraph;
	}

	public VpfxNativeResolvedResource mainTargetResource() {
		return mainTargetResource;
	}

	public boolean sameTargetHazard() {
		return sameTargetHazard;
	}

	public boolean transientTempRequired() {
		return transientTempRequired;
	}

	public boolean wouldExecute() {
		return wouldExecute;
	}

	public String fallbackBackend() {
		return fallbackBackend;
	}

	public int resolvedPassCount() {
		return resolvedGraph != null ? resolvedGraph.resolvedPassCount() : 0;
	}

	public int tempResourceCount() {
		return resolvedGraph != null ? resolvedGraph.tempResourceCount() : 0;
	}

	@Override
	public String toString() {
		return "ExecutionPlan{" + "packId='" + packId + '\'' + ", passes=" + resolvedPassCount()
				+ ", tempResources=" + tempResourceCount() + ", hazard=" + sameTargetHazard
				+ ", transientRequired=" + transientTempRequired + ", wouldExecute=" + wouldExecute
				+ ", fallback=" + fallbackBackend + '}';
	}
}
