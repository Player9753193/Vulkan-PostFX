package com.ionhex975.vulkanpostfx.client.reload;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.state.PostFxRuntimeState;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class PostFxReloadHooks {
    private static final Identifier LISTENER_ID =
            Identifier.fromNamespaceAndPath("vulkanpostfx", "postfx_reload_restore");

    private PostFxReloadHooks() {
    }

    public static IdentifiableResourceReloadListener createReloadListener() {
        return new IdentifiableResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return LISTENER_ID;
            }

            @Override
            public CompletableFuture<Void> reload(
                    PreparableReloadListener.SharedState currentReload,
                    Executor taskExecutor,
                    PreparableReloadListener.PreparationBarrier preparationBarrier,
                    Executor reloadExecutor
            ) {
                /*
                 * Do NOT rescan shaderpacks or materialize runtime packs here.
                 *
                 * This listener runs while Minecraft's resource reload is already in progress.
                 * Mutating vulkanpostfx_runtime from here can corrupt the reload transaction.
                 * F7/F10 must prepare RuntimeZipPackState before calling Minecraft#reloadResourcePacks().
                 */
                return CompletableFuture
                        .<Void>completedFuture(null)
                        .thenCompose(preparationBarrier::wait)
                        .thenRunAsync(() -> {
                            try {
                                PostFxRuntimeState.requestReapply();

                                VulkanPostFX.LOGGER.info(
                                        "[{}] Resource reload completed; requested VPFX PostFX state reapply",
                                        VulkanPostFX.MOD_ID
                                );
                            } catch (Throwable t) {
                                VulkanPostFX.LOGGER.error(
                                        "[{}] VPFX post-resource-reload restore failed; keeping Minecraft resource reload alive",
                                        VulkanPostFX.MOD_ID,
                                        t
                                );
                            }
                        }, reloadExecutor);
            }
        };
    }
}