package com.ionhex975.vulkanpostfx.client.input;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.gui.VpfxScreenBridge;
import com.ionhex975.vulkanpostfx.client.gui.VpfxShaderPackSelectionScreen;
import com.ionhex975.vulkanpostfx.client.reload.VpfxHotReloadManager;
import com.ionhex975.vulkanpostfx.client.state.PostFxRuntimeState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class PostFxDebugKeybinds {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("vulkanpostfx", "general"));

    private static final String OPEN_SHADER_PACK_MENU_KEY = "key.vulkanpostfx.open_shader_pack_menu";
    private static final String TOGGLE_DEBUG_EFFECT_KEY = "key.vulkanpostfx.toggle_debug_effect";
    private static final String TOGGLE_SHADOW_DEPTH_DEBUG_KEY = "key.vulkanpostfx.toggle_shadow_depth_debug";
    private static final String TOGGLE_DEBUG_HUD_KEY = "key.vulkanpostfx.toggle_debug_hud";
    private static final String HOT_RELOAD_SHADER_PACK_KEY = "key.vulkanpostfx.hot_reload_shader_pack";

    private static KeyMapping openShaderPackMenuKey;
    private static KeyMapping toggleDebugEffectKey;
    private static KeyMapping toggleShadowDepthDebugKey;
    private static KeyMapping toggleDebugHudKey;
    private static KeyMapping hotReloadShaderPackKey;

    private static boolean openMenuKeyWasDownLastTick;
    private static boolean toggleKeyWasDownLastTick;
    private static boolean shadowDepthDebugKeyWasDownLastTick;
    private static boolean debugHudKeyWasDownLastTick;
    private static boolean hotReloadKeyWasDownLastTick;
    private static boolean initialized;

    private PostFxDebugKeybinds() {
    }

    public static void init() {
        getOrCreateOpenShaderPackMenuKey();
        getOrCreateToggleDebugEffectKey();
        getOrCreateToggleShadowDepthDebugKey();
        getOrCreateToggleDebugHudKey();
        getOrCreateHotReloadShaderPackKey();

        if (initialized) {
            return;
        }

        initialized = true;

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean openMenuIsDownNow = openShaderPackMenuKey.isDown();
            boolean toggleIsDownNow = toggleDebugEffectKey.isDown();
            boolean shadowDepthDebugIsDownNow = toggleShadowDepthDebugKey.isDown();
            boolean debugHudIsDownNow = toggleDebugHudKey.isDown();
            boolean hotReloadIsDownNow = hotReloadShaderPackKey.isDown();

            if (openMenuIsDownNow && !openMenuKeyWasDownLastTick) {
                VpfxScreenBridge.open(new VpfxShaderPackSelectionScreen(VpfxScreenBridge.currentScreen()));

                VulkanPostFX.LOGGER.info(
                        "[{}] VPFX settings screen opened via F7",
                        VulkanPostFX.MOD_ID
                );
            }

            if (toggleIsDownNow && !toggleKeyWasDownLastTick) {
                boolean enabled = PostFxRuntimeState.toggleDebugEffectEnabled();

                VulkanPostFX.LOGGER.info(
                        "[{}] Shader toggle via F8: {}",
                        VulkanPostFX.MOD_ID,
                        enabled ? "shader ON / vanilla OFF" : "shader OFF / vanilla ON"
                );
            }

            if (shadowDepthDebugIsDownNow && !shadowDepthDebugKeyWasDownLastTick) {
                boolean enabled = PostFxRuntimeState.toggleShadowDepthDebugView();

                VulkanPostFX.LOGGER.info(
                        "[{}] Shadow depth debug view toggled: {}",
                        VulkanPostFX.MOD_ID,
                        enabled ? "shadow_depth direct view ON" : "shadow_depth direct view OFF"
                );
            }

            if (debugHudIsDownNow && !debugHudKeyWasDownLastTick) {
                boolean enabled = PostFxRuntimeState.toggleDebugHudVisible();

                VulkanPostFX.LOGGER.info(
                        "[{}] VPFX status HUD toggled: {}",
                        VulkanPostFX.MOD_ID,
                        enabled ? "ON" : "OFF"
                );
            }

            if (hotReloadIsDownNow && !hotReloadKeyWasDownLastTick) {
                VpfxHotReloadManager.hotReloadCurrentPack(client, "keybind:F10");

                VulkanPostFX.LOGGER.info(
                        "[{}] VPFX hot reload requested via F10",
                        VulkanPostFX.MOD_ID
                );
            }

            openMenuKeyWasDownLastTick = openMenuIsDownNow;
            toggleKeyWasDownLastTick = toggleIsDownNow;
            shadowDepthDebugKeyWasDownLastTick = shadowDepthDebugIsDownNow;
            debugHudKeyWasDownLastTick = debugHudIsDownNow;
            hotReloadKeyWasDownLastTick = hotReloadIsDownNow;
        });
    }

    public static KeyMapping getOrCreateOpenShaderPackMenuKey() {
        if (openShaderPackMenuKey == null) {
            openShaderPackMenuKey = new KeyMapping(
                    OPEN_SHADER_PACK_MENU_KEY,
                    GLFW.GLFW_KEY_F7,
                    CATEGORY
            );
        }

        return openShaderPackMenuKey;
    }

    public static KeyMapping getOrCreateToggleDebugEffectKey() {
        if (toggleDebugEffectKey == null) {
            toggleDebugEffectKey = new KeyMapping(
                    TOGGLE_DEBUG_EFFECT_KEY,
                    GLFW.GLFW_KEY_F8,
                    CATEGORY
            );
        }

        return toggleDebugEffectKey;
    }

    public static KeyMapping getOrCreateToggleShadowDepthDebugKey() {
        if (toggleShadowDepthDebugKey == null) {
            toggleShadowDepthDebugKey = new KeyMapping(
                    TOGGLE_SHADOW_DEPTH_DEBUG_KEY,
                    GLFW.GLFW_KEY_UNKNOWN,
                    CATEGORY
            );
        }

        return toggleShadowDepthDebugKey;
    }


    public static KeyMapping getOrCreateToggleDebugHudKey() {
        if (toggleDebugHudKey == null) {
            toggleDebugHudKey = new KeyMapping(
                    TOGGLE_DEBUG_HUD_KEY,
                    GLFW.GLFW_KEY_UNKNOWN,
                    CATEGORY
            );
        }

        return toggleDebugHudKey;
    }

    public static KeyMapping getOrCreateHotReloadShaderPackKey() {
        if (hotReloadShaderPackKey == null) {
            hotReloadShaderPackKey = new KeyMapping(
                    HOT_RELOAD_SHADER_PACK_KEY,
                    GLFW.GLFW_KEY_F10,
                    CATEGORY
            );
        }

        return hotReloadShaderPackKey;
    }

    public static KeyMapping getOpenShaderPackMenuKey() {
        return openShaderPackMenuKey;
    }

    public static KeyMapping getToggleDebugEffectKey() {
        return toggleDebugEffectKey;
    }

    public static KeyMapping getToggleShadowDepthDebugKey() {
        return toggleShadowDepthDebugKey;
    }

    public static KeyMapping getToggleDebugHudKey() {
        return toggleDebugHudKey;
    }

    public static KeyMapping getHotReloadShaderPackKey() {
        return hotReloadShaderPackKey;
    }
}