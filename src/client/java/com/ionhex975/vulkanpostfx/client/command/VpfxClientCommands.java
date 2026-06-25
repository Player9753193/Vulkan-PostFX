package com.ionhex975.vulkanpostfx.client.command;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.diagnostics.doctor.VpfxDoctorFormatter;
import com.ionhex975.vulkanpostfx.client.diagnostics.exporter.VpfxDiagnosticExportResult;
import com.ionhex975.vulkanpostfx.client.diagnostics.exporter.VpfxDiagnosticExportService;
import com.ionhex975.vulkanpostfx.client.diagnostics.doctor.VpfxDoctorReport;
import com.ionhex975.vulkanpostfx.client.diagnostics.doctor.VpfxDoctorService;
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
                        .then(ClientCommands.literal("doctor")
                                .executes(VpfxClientCommands::executeDoctor)
                                .then(ClientCommands.literal("copy")
                                        .executes(VpfxClientCommands::executeDoctorCopy))
                                .then(ClientCommands.literal("log")
                                        .executes(VpfxClientCommands::executeDoctorLog)))
                        .then(ClientCommands.literal("export-diagnostics")
                                .executes(VpfxClientCommands::executeExportDiagnostics))
                        .then(ClientCommands.literal("export")
                                .executes(VpfxClientCommands::executeExportDiagnostics))
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
        sendKey(source, "command.vulkanpostfx.help.title");
        sendKey(source, "command.vulkanpostfx.help.reload");
        sendKey(source, "command.vulkanpostfx.help.reload_auto");
        sendKey(source, "command.vulkanpostfx.help.reload_builtin");
        sendKey(source, "command.vulkanpostfx.help.list");
        sendKey(source, "command.vulkanpostfx.help.doctor");
        sendKey(source, "command.vulkanpostfx.help.export_diagnostics");
        sendKey(source, "command.vulkanpostfx.help.off");
        return Command.SINGLE_SUCCESS;
    }

    private static int executeReloadCurrent(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        sendKey(source, "command.vulkanpostfx.reload.started");

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
        sendKey(source, "command.vulkanpostfx.reload_auto.started");

        CompletableFuture<Void> future = VpfxHotReloadManager.selectAutoAndReload(
                Minecraft.getInstance(),
                "client-command:/vpfx reload auto"
        );

        reportReloadResult(source, future);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeReloadBuiltin(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        sendKey(source, "command.vulkanpostfx.reload_builtin.started");

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

        sendKey(source, "command.vulkanpostfx.list.count", packs.size());
        sendKey(source, "command.vulkanpostfx.list.active", describePack(active));

        for (ShaderPackContainer pack : packs) {
            String prefix = ActiveShaderPackManager.isActivePack(pack) ? "* " : "  ";
            send(source, Component.literal(prefix + describePack(pack)));
        }

        return Command.SINGLE_SUCCESS;
    }


    private static int executeDoctor(CommandContext<FabricClientCommandSource> context) {
        try {
            VpfxDoctorReport report = VpfxDoctorService.createReport();
            send(context.getSource(), VpfxDoctorFormatter.toChatSummary(report));
        } catch (Throwable t) {
            sendKey(context.getSource(), "command.vulkanpostfx.doctor.failed", t.getClass().getSimpleName());
            VulkanPostFX.LOGGER.error("[{}] /vpfx doctor failed", VulkanPostFX.MOD_ID, t);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeDoctorCopy(CommandContext<FabricClientCommandSource> context) {
        try {
            VpfxDoctorReport report = VpfxDoctorService.createReport();
            String text = VpfxDoctorFormatter.toPlainText(report);
            Minecraft.getInstance().keyboardHandler.setClipboard(text);
            sendKey(context.getSource(), "command.vulkanpostfx.doctor.copied");
        } catch (Throwable t) {
            sendKey(context.getSource(), "command.vulkanpostfx.doctor.failed", t.getClass().getSimpleName());
            VulkanPostFX.LOGGER.error("[{}] /vpfx doctor copy failed", VulkanPostFX.MOD_ID, t);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeDoctorLog(CommandContext<FabricClientCommandSource> context) {
        try {
            VpfxDoctorReport report = VpfxDoctorService.createReport();
            VulkanPostFX.LOGGER.info("\n{}", VpfxDoctorFormatter.toPlainText(report));
            sendKey(context.getSource(), "command.vulkanpostfx.doctor.logged");
        } catch (Throwable t) {
            sendKey(context.getSource(), "command.vulkanpostfx.doctor.failed", t.getClass().getSimpleName());
            VulkanPostFX.LOGGER.error("[{}] /vpfx doctor log failed", VulkanPostFX.MOD_ID, t);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeExportDiagnostics(CommandContext<FabricClientCommandSource> context) {
        try {
            VpfxDiagnosticExportResult result = VpfxDiagnosticExportService.exportNow();
            String absolutePath = result.zipPath().toAbsolutePath().toString();
            Minecraft.getInstance().keyboardHandler.setClipboard(absolutePath);
            sendKey(
                    context.getSource(),
                    "command.vulkanpostfx.export_diagnostics.complete",
                    absolutePath,
                    result.filesWritten(),
                    result.skippedFiles().size()
            );
        } catch (Throwable t) {
            sendKey(context.getSource(), "command.vulkanpostfx.export_diagnostics.failed", t.getClass().getSimpleName());
            VulkanPostFX.LOGGER.error("[{}] /vpfx export-diagnostics failed", VulkanPostFX.MOD_ID, t);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeOff(CommandContext<FabricClientCommandSource> context) {
        PostFxRuntimeState.setDebugEffectEnabled(false);
        PostFxRuntimeState.requestReapply();
        sendKey(context.getSource(), "command.vulkanpostfx.off.done");
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
                sendKey(source, "command.vulkanpostfx.reload.failed");
                VulkanPostFX.LOGGER.error(
                        "[{}] VPFX reload command failed safely; vanilla rendering requested",
                        VulkanPostFX.MOD_ID,
                        throwable
                );
                return;
            }

            sendKey(source, "command.vulkanpostfx.reload.complete", describePack(activePack));
        }));
    }

    private static String describePack(ShaderPackContainer pack) {
        if (pack == null) {
            return Component.translatable("vulkanpostfx.common.none").getString();
        }

        return pack.manifest().name()
                + " [id=" + pack.manifest().id()
                + ", source=" + pack.sourceId()
                + "]";
    }

    private static void send(FabricClientCommandSource source, Component message) {
        source.sendFeedback(message);
    }

    private static void sendKey(FabricClientCommandSource source, String key, Object... args) {
        source.sendFeedback(Component.translatable(key, args));
    }
}