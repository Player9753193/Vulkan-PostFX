package com.ionhex975.vulkanpostfx.client.shadow;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.state.PostFxRuntimeState;
import net.minecraft.client.Minecraft;

/**
 * Gates vanilla circular entity shadows while VPFX owns entity shadowing.
 *
 * <p>This suppresses only the vanilla blob shadow submitted through
 * SubmitNodeCollection.submitShadow(...). It does not touch VPFX entity shadow casters, vanilla
 * entity model rendering, name tags, translucent features, or the global Minecraft
 * options.entityShadows setting.</p>
 */
public final class VanillaEntityShadowSuppressor {
    private static final boolean KEEP_VANILLA_ENTITY_SHADOWS = Boolean.getBoolean(
            "vulkanpostfx.shadow.keepVanillaEntityShadows"
    );

    private static boolean suppressionLogged;

    private VanillaEntityShadowSuppressor() {
    }

    public static boolean shouldSuppress() {
        if (KEEP_VANILLA_ENTITY_SHADOWS) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            return false;
        }

        // If an external pack has failed and VPFX is intentionally staying in vanilla rendering,
        // keep vanilla entity shadows as a safe fallback.
        if (PostFxRuntimeState.isExternalPackMarkedFailed()) {
            return false;
        }

        // Suppress only when the VPFX shadow map is actually active and usable this frame. This
        // keeps vanilla blob shadows available when VPFX is disabled or when the shadow pass could
        // not produce a valid target.
        boolean suppress = WorldShadowUniformBuffer.isWorldShadowEnabled();

        if (suppress && !suppressionLogged) {
            suppressionLogged = true;
            VulkanPostFX.LOGGER.info(
                    "[{}] Vanilla circular entity shadows suppressed while VPFX shadow map is active",
                    VulkanPostFX.MOD_ID
            );
        }

        return suppress;
    }
}
