package com.ionhex975.vulkanpostfx.client.pack;

import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxNativePackDefinition;

import java.nio.file.Path;

/**
 * 一个已识别的光影包容器。
 *
 * 现在支持两种来源：
 * 1) 旧 builtin/legacy manifest
 * 2) VPFX native pack definition（正式主线）
 *
 * 当前策略：
 * - 运行时后半段仍通过 manifest/resourceIndex/sourcePath 工作
 * - VPFX 原生解析结果通过 vpfxDefinition 附带保存，供后续主线继续替换
 */
public final class ShaderPackContainer {
    private final ShaderPackManifest manifest;
    private final String sourceId;
    private final Path sourcePath;
    private final ShaderPackResourceIndex resourceIndex;
    private final VpfxNativePackDefinition vpfxDefinition;

    public ShaderPackContainer(ShaderPackManifest manifest, String sourceId) {
        this(manifest, sourceId, null, ShaderPackResourceIndex.empty(), null);
    }

    public ShaderPackContainer(
            ShaderPackManifest manifest,
            String sourceId,
            Path sourcePath
    ) {
        this(manifest, sourceId, sourcePath, ShaderPackResourceIndex.empty(), null);
    }

    public ShaderPackContainer(
            ShaderPackManifest manifest,
            String sourceId,
            Path sourcePath,
            ShaderPackResourceIndex resourceIndex
    ) {
        this(manifest, sourceId, sourcePath, resourceIndex, null);
    }

    public ShaderPackContainer(
            ShaderPackManifest manifest,
            String sourceId,
            Path sourcePath,
            ShaderPackResourceIndex resourceIndex,
            VpfxNativePackDefinition vpfxDefinition
    ) {
        this.manifest = manifest;
        this.sourceId = sourceId;
        this.sourcePath = sourcePath;
        this.resourceIndex = resourceIndex;
        this.vpfxDefinition = vpfxDefinition;
    }

    public ShaderPackManifest manifest() {
        return manifest;
    }

    public String sourceId() {
        return sourceId;
    }

    public Path sourcePath() {
        return sourcePath;
    }

    public ShaderPackResourceIndex resourceIndex() {
        return resourceIndex;
    }

    public boolean isVpfxNativePack() {
        return vpfxDefinition != null;
    }

    public VpfxNativePackDefinition vpfxDefinition() {
        return vpfxDefinition;
    }
}