package com.ionhex975.vulkanpostfx.client.pack;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 光影包资源索引。
 *
 * 当前阶段只做最小能力：
 * - 记录包内有哪些资源路径
 * - 支持 exists(path) 查询
 *
 * 后面可以继续扩展：
 * - 列目录
 * - 读取字节
 * - 过滤特定后缀
 */
public final class ShaderPackResourceIndex {
    private final Set<String> paths;

    public ShaderPackResourceIndex(Set<String> paths) {
        this.paths = Collections.unmodifiableSet(new LinkedHashSet<>(paths));
    }

    public boolean exists(String path) {
        return paths.contains(normalize(path));
    }

    public Set<String> allPaths() {
        return paths;
    }

    public int size() {
        return paths.size();
    }

    public static String normalize(String path) {
        return path.replace('\\', '/');
    }

    public static ShaderPackResourceIndex empty() {
        return new ShaderPackResourceIndex(Set.of());
    }
}