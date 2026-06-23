package com.ionhex975.vulkanpostfx.client.effect;

import net.minecraft.resources.Identifier;

/**
 * 一个最小效果定义：
 * - primaryId: 优先加载的效果资源
 * - fallbackId: primary 加载失败时的回退资源
 * - displayName: 日志/调试显示名
 */
public final class PostFxEffectDefinition {
    private final Identifier primaryId;
    private final Identifier fallbackId;
    private final String displayName;

    public PostFxEffectDefinition(Identifier primaryId, Identifier fallbackId, String displayName) {
        this.primaryId = primaryId;
        this.fallbackId = fallbackId;
        this.displayName = displayName;
    }

    public Identifier primaryId() {
        return primaryId;
    }

    public Identifier fallbackId() {
        return fallbackId;
    }

    public String displayName() {
        return displayName;
    }
}