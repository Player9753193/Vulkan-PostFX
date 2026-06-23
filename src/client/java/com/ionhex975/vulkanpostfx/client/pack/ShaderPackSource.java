package com.ionhex975.vulkanpostfx.client.pack;

import java.util.Collection;

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
}