package com.ionhex975.vulkanpostfx.client.ui;

import net.minecraft.network.chat.Component;

public enum VpfxSettingsCategory {
    PACKS("Packs"),
    GENERAL("General"),
    BACKEND("Backend"),
    DEBUG("Debug"),
    DEVELOPER("Developer"),
    ABOUT("About");

    private final String displayName;

    VpfxSettingsCategory(String displayName) {
        this.displayName = displayName;
    }

    public Component text() {
        return Component.literal(displayName);
    }

    public String displayName() {
        return displayName;
    }
}
