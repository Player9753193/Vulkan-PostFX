package com.ionhex975.vulkanpostfx.client.postfx;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;

import java.util.Set;

public final class PostFxExternalTargetIds {
    public static final Identifier SCENE_COLOR =
            Identifier.tryParse("minecraft:scene_color");

    public static final Identifier SCENE_DEPTH =
            Identifier.tryParse(VulkanPostFX.MOD_ID + ":scene_depth");

    public static final Identifier SHADOW_DEPTH =
            Identifier.tryParse(VulkanPostFX.MOD_ID + ":shadow_depth");

    /**
     * 当前对外允许的 external targets。
     *
     * VPFX v1 alpha:
     * - minecraft:main                     (builtin — always available)
     * - minecraft:scene_color              (alias for main target color)
     * - minecraft:scene_depth              (scene depth alias)
     * - minecraft:shadow_depth             (shadow depth alias)
     * - vulkanpostfx:scene_depth           (VPFX native scene depth target)
     * - vulkanpostfx:shadow_depth          (VPFX native shadow depth target)
     */
    private static final Set<Identifier> ALLOWED = Set.of(
            PostChain.MAIN_TARGET_ID,
            Identifier.tryParse("minecraft:scene_color"),
            Identifier.tryParse("minecraft:scene_depth"),
            Identifier.tryParse("minecraft:shadow_depth"),
            SCENE_DEPTH,
            SHADOW_DEPTH
    );

    private PostFxExternalTargetIds() {
    }

    public static Set<Identifier> allowedTargets() {
        return ALLOWED;
    }
}
