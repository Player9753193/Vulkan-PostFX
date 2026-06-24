package com.ionhex975.vulkanpostfx.client.pack;

import java.util.Collection;
import java.util.List;

/**
 * 光影包来源抽象。
 *
 * 目标是统一两类来源：
 * - BuiltinShaderPackSource（内置开发包）
 * - ZipShaderPackSource（ZIP 光影包）
 */
public interface ShaderPackSource {
    String id();

    Collection<ShaderPackContainer> discoverPacks();

    /**
     * Discovery issues from the last scan. Valid pack containers are still returned
     * through discoverPacks(); this side channel lets the settings UI show broken
     * or invalid candidates instead of silently dropping them.
     */
    default Collection<ShaderPackScanIssue> getLastScanIssues() {
        return List.of();
    }
}
