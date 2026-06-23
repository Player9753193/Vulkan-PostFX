package com.ionhex975.vulkanpostfx.client.mixin;

import com.ionhex975.vulkanpostfx.client.input.PostFxDebugKeybinds;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Mixin(Options.class)
public abstract class OptionsKeyMappingMixin {
    @Shadow
    @Final
    @Mutable
    public KeyMapping[] keyMappings;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void vulkanpostfx$appendCustomKeyMappings(
            Minecraft minecraft,
            File workingDirectory,
            CallbackInfo ci
    ) {
        List<KeyMapping> existing = new ArrayList<>(Arrays.asList(this.keyMappings));

        appendIfMissing(existing, PostFxDebugKeybinds.getOrCreateOpenShaderPackMenuKey());
        appendIfMissing(existing, PostFxDebugKeybinds.getOrCreateToggleDebugEffectKey());
        appendIfMissing(existing, PostFxDebugKeybinds.getOrCreateToggleShadowDepthDebugKey());
        appendIfMissing(existing, PostFxDebugKeybinds.getOrCreateToggleDebugHudKey());
        appendIfMissing(existing, PostFxDebugKeybinds.getOrCreateHotReloadShaderPackKey());

        if (existing.size() != this.keyMappings.length) {
            this.keyMappings = existing.toArray(KeyMapping[]::new);
        }
    }

    private static void appendIfMissing(List<KeyMapping> keyMappings, KeyMapping keyMapping) {
        if (!keyMappings.contains(keyMapping)) {
            keyMappings.add(keyMapping);
        }
    }
}