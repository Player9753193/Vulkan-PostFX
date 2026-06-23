package com.ionhex975.vulkanpostfx.client.pack.vpfx;

import java.util.List;

public final class VpfxPackMetadata {
    private final String homepage;
    private final String license;
    private final List<String> tags;

    public VpfxPackMetadata(String homepage, String license, List<String> tags) {
        this.homepage = homepage == null ? "" : homepage;
        this.license = license == null ? "" : license;
        this.tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public String getHomepage() {
        return homepage;
    }

    public String getLicense() {
        return license;
    }

    public List<String> getTags() {
        return tags;
    }

    @Override
    public String toString() {
        return "VpfxPackMetadata{" +
                "homepage='" + homepage + '\'' +
                ", license='" + license + '\'' +
                ", tags=" + tags +
                '}';
    }
}