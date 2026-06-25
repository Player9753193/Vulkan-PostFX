package com.ionhex975.vulkanpostfx.client.ui;

import net.minecraft.network.chat.Component;

public enum VpfxSettingsCategory {
    PACKS("category.vulkanpostfx.packs"),
    GENERAL("category.vulkanpostfx.general"),
    BACKEND("category.vulkanpostfx.backend"),
    DEBUG("category.vulkanpostfx.debug"),
    DEVELOPER("category.vulkanpostfx.developer"),
    ABOUT("category.vulkanpostfx.about");

    private final String translationKey;

    VpfxSettingsCategory(String translationKey) {
        this.translationKey = translationKey;
    }

    public Component text() {
        return Component.translatable(translationKey);
    }

    public String displayName() {
        return text().getString();
    }

    public String translationKey() {
        return translationKey;
    }
}
