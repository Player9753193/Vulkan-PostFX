package com.ionhex975.vulkanpostfx.client.command;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.pack.ActiveShaderPackManager;
import com.ionhex975.vulkanpostfx.client.pack.ShaderPackContainer;
import com.ionhex975.vulkanpostfx.client.reload.VpfxHotReloadManager;
import com.ionhex975.vulkanpostfx.client.state.PostFxRuntimeState;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Client-side VPFX commands.
 *
 * Commands:
 * - /vpfx reload          Reload the currently configured VPFX pack.
 * - /vpfx reload auto     Switch config to auto, rescan shaderpacks, then reload.
 * - /vpfx reload builtin  Switch config to builtin, then apply vanilla-safe state.
 * - /vpfx list            Rescan shaderpacks and print discovered packs.
 * - /vpfx off             Disable VPFX and return to vanilla rendering.
 */
public final class VpfxClientCommands {
    private VpfxClientCommands() {
    }

    public static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommands.literal("vpfx")
                        .executes(VpfxClientCommands::executeHelp)
                        .then(ClientCommands.literal("reload")
                                .executes(VpfxClientCommands::executeReloadCurrent)
                                .then(ClientCommands.literal("auto")
                                        .executes(VpfxClientCommands::executeReloadAuto))
                                .then(ClientCommands.literal("builtin")
                                        .executes(VpfxClientCommands::executeReloadBuiltin)))
                        .then(ClientCommands.literal("list")
                                .executes(VpfxClientCommands::executeList))
                        .then(ClientCommands.literal("off")
                                .executes(VpfxClientCommands::executeOff))
        ));

        VulkanPostFX.LOGGER.info(
                "[{}] Client command registered: /vpfx reload",
                VulkanPostFX.MOD_ID
        );
    }

    private static int executeHelp(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        send(source, "VPFX commands:");
        send(source, "  /vpfx reload          - reload current configured pack");
        send(source, "  /vpfx reload auto     - auto-select first safe ZIP and reload");
        send(source, "  /vpfx reload builtin  - switch to built-in debug pack");
        send(source, "  /vpfx list            - list discovered VPFX packs");
        send(source, "  /vpfx off             - disable VPFX and return to vanilla");
        return Command.SINGLE_SUCCESS;
    }

    private static int executeReloadCurrent(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        send(source, "VPFX reload started for current config...");

        CompletableFuture<Void> future = VpfxHotReloadManager.hotReloadCurrentPack(
                Minecraft.getInstance(),
                true,
                "client-command:/vpfx reload"
        );

        reportReloadResult(source, future);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeReloadAuto(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        send(source, "VPFX auto-select reload started. New ZIP packs in shaderpacks/ will be considered...");

        CompletableFuture<Void> future = VpfxHotReloadManager.selectAutoAndReload(
                Minecraft.getInstance(),
                "client-command:/vpfx reload auto"
        );

        reportReloadResult(source, future);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeReloadBuiltin(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        send(source, "VPFX switching to built-in debug pack...");

        CompletableFuture<Void> future = VpfxHotReloadManager.selectBuiltinAndReload(
                Minecraft.getInstance(),
                "client-command:/vpfx reload builtin"
        );

        reportReloadResult(source, future);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeList(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        ActiveShaderPackManager.bootstrap();

        ShaderPackContainer active = ActiveShaderPackManager.getActivePack();
        List<ShaderPackContainer> packs = ActiveShaderPackManager.getDiscoveredPacks();

        send(source, "VPFX discovered packs: " + packs.size());
        send(source, "Active: " + describePack(active));

        for (ShaderPackContainer pack : packs) {
            String prefix = ActiveShaderPackManager.isActivePack(pack) ? "* " : "  ";
            send(source, prefix + describePack(pack));
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int executeOff(CommandContext<FabricClientCommandSource> context) {
        PostFxRuntimeState.setDebugEffectEnabled(false);
        PostFxRuntimeState.requestReapply();
        send(context.getSource(), "VPFX disabled. Vanilla rendering requested.");
        VulkanPostFX.LOGGER.info(
                "[{}] VPFX disabled by client command: /vpfx off",
                VulkanPostFX.MOD_ID
        );
        return Command.SINGLE_SUCCESS;
    }

    private static void reportReloadResult(
            FabricClientCommandSource source,
            CompletableFuture<Void> future
    ) {
        future.whenComplete((ignored, throwable) -> Minecraft.getInstance().execute(() -> {
            ShaderPackContainer activePack = ActiveShaderPackManager.getActivePack();

            if (throwable != null) {
                send(source, "VPFX reload failed safely. Vanilla rendering was requested. Check latest.log.");
                VulkanPostFX.LOGGER.error(
                        "[{}] VPFX reload command failed safely; vanilla rendering requested",
                        VulkanPostFX.MOD_ID,
                        throwable
                );
                return;
            }

            send(source, "VPFX reload complete. Active: " + describePack(activePack));
        }));
    }

    private static String describePack(ShaderPackContainer pack) {
        if (pack == null) {
            return "none";
        }

        return pack.manifest().name()
                + " [id=" + pack.manifest().id()
                + ", source=" + pack.sourceId()
                + "]";
    }

    private static void send(FabricClientCommandSource source, String message) {
        source.sendFeedback(Component.literal(message));
    }
}