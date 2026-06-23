package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx;

public final class VpfxNativeRuntimeSupportResult {

	private final boolean resultSupported;
	private final String resultReason;

	private VpfxNativeRuntimeSupportResult(boolean resultSupported, String resultReason) {
		this.resultSupported = resultSupported;
		this.resultReason = resultReason;
	}

	public static VpfxNativeRuntimeSupportResult supported() {
		return new VpfxNativeRuntimeSupportResult(true, "supported");
	}

	public static VpfxNativeRuntimeSupportResult unsupported(String reason) {
		return new VpfxNativeRuntimeSupportResult(false, reason);
	}

	public boolean isSupported() {
		return resultSupported;
	}

	public String reason() {
		return resultReason;
	}

	@Override
	public String toString() {
		return "VpfxNativeRuntimeSupportResult{" + "supported=" + resultSupported + ", reason='" + resultReason + '\'' + '}';
	}
}
