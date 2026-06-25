package com.ionhex975.vulkanpostfx.client.effect;

import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 最小效果注册表。
 *
 * 当前支持两个可见调试效果：
 * - debug_invert：反色
 * - debug_grayscale：灰度
 * - debug_shadow_depth：直显 shadow depth
 * - debug_held_light_glow：手持光源屏幕空间伪动态光
 */
public final class PostFxEffectRegistry {
    public static final String DEBUG_INVERT = "debug_invert";
    public static final String DEBUG_GRAYSCALE = "debug_grayscale";
    public static final String DEBUG_SHADOW_DEPTH = "debug_shadow_depth";
    public static final String DEBUG_HELD_LIGHT_GLOW = "debug_held_light_glow";

    private static final Map<String, PostFxEffectDefinition> EFFECTS = new LinkedHashMap<>();

    static {
        register(
            DEBUG_INVERT,
            new PostFxEffectDefinition(
                Identifier.fromNamespaceAndPath("vulkanpostfx", "debug_invert"),
                Identifier.withDefaultNamespace("invert"),
                "Debug Invert"
            )
        );

        register(
            DEBUG_GRAYSCALE,
            new PostFxEffectDefinition(
                Identifier.fromNamespaceAndPath("vulkanpostfx", "debug_grayscale"),
                Identifier.withDefaultNamespace("invert"),
                "Debug Grayscale"
            )
        );

        register(
            DEBUG_SHADOW_DEPTH,
            new PostFxEffectDefinition(
                Identifier.fromNamespaceAndPath("vulkanpostfx", "debug_shadow_depth"),
                Identifier.withDefaultNamespace("invert"),
                "Debug Shadow Depth"
            )
        );

        register(
            DEBUG_HELD_LIGHT_GLOW,
            new PostFxEffectDefinition(
                Identifier.fromNamespaceAndPath("vulkanpostfx", "debug_held_light_glow"),
                Identifier.withDefaultNamespace("invert"),
                "Debug Held-Light Glow"
            )
        );
    }

    private PostFxEffectRegistry() {
    }

    public static void register(String key, PostFxEffectDefinition definition) {
        EFFECTS.put(key, definition);
    }

    public static PostFxEffectDefinition get(String key) {
        return EFFECTS.get(key);
    }

    public static Collection<PostFxEffectDefinition> all() {
        return EFFECTS.values();
    }
}
