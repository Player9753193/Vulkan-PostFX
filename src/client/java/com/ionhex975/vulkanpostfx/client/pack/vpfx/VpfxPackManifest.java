package com.ionhex975.vulkanpostfx.client.pack.vpfx;

import java.util.Map;

public final class VpfxPackManifest {
    private final int formatVersion;
    private final String packId;
    private final String name;
    private final String version;
    private final String author;
    private final String description;
    private final String entryPostEffect;
    private final VpfxCapabilitySet capabilities;
    private final Map<String, String> targets;
    private final Map<String, VpfxTextureManifestEntry> textures;
    private final VpfxPackMetadata metadata;

    public VpfxPackManifest(
            int formatVersion,
            String packId,
            String name,
            String version,
            String author,
            String description,
            String entryPostEffect,
            VpfxCapabilitySet capabilities,
            Map<String, String> targets,
            Map<String, VpfxTextureManifestEntry> textures,
            VpfxPackMetadata metadata
    ) {
        this.formatVersion = formatVersion;
        this.packId = packId;
        this.name = name;
        this.version = version;
        this.author = author == null ? "" : author;
        this.description = description == null ? "" : description;
        this.entryPostEffect = entryPostEffect;
        this.capabilities = capabilities;
        this.targets = Map.copyOf(targets);
        this.textures = Map.copyOf(textures);
        this.metadata = metadata;
    }

    public int getFormatVersion() {
        return formatVersion;
    }

    public String getPackId() {
        return packId;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getAuthor() {
        return author;
    }

    public String getDescription() {
        return description;
    }

    public String getEntryPostEffect() {
        return entryPostEffect;
    }

    public VpfxCapabilitySet getCapabilities() {
        return capabilities;
    }

    public Map<String, String> getTargets() {
        return targets;
    }

    public Map<String, VpfxTextureManifestEntry> getTextures() {
        return textures;
    }

    public VpfxPackMetadata getMetadata() {
        return metadata;
    }

    @Override
    public String toString() {
        return "VpfxPackManifest{" +
                "formatVersion=" + formatVersion +
                ", packId='" + packId + '\'' +
                ", name='" + name + '\'' +
                ", version='" + version + '\'' +
                ", entryPostEffect='" + entryPostEffect + '\'' +
                ", capabilities=" + capabilities +
                ", targets=" + targets +
                ", textures=" + textures +
                '}';
    }
}