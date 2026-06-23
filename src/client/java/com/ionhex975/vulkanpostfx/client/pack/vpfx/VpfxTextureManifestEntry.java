package com.ionhex975.vulkanpostfx.client.pack.vpfx;

public final class VpfxTextureManifestEntry {
    private final String name;
    private final String path;
    private final VpfxTextureFilter filter;
    private final VpfxTextureWrap wrap;

    public VpfxTextureManifestEntry(
            String name,
            String path,
            VpfxTextureFilter filter,
            VpfxTextureWrap wrap
    ) {
        this.name = name;
        this.path = path;
        this.filter = filter;
        this.wrap = wrap;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public VpfxTextureFilter getFilter() {
        return filter;
    }

    public VpfxTextureWrap getWrap() {
        return wrap;
    }

    @Override
    public String toString() {
        return "VpfxTextureManifestEntry{" +
                "name='" + name + '\'' +
                ", path='" + path + '\'' +
                ", filter=" + filter +
                ", wrap=" + wrap +
                '}';
    }
}