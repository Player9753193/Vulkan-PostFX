package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx;

import java.util.Objects;

public final class VpfxNativePipelineKey {

	private final String packId;
	private final String passId;
	private final VpfxPassType passType;
	private final String vertexShaderRef;
	private final String fragmentShaderRef;
	private final String outputFormatPlaceholder;
	private final String vertexSourceHash;
	private final String fragmentSourceHash;
	private final String samplerConvention;

	public VpfxNativePipelineKey(
			String packId,
			String passId,
			VpfxPassType passType,
			String vertexShaderRef,
			String fragmentShaderRef,
			String outputFormatPlaceholder
	) {
		this(packId, passId, passType, vertexShaderRef, fragmentShaderRef, outputFormatPlaceholder, "", "", "");
	}

	public VpfxNativePipelineKey(
			String packId,
			String passId,
			VpfxPassType passType,
			String vertexShaderRef,
			String fragmentShaderRef,
			String outputFormatPlaceholder,
			String vertexSourceHash,
			String fragmentSourceHash,
			String samplerConvention
	) {
		this.packId = packId != null ? packId : "unknown";
		this.passId = passId != null ? passId : "(anonymous)";
		this.passType = passType != null ? passType : VpfxPassType.FULLSCREEN;
		this.vertexShaderRef = vertexShaderRef != null ? vertexShaderRef : "";
		this.fragmentShaderRef = fragmentShaderRef != null ? fragmentShaderRef : "";
		this.outputFormatPlaceholder = outputFormatPlaceholder != null ? outputFormatPlaceholder : "RGBA8_UNORM";
		this.vertexSourceHash = vertexSourceHash != null ? vertexSourceHash : "";
		this.fragmentSourceHash = fragmentSourceHash != null ? fragmentSourceHash : "";
		this.samplerConvention = samplerConvention != null ? samplerConvention : "";
	}

	public String packId() {
		return packId;
	}

	public String passId() {
		return passId;
	}

	public VpfxPassType passType() {
		return passType;
	}

	public String vertexShaderRef() {
		return vertexShaderRef;
	}

	public String fragmentShaderRef() {
		return fragmentShaderRef;
	}

	public String outputFormatPlaceholder() {
		return outputFormatPlaceholder;
	}

	public String vertexSourceHash() {
		return vertexSourceHash;
	}

	public String fragmentSourceHash() {
		return fragmentSourceHash;
	}

	public String samplerConvention() {
		return samplerConvention;
	}

	public boolean hasUserShaderHashes() {
		return !vertexSourceHash.isEmpty() && !fragmentSourceHash.isEmpty();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof VpfxNativePipelineKey that)) return false;
		return packId.equals(that.packId)
				&& passId.equals(that.passId)
				&& passType == that.passType
				&& vertexShaderRef.equals(that.vertexShaderRef)
				&& fragmentShaderRef.equals(that.fragmentShaderRef)
				&& outputFormatPlaceholder.equals(that.outputFormatPlaceholder)
				&& vertexSourceHash.equals(that.vertexSourceHash)
				&& fragmentSourceHash.equals(that.fragmentSourceHash)
				&& samplerConvention.equals(that.samplerConvention);
	}

	@Override
	public int hashCode() {
		return Objects.hash(packId, passId, passType, vertexShaderRef, fragmentShaderRef,
				outputFormatPlaceholder, vertexSourceHash, fragmentSourceHash, samplerConvention);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("PipelineKey{pack='").append(packId).append("', pass='").append(passId)
				.append("', type=").append(passType)
				.append(", vs='").append(vertexShaderRef).append("', fs='").append(fragmentShaderRef)
				.append(", format=").append(outputFormatPlaceholder);
		if (!vertexSourceHash.isEmpty()) {
			sb.append(", vs_hash=").append(vertexSourceHash);
		}
		if (!fragmentSourceHash.isEmpty()) {
			sb.append(", fs_hash=").append(fragmentSourceHash);
		}
		if (!samplerConvention.isEmpty()) {
			sb.append(", sampler=").append(samplerConvention);
		}
		sb.append('}');
		return sb.toString();
	}
}
