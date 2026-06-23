package com.ionhex975.vulkanpostfx.client.pack;

/**
 * 光影包最小清单。
 *
 * 当前字段：
 * - id: 包唯一标识
 * - name: 展示名称
 * - version: 包格式版本
 * - entryEffectKey: 默认入口效果逻辑名
 * - entryPostEffect: 默认入口后处理资源路径（包内路径）
 */
public final class ShaderPackManifest {
    private final String id;
    private final String name;
    private final int version;
    private final String entryEffectKey;
    private final String entryPostEffect;

    public ShaderPackManifest(
            String id,
            String name,
            int version,
            String entryEffectKey,
            String entryPostEffect
    ) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.entryEffectKey = entryEffectKey;
        this.entryPostEffect = entryPostEffect;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public int version() {
        return version;
    }

    public String entryEffectKey() {
        return entryEffectKey;
    }

    public String entryPostEffect() {
        return entryPostEffect;
    }
}