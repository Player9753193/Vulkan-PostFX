package com.ionhex975.vulkanpostfx.client.gui;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.pack.ActiveShaderPackManager;
import com.ionhex975.vulkanpostfx.client.reload.VpfxHotReloadManager;
import com.ionhex975.vulkanpostfx.client.state.PostFxRuntimeState;
import com.ionhex975.vulkanpostfx.client.ui.model.VpfxPackListEntry;
import com.ionhex975.vulkanpostfx.client.ui.model.VpfxUiState;
import com.ionhex975.vulkanpostfx.client.ui.service.VpfxUiService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Reese-style VPFX settings screen.
 *
 * Visual goals:
 * - centered 16:9-bounded panel
 * - search-style top bar
 * - horizontal tabs
 * - dark translucent content frame
 * - flat option rows and compact bottom buttons
 */
public final class VpfxShaderPackSelectionScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int SETTING_ROW_MIN_HEIGHT = 38;
    private static final int PACK_ROW_MIN_HEIGHT = 40;
    private static final int DESCRIPTION_LINE_HEIGHT = 9;
    private static final int TAB_HEIGHT = 24;

    // Footer 按钮外边距：按钮上边距 = 下边距 = Done 右边距
    private static final int FOOTER_INSET = 8;
    private static final int FOOTER_HEIGHT = ROW_HEIGHT + FOOTER_INSET * 2;
    private static final int PACKS_PER_PAGE = 5;

    private static final int BG_PANEL = 0xD8101014;
    private static final int BG_HEADER = 0xE0000000;
    private static final int BG_FRAME = 0xAA000000;
    private static final int BG_ROW = 0x381E2026;
    private static final int BG_ROW_HOVER = 0x66313640;
    private static final int BG_ROW_ACTIVE = 0x66364F68;
    private static final int BG_INPUT = 0x90000000;
    private static final int BORDER_SOFT = 0x225A8DAA;

    private static final int TEXT_PRIMARY = 0xFFEDEDF0;
    private static final int TEXT_SECONDARY = 0xFFB7BAC4;
    private static final int TEXT_MUTED = 0xFF808890;
    private static final int TEXT_ACCENT = 0xFF66CCFF;
    private static final int TEXT_SUCCESS = 0xFF62D394;
    private static final int TEXT_WARN = 0xFFFFB454;
    private static final int TEXT_ERROR = 0xFFFF6B6B;
    private static final int TEXT_HOVER = 0xFFFFFFFF;

    private final Screen parent;
    private final List<ClickZone> clickZones = new ArrayList<>();

    private UiPage currentPage = UiPage.PACKS;
    private int packPage;
    private int packListScroll;
    private int packListTop;
    private int packListBottom;
    private int packListViewportHeight;
    private int packListContentHeight;
    private boolean reloadInProgress;
    private Component statusMessage;

    public VpfxShaderPackSelectionScreen(Screen parent) {
        this(parent, Component.translatable("screen.vulkanpostfx.shaderpacks.status.ready"));
    }

    private VpfxShaderPackSelectionScreen(Screen parent, Component statusMessage) {
        super(Component.translatable("screen.vulkanpostfx.shaderpacks.title"));
        this.parent = parent;
        this.statusMessage = statusMessage;
    }

    @Override
    protected void init() {
        ActiveShaderPackManager.bootstrap();
        VpfxUiService.get().refreshRegistry();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        clickZones.clear();

        VpfxUiState state = VpfxUiService.get().snapshot();
        Layout layout = layout();

        drawChrome(graphics, layout, state);
        drawTabs(graphics, layout, mouseX, mouseY);

        switch (currentPage) {
            case PACKS -> drawPacksPage(graphics, layout, state, mouseX, mouseY);
            case GENERAL -> drawGeneralPage(graphics, layout, state, mouseX, mouseY);
            case BACKEND -> drawBackendPage(graphics, layout, state, mouseX, mouseY);
            case DEBUG -> drawDebugPage(graphics, layout, state, mouseX, mouseY);
            case DEVELOPER -> drawDeveloperPage(graphics, layout, state, mouseX, mouseY);
            case ABOUT -> drawAboutPage(graphics, layout, mouseX, mouseY);
        }

        drawFooter(graphics, layout, mouseX, mouseY);
        drawTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) {
            return super.mouseClicked(event, doubleClick);
        }

        double mouseX = event.x();
        double mouseY = event.y();

        for (ClickZone zone : clickZones) {
            if (zone.contains(mouseX, mouseY)) {
                if (zone.enabled) {
                    zone.action.run();
                }
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (currentPage == UiPage.PACKS && packListContentHeight > packListViewportHeight) {
            Layout layout = layout();
            int top = packListTop > 0 ? packListTop : layout.bodyY;
            int bottom = packListBottom > top ? packListBottom : layout.frameY + layout.frameHeight - 8;
            if (contains(mouseX, mouseY, layout.bodyX, top, layout.bodyWidth, bottom - top)) {
                int maxScroll = Math.max(0, packListContentHeight - packListViewportHeight);
                int delta = (int) Math.round(-scrollY * 28.0D);
                packListScroll = clamp(packListScroll + delta, 0, maxScroll);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        VpfxScreenBridge.setScreen(parent);
    }

    private void drawChrome(GuiGraphicsExtractor graphics, Layout layout, VpfxUiState state) {
        fill(graphics, layout.outerX, layout.outerY, layout.outerX + layout.outerWidth, layout.outerY + layout.outerHeight, BG_PANEL);
        border(graphics, layout.outerX, layout.outerY, layout.outerWidth, layout.outerHeight, BORDER_SOFT);

        fill(graphics, layout.searchX, layout.searchY, layout.searchX + layout.searchWidth, layout.searchY + 20, BG_INPUT);
        border(graphics, layout.searchX, layout.searchY, layout.searchWidth, 20, BORDER_SOFT);
        text(graphics, tr("screen.vulkanpostfx.shaderpacks.search_placeholder"), layout.searchX + 7, layout.searchY + 6, TEXT_MUTED);

        text(graphics, tr("screen.vulkanpostfx.shaderpacks.brand"), layout.contentX, layout.titleY, TEXT_PRIMARY);
        text(graphics, tr("screen.vulkanpostfx.shaderpacks.settings_suffix"), layout.contentX + this.font.width(tr("screen.vulkanpostfx.shaderpacks.brand")) + 7, layout.titleY, TEXT_ACCENT);

        String compactState = onOff(state.vpfxEnabled());
        int compactColor = state.failedEffectId().isBlank() ? (state.vpfxEnabled() ? TEXT_SUCCESS : TEXT_MUTED) : TEXT_ERROR;
        String backend = state.nativeDirect() ? tr("vulkanpostfx.backend.kind.native") : state.postChainRuntime() ? tr("vulkanpostfx.backend.kind.postchain") : tr("vulkanpostfx.backend.kind.vanilla");
        String status = reloadInProgress ? tr("screen.vulkanpostfx.shaderpacks.status.reloading") : compactState + " · " + backend + " · " + state.activePackId();
        textRight(graphics, status, layout.contentX + layout.contentWidth, layout.titleY, compactColor);

        fill(graphics, layout.frameX, layout.frameY, layout.frameX + layout.frameWidth, layout.frameY + layout.frameHeight, BG_FRAME);
        border(graphics, layout.frameX, layout.frameY, layout.frameWidth, layout.frameHeight, BORDER_SOFT);
        fill(graphics, layout.frameX, layout.frameY, layout.frameX + layout.frameWidth, layout.frameY + TAB_HEIGHT, BG_HEADER);
        fill(graphics, layout.frameX, layout.frameY + TAB_HEIGHT - 1, layout.frameX + layout.frameWidth, layout.frameY + TAB_HEIGHT, 0xAA345E7D);

        String stateText = reloadInProgress ? tr("screen.vulkanpostfx.shaderpacks.status.reload_in_progress") : textOf(statusMessage);
        text(graphics, stateText, layout.frameX + 8, layout.frameY + layout.frameHeight + 6, reloadInProgress ? TEXT_WARN : TEXT_SECONDARY);
    }

    private void drawTabs(GuiGraphicsExtractor graphics, Layout layout, int mouseX, int mouseY) {
        int tabCount = UiPage.values().length;
        int tabWidth = Math.max(72, layout.frameWidth / tabCount);
        int x = layout.frameX;

        for (UiPage page : UiPage.values()) {
            int w = page == UiPage.ABOUT ? layout.frameX + layout.frameWidth - x : tabWidth;
            boolean active = currentPage == page;
            boolean hovered = contains(mouseX, mouseY, x, layout.frameY, w, TAB_HEIGHT);
            int bg = active ? 0x66364F68 : hovered ? 0x44313640 : 0x00000000;
            fill(graphics, x, layout.frameY, x + w, layout.frameY + TAB_HEIGHT, bg);
            if (active) {
                fill(graphics, x, layout.frameY + TAB_HEIGHT - 2, x + w, layout.frameY + TAB_HEIGHT, TEXT_ACCENT);
            }
            int color = active ? TEXT_ACCENT : hovered ? TEXT_HOVER : TEXT_SECONDARY;
            textCentered(graphics, tr(page.labelKey), x, layout.frameY + 8, w, color);
            int zoneX = x;
            clickZones.add(new ClickZone(zoneX, layout.frameY, w, TAB_HEIGHT, true, () -> {
                if (currentPage != page) {
                    currentPage = page;
                    packListScroll = 0;
                }
            }, tr(page.descriptionKey)));
            x += w;
        }
    }

    private void drawPacksPage(GuiGraphicsExtractor graphics, Layout layout, VpfxUiState state, int mouseX, int mouseY) {
        int x = layout.bodyX;
        int y = layout.bodyY;
        int width = layout.bodyWidth;

        y = section(graphics, x, y, tr("screen.vulkanpostfx.shaderpacks.section.active_pack"));
        y = infoRow(graphics, x, y, width, tr("screen.vulkanpostfx.shaderpacks.label.current"), state.activePackName() + " [" + state.activePackId() + "]", state.failedEffectId().isBlank() ? TEXT_SUCCESS : TEXT_ERROR);
        y = infoRow(graphics, x, y, width, tr("screen.vulkanpostfx.shaderpacks.label.runtime"), backendSummary(state), state.nativeDirect() ? TEXT_SUCCESS : TEXT_SECONDARY);
        y += 6;

        int actionW = (width - 16) / 3;
        actionButton(graphics, x, y, actionW, tr("screen.vulkanpostfx.shaderpacks.reload"), !reloadInProgress, mouseX, mouseY,
                () -> beginReload(VpfxHotReloadManager.hotReloadCurrentPack(Minecraft.getInstance(), true, "settings:reload-current"),
                        trc("screen.vulkanpostfx.shaderpacks.status.reload_started"), trc("screen.vulkanpostfx.shaderpacks.status.reload_done")),
                tr("screen.vulkanpostfx.shaderpacks.tooltip.reload_current"));
        actionButton(graphics, x + actionW + 8, y, actionW, tr("screen.vulkanpostfx.shaderpacks.auto_short"), !reloadInProgress, mouseX, mouseY,
                () -> beginReload(VpfxHotReloadManager.selectAutoAndReload(Minecraft.getInstance(), "settings:select-auto"),
                        trc("screen.vulkanpostfx.shaderpacks.status.auto_started"), trc("screen.vulkanpostfx.shaderpacks.status.auto_done")),
                tr("screen.vulkanpostfx.shaderpacks.tooltip.auto"));
        actionButton(graphics, x + (actionW + 8) * 2, y, actionW, tr("screen.vulkanpostfx.shaderpacks.builtin_short"), !reloadInProgress, mouseX, mouseY,
                () -> beginReload(VpfxHotReloadManager.selectBuiltinAndReload(Minecraft.getInstance(), "settings:select-builtin"),
                        trc("screen.vulkanpostfx.shaderpacks.status.builtin_started"), trc("screen.vulkanpostfx.shaderpacks.status.builtin_done")),
                tr("screen.vulkanpostfx.shaderpacks.tooltip.builtin"));
        y += ROW_HEIGHT + 10;

        y = section(graphics, x, y, tr("screen.vulkanpostfx.shaderpacks.section.available_packs"));
        List<VpfxPackListEntry> packs = state.packs();

        int listTop = y;
        int listBottom = layout.frameY + layout.frameHeight - 8;
        int listHeight = Math.max(40, listBottom - listTop);
        packListTop = listTop;
        packListBottom = listBottom;
        packListViewportHeight = listHeight;
        packListContentHeight = calculatePackListContentHeight(packs, width);
        packListScroll = clamp(packListScroll, 0, Math.max(0, packListContentHeight - packListViewportHeight));

        if (packListContentHeight > packListViewportHeight) {
            textRight(graphics, tr("screen.vulkanpostfx.shaderpacks.scroll", packListScroll + 1, Math.max(1, packListContentHeight - packListViewportHeight + 1)), x + width, listTop - 12, TEXT_MUTED);
        }

        graphics.enableScissor(x, listTop, x + width, listBottom);
        int rowY = listTop - packListScroll;
        if (packs.isEmpty()) {
            plainRow(graphics, x, rowY, width, tr("screen.vulkanpostfx.shaderpacks.no_packs.title"), tr("screen.vulkanpostfx.shaderpacks.no_packs.body"), TEXT_MUTED, mouseX, mouseY, null, tr("screen.vulkanpostfx.shaderpacks.no_packs.tooltip"));
        } else {
            for (VpfxPackListEntry pack : packs) {
                int rowHeight = packRowHeight(pack, width);
                if (rowY + rowHeight >= listTop && rowY <= listBottom) {
                    packRow(graphics, x, rowY, width, pack, mouseX, mouseY, listTop, listBottom);
                }
                rowY += rowHeight + 4;
            }
        }
        graphics.disableScissor();

        drawPackListScrollbar(graphics, x + width - 4, listTop, 3, listHeight);
    }

    private void drawGeneralPage(GuiGraphicsExtractor graphics, Layout layout, VpfxUiState state, int mouseX, int mouseY) {
        int x = layout.bodyX;
        int y = layout.bodyY;
        int width = layout.bodyWidth;
        y = section(graphics, x, y, tr("category.vulkanpostfx.general"));
        y = settingRow(graphics, x, y, width, tr("screen.vulkanpostfx.shaderpacks.setting.enable_vpfx"), onOff(state.vpfxEnabled()), state.vpfxEnabled() ? TEXT_SUCCESS : TEXT_MUTED,
                mouseX, mouseY, () -> {
                    boolean enabled = PostFxRuntimeState.toggleDebugEffectEnabled();
                    PostFxRuntimeState.requestReapply();
                    statusMessage = trc(enabled ? "screen.vulkanpostfx.shaderpacks.status.vpfx_enabled" : "screen.vulkanpostfx.shaderpacks.status.vpfx_disabled");
                }, tr("screen.vulkanpostfx.shaderpacks.tooltip.enable_vpfx"));
        y = settingRow(graphics, x, y, width, tr("screen.vulkanpostfx.shaderpacks.setting.shadow_depth_debug"), onOff(state.shadowDepthDebug()), state.shadowDepthDebug() ? TEXT_WARN : TEXT_MUTED,
                mouseX, mouseY, () -> {
                    boolean enabled = PostFxRuntimeState.toggleShadowDepthDebugView();
                    PostFxRuntimeState.requestReapply();
                    statusMessage = trc(enabled ? "screen.vulkanpostfx.shaderpacks.status.shadow_debug_enabled" : "screen.vulkanpostfx.shaderpacks.status.shadow_debug_disabled");
                }, tr("screen.vulkanpostfx.shaderpacks.tooltip.shadow_depth_debug"));
        y = settingRow(graphics, x, y, width, tr("screen.vulkanpostfx.shaderpacks.setting.current_effect"), state.effectId(), TEXT_SECONDARY, mouseX, mouseY, null, tr("screen.vulkanpostfx.shaderpacks.tooltip.current_effect"));
        y = settingRow(graphics, x, y, width, tr("screen.vulkanpostfx.shaderpacks.setting.config_mode"), state.configMode(), TEXT_SECONDARY, mouseX, mouseY, null, tr("screen.vulkanpostfx.shaderpacks.tooltip.config_mode"));
    }

    private void drawBackendPage(GuiGraphicsExtractor graphics, Layout layout, VpfxUiState state, int mouseX, int mouseY) {
        int x = layout.bodyX;
        int y = layout.bodyY;
        int width = layout.bodyWidth;
        y = section(graphics, x, y, tr("category.vulkanpostfx.backend"));
        y = settingRow(graphics, x, y, width, tr("screen.vulkanpostfx.shaderpacks.setting.backend"), state.backendId(), state.nativeDirect() ? TEXT_SUCCESS : TEXT_SECONDARY, mouseX, mouseY, null, tr("screen.vulkanpostfx.shaderpacks.tooltip.backend"));
        y = settingRow(graphics, x, y, width, tr("screen.vulkanpostfx.shaderpacks.setting.display_name"), state.backendDisplayName(), TEXT_SECONDARY, mouseX, mouseY, null, tr("screen.vulkanpostfx.shaderpacks.tooltip.display_name"));
        y = settingRow(graphics, x, y, width, tr("vulkanpostfx.backend.native"), yesNo(state.nativeDirect()), state.nativeDirect() ? TEXT_SUCCESS : TEXT_MUTED, mouseX, mouseY, null, tr("screen.vulkanpostfx.shaderpacks.tooltip.native_direct"));
        y = settingRow(graphics, x, y, width, tr("vulkanpostfx.backend.postchain"), yesNo(state.postChainRuntime()), state.postChainRuntime() ? TEXT_WARN : TEXT_MUTED, mouseX, mouseY, null, tr("screen.vulkanpostfx.shaderpacks.tooltip.postchain_runtime"));
        y = settingRow(graphics, x, y, width, tr("screen.vulkanpostfx.shaderpacks.setting.passes_targets"), state.passCount() + " / " + state.targetCount(), TEXT_SECONDARY, mouseX, mouseY, null, tr("screen.vulkanpostfx.shaderpacks.tooltip.passes_targets"));
        y = settingRow(graphics, x, y, width, tr("screen.vulkanpostfx.shaderpacks.setting.fallback_reason"), state.fallbackReason(), state.fallbackReason().equals("none") ? TEXT_MUTED : TEXT_WARN, mouseX, mouseY, null, tr("screen.vulkanpostfx.shaderpacks.tooltip.fallback_reason"));
    }

    private void drawDebugPage(GuiGraphicsExtractor graphics, Layout layout, VpfxUiState state, int mouseX, int mouseY) {
        int x = layout.bodyX;
        int y = layout.bodyY;
        int width = layout.bodyWidth;
        y = section(graphics, x, y, tr("category.vulkanpostfx.debug"));
        y = settingRow(graphics, x, y, width, tr("screen.vulkanpostfx.shaderpacks.setting.status_hud"), onOff(PostFxRuntimeState.isDebugHudVisible()), PostFxRuntimeState.isDebugHudVisible() ? TEXT_SUCCESS : TEXT_MUTED,
                mouseX, mouseY, () -> {
                    boolean enabled = PostFxRuntimeState.toggleDebugHudVisible();
                    statusMessage = trc(enabled ? "screen.vulkanpostfx.shaderpacks.status.hud_shown" : "screen.vulkanpostfx.shaderpacks.status.hud_hidden");
                }, tr("screen.vulkanpostfx.shaderpacks.tooltip.status_hud"));
        y = settingRow(graphics, x, y, width, tr("screen.vulkanpostfx.shaderpacks.setting.failed_effect"), state.failedEffectId().isBlank() ? tr("vulkanpostfx.common.none") : state.failedEffectId(), state.failedEffectId().isBlank() ? TEXT_MUTED : TEXT_ERROR, mouseX, mouseY, null, tr("screen.vulkanpostfx.shaderpacks.tooltip.failed_effect"));
        y = settingRow(graphics, x, y, width, tr("screen.vulkanpostfx.shaderpacks.setting.runtime_namespace"), emptyAsNone(state.runtimeNamespace()), TEXT_SECONDARY, mouseX, mouseY, null, tr("screen.vulkanpostfx.shaderpacks.tooltip.runtime_namespace"));
        y = settingRow(graphics, x, y, width, tr("screen.vulkanpostfx.shaderpacks.setting.runtime_root"), shortPath(state.runtimeRoot()), TEXT_MUTED, mouseX, mouseY, null, tr("screen.vulkanpostfx.shaderpacks.tooltip.runtime_root"));
        y += 8;
        actionButton(graphics, x, y, width, tr("screen.vulkanpostfx.shaderpacks.action.clear_failed"), !reloadInProgress, mouseX, mouseY,
                () -> beginReload(VpfxHotReloadManager.hotReloadCurrentPack(Minecraft.getInstance(), true, "settings:clear-failed-reload"),
                        trc("screen.vulkanpostfx.shaderpacks.status.clear_failed_reload_started"), trc("screen.vulkanpostfx.shaderpacks.status.reload_completed")),
                tr("screen.vulkanpostfx.shaderpacks.tooltip.clear_failed"));
    }

    private void drawDeveloperPage(GuiGraphicsExtractor graphics, Layout layout, VpfxUiState state, int mouseX, int mouseY) {
        int x = layout.bodyX;
        int y = layout.bodyY;
        int width = layout.bodyWidth;
        y = section(graphics, x, y, tr("screen.vulkanpostfx.shaderpacks.section.developer_snapshot"));
        y = infoRow(graphics, x, y, width, tr("screen.vulkanpostfx.shaderpacks.label.pack_id"), state.activePackId(), TEXT_SECONDARY);
        y = infoRow(graphics, x, y, width, tr("screen.vulkanpostfx.shaderpacks.label.pack_source"), state.activePackSource(), TEXT_SECONDARY);
        y = infoRow(graphics, x, y, width, tr("screen.vulkanpostfx.shaderpacks.label.backend_id"), state.backendId(), TEXT_SECONDARY);
        y = infoRow(graphics, x, y, width, tr("screen.vulkanpostfx.shaderpacks.label.effect"), state.effectId(), TEXT_SECONDARY);
        y = infoRow(graphics, x, y, width, tr("screen.vulkanpostfx.shaderpacks.label.config"), state.configMode(), TEXT_SECONDARY);
        y = infoRow(graphics, x, y, width, tr("screen.vulkanpostfx.shaderpacks.label.pass_count"), String.valueOf(state.passCount()), TEXT_SECONDARY);
        y = infoRow(graphics, x, y, width, tr("screen.vulkanpostfx.shaderpacks.label.target_count"), String.valueOf(state.targetCount()), TEXT_SECONDARY);
    }

    private void drawAboutPage(GuiGraphicsExtractor graphics, Layout layout, int mouseX, int mouseY) {
        int x = layout.bodyX;
        int y = layout.bodyY;
        int width = layout.bodyWidth;
        y = section(graphics, x, y, tr("screen.vulkanpostfx.shaderpacks.section.about"));
        y = plainRow(graphics, x, y, width, tr("screen.vulkanpostfx.shaderpacks.brand"), tr("screen.vulkanpostfx.shaderpacks.about.description"), TEXT_ACCENT, mouseX, mouseY, null, tr("screen.vulkanpostfx.shaderpacks.about.not_iris"));
        y = plainRow(graphics, x, y, width, "F7", tr("screen.vulkanpostfx.shaderpacks.about.f7"), TEXT_SECONDARY, mouseX, mouseY, null, tr("screen.vulkanpostfx.shaderpacks.about.f7.tooltip"));
        y = plainRow(graphics, x, y, width, "F8", tr("screen.vulkanpostfx.shaderpacks.about.f8"), TEXT_SECONDARY, mouseX, mouseY, null, tr("screen.vulkanpostfx.shaderpacks.about.f8.tooltip"));
        y = plainRow(graphics, x, y, width, "F9", tr("screen.vulkanpostfx.shaderpacks.about.f9"), TEXT_SECONDARY, mouseX, mouseY, null, tr("screen.vulkanpostfx.shaderpacks.about.f9.tooltip"));
        y += 8;
        Path shaderPackDirectory = ActiveShaderPackManager.getShaderPackDirectory();
        y = plainRow(graphics, x, y, width, "HUD", tr("screen.vulkanpostfx.shaderpacks.about.hud"), TEXT_SECONDARY, mouseX, mouseY, null, tr("screen.vulkanpostfx.shaderpacks.about.hud.tooltip"));
        plainRow(graphics, x, y, width, "shaderpacks/", String.valueOf(shaderPackDirectory), TEXT_MUTED, mouseX, mouseY, null, tr("screen.vulkanpostfx.shaderpacks.about.shaderpacks.tooltip"));
    }

    private void drawFooter(GuiGraphicsExtractor graphics, Layout layout, int mouseX, int mouseY) {
        int buttonW = 68;
        int buttonGap = 8;

        int footerTop = layout.outerY + layout.outerHeight - FOOTER_HEIGHT;

        // 核心：上边距 = FOOTER_INSET，下边距 = FOOTER_INSET
        int buttonY = footerTop + FOOTER_INSET;

        // 核心：Done 右侧边距 = FOOTER_INSET
        int doneX = layout.outerX + layout.outerWidth - buttonW - FOOTER_INSET;
        int reloadX = doneX - buttonW - buttonGap;
        int autoX = reloadX - buttonW - buttonGap;

        actionButton(
                graphics,
                autoX,
                buttonY,
                buttonW,
                tr("screen.vulkanpostfx.shaderpacks.auto_short"),
                !reloadInProgress,
                mouseX,
                mouseY,
                () -> beginReload(
                        VpfxHotReloadManager.selectAutoAndReload(
                                Minecraft.getInstance(),
                                "settings:footer-auto"
                        ),
                        trc("screen.vulkanpostfx.shaderpacks.status.auto_started"),
                        trc("screen.vulkanpostfx.shaderpacks.status.auto_done")
                ),
                tr("screen.vulkanpostfx.shaderpacks.tooltip.auto")
        );

        actionButton(
                graphics,
                reloadX,
                buttonY,
                buttonW,
                tr("screen.vulkanpostfx.shaderpacks.reload"),
                !reloadInProgress,
                mouseX,
                mouseY,
                () -> beginReload(
                        VpfxHotReloadManager.hotReloadCurrentPack(
                                Minecraft.getInstance(),
                                true,
                                "settings:footer-reload"
                        ),
                        trc("screen.vulkanpostfx.shaderpacks.status.reload_started"),
                        trc("screen.vulkanpostfx.shaderpacks.status.reload_completed")
                ),
                tr("screen.vulkanpostfx.shaderpacks.tooltip.reload_current")
        );

        actionButton(
                graphics,
                doneX,
                buttonY,
                buttonW,
                tr("screen.vulkanpostfx.shaderpacks.done"),
                true,
                mouseX,
                mouseY,
                this::onClose,
                tr("screen.vulkanpostfx.shaderpacks.tooltip.done")
        );
    }

    private int section(GuiGraphicsExtractor graphics, int x, int y, String title) {
        text(graphics, title, x, y, TEXT_ACCENT);
        return y + 15;
    }

    private int calculatePackListContentHeight(List<VpfxPackListEntry> packs, int width) {
        if (packs.isEmpty()) {
            return SETTING_ROW_MIN_HEIGHT + 4;
        }
        int height = 0;
        for (VpfxPackListEntry pack : packs) {
            height += packRowHeight(pack, width) + 4;
        }
        return Math.max(0, height - 4);
    }

    private int packRowHeight(VpfxPackListEntry pack, int width) {
        String description = packDescription(pack);
        int descriptionWidth = Math.max(120, width - 104);
        List<String> descriptionLines = wrapText(description, descriptionWidth);
        return Math.max(PACK_ROW_MIN_HEIGHT, 24 + descriptionLines.size() * DESCRIPTION_LINE_HEIGHT);
    }

    private void drawPackListScrollbar(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        int maxScroll = Math.max(0, packListContentHeight - packListViewportHeight);
        if (maxScroll <= 0 || height <= 8) {
            return;
        }

        fill(graphics, x, y, x + width, y + height, 0x44000000);
        int thumbHeight = Math.max(18, height * packListViewportHeight / Math.max(packListContentHeight, 1));
        int travel = Math.max(1, height - thumbHeight);
        int thumbY = y + (int) Math.round((double) packListScroll / (double) maxScroll * travel);
        fill(graphics, x, thumbY, x + width, thumbY + thumbHeight, 0xAA66CCFF);
    }

    private int packRow(GuiGraphicsExtractor graphics, int x, int y, int width, VpfxPackListEntry pack, int mouseX, int mouseY) {
        return packRow(graphics, x, y, width, pack, mouseX, mouseY, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    private int packRow(GuiGraphicsExtractor graphics, int x, int y, int width, VpfxPackListEntry pack, int mouseX, int mouseY, int clipTop, int clipBottom) {
        boolean active = pack.selected();
        boolean invalid = pack.invalid();
        String description = packDescription(pack);
        int descriptionWidth = Math.max(120, width - 104);
        List<String> descriptionLines = wrapText(description, descriptionWidth);
        int rowHeight = Math.max(PACK_ROW_MIN_HEIGHT, 24 + descriptionLines.size() * DESCRIPTION_LINE_HEIGHT);

        int zoneY = Math.max(y, clipTop);
        int zoneBottom = Math.min(y + rowHeight, clipBottom);
        boolean hovered = zoneBottom > zoneY && contains(mouseX, mouseY, x, zoneY, width, zoneBottom - zoneY);
        int bg = active ? BG_ROW_ACTIVE : hovered ? BG_ROW_HOVER : BG_ROW;
        fill(graphics, x, y, x + width, y + rowHeight, bg);
        if (active) {
            fill(graphics, x, y, x + 2, y + rowHeight, TEXT_ACCENT);
        } else if (invalid) {
            fill(graphics, x, y, x + 2, y + rowHeight, TEXT_ERROR);
        } else if (pack.warning()) {
            fill(graphics, x, y, x + 2, y + rowHeight, TEXT_WARN);
        }

        String name = fitText(pack.name(), Math.max(60, width - 190));
        int nameColor = invalid ? TEXT_ERROR : active ? TEXT_HOVER : pack.warning() ? TEXT_WARN : TEXT_PRIMARY;
        text(graphics, name, x + 7, y + 6, nameColor);

        String id = "[" + pack.id() + "]";
        int idX = x + 7 + this.font.width(name) + 8;
        int badgeLeft = x + width - 88;
        if (idX + this.font.width(id) < badgeLeft - 8) {
            text(graphics, id, idX, y + 6, invalid ? TEXT_ERROR : TEXT_MUTED);
        }

        String backend = packBadge(pack);
        int badgeColor = packBadgeColor(pack);
        badge(graphics, x + width - 82, y + 4, 74, backend, badgeColor);

        int descY = y + 20;
        int descColor = invalid ? TEXT_ERROR : pack.warning() ? TEXT_WARN : TEXT_SECONDARY;
        for (String line : descriptionLines) {
            text(graphics, line, x + 7, descY, descColor);
            descY += DESCRIPTION_LINE_HEIGHT;
        }

        String tooltip = packTooltip(pack);
        Runnable rowAction = () -> {
            if (!pack.canActivate()) {
                copyPackDiagnostics(pack);
                return;
            }

            CompletableFuture<Void> future = "builtin".equals(pack.source())
                    ? VpfxHotReloadManager.selectBuiltinAndReload(Minecraft.getInstance(), "settings:select-builtin-row")
                    : VpfxHotReloadManager.selectExternalAndReload(Minecraft.getInstance(), pack.id(), "settings:select:" + pack.id());
            beginReload(
                    future,
                    trc("screen.vulkanpostfx.shaderpacks.status.loading_pack", pack.name()),
                    trc("screen.vulkanpostfx.shaderpacks.status.loaded_pack", pack.name())
            );
        };
        if (zoneBottom > zoneY) {
            clickZones.add(new ClickZone(x, zoneY, width, zoneBottom - zoneY, !reloadInProgress, rowAction, tooltip));
        }
        return y + rowHeight + 4;
    }


    private int settingRow(GuiGraphicsExtractor graphics, int x, int y, int width, String label, String value, int valueColor, int mouseX, int mouseY, Runnable action, String description) {
        boolean enabled = action != null;
        int valueWidth = Math.max(90, width / 3);
        int descriptionWidth = Math.max(120, width - valueWidth - 26);
        List<String> descriptionLines = wrapText(description, descriptionWidth);
        int rowHeight = Math.max(SETTING_ROW_MIN_HEIGHT, 24 + descriptionLines.size() * DESCRIPTION_LINE_HEIGHT);
        boolean hovered = enabled && contains(mouseX, mouseY, x, y, width, rowHeight);

        fill(graphics, x, y, x + width, y + rowHeight, hovered ? BG_ROW_HOVER : BG_ROW);
        text(graphics, label, x + 7, y + 6, TEXT_PRIMARY);
        textRight(graphics, fitText(value, valueWidth), x + width - 8, y + 6, valueColor);

        int descY = y + 20;
        for (String line : descriptionLines) {
            text(graphics, line, x + 7, descY, TEXT_SECONDARY);
            descY += DESCRIPTION_LINE_HEIGHT;
        }

        if (enabled) {
            clickZones.add(new ClickZone(x, y, width, rowHeight, true, action, description));
        } else if (description != null) {
            clickZones.add(new ClickZone(x, y, width, rowHeight, false, () -> {}, description));
        }
        return y + rowHeight + 4;
    }

    private int infoRow(GuiGraphicsExtractor graphics, int x, int y, int width, String label, String value, int valueColor) {
        int valueWidth = Math.max(100, width / 2);
        int rowHeight = SETTING_ROW_MIN_HEIGHT;
        fill(graphics, x, y, x + width, y + rowHeight, BG_ROW);
        text(graphics, label, x + 7, y + 6, TEXT_MUTED);
        textRight(graphics, fitText(value, valueWidth), x + width - 8, y + 6, valueColor);
        String description = value == null ? "" : value;
        for (String line : wrapText(description, Math.max(120, width - 14))) {
            text(graphics, line, x + 7, y + 20, TEXT_SECONDARY);
            break;
        }
        return y + rowHeight + 4;
    }

    private int plainRow(GuiGraphicsExtractor graphics, int x, int y, int width, String title, String value, int titleColor, int mouseX, int mouseY, Runnable action, String description) {
        boolean enabled = action != null;
        int descriptionWidth = Math.max(120, width - 14);
        String visibleDescription = value == null || value.isBlank() ? description : value;
        List<String> descriptionLines = wrapText(visibleDescription, descriptionWidth);
        int rowHeight = Math.max(SETTING_ROW_MIN_HEIGHT, 24 + descriptionLines.size() * DESCRIPTION_LINE_HEIGHT);
        boolean hovered = contains(mouseX, mouseY, x, y, width, rowHeight);
        fill(graphics, x, y, x + width, y + rowHeight, hovered && enabled ? BG_ROW_HOVER : BG_ROW);
        text(graphics, title, x + 7, y + 6, titleColor);
        int descY = y + 20;
        for (String line : descriptionLines) {
            text(graphics, line, x + 7, descY, TEXT_SECONDARY);
            descY += DESCRIPTION_LINE_HEIGHT;
        }
        clickZones.add(new ClickZone(x, y, width, rowHeight, enabled, enabled ? action : () -> {}, description));
        return y + rowHeight + 4;
    }

    private void actionButton(GuiGraphicsExtractor graphics, int x, int y, int width, String label, boolean enabled, int mouseX, int mouseY, Runnable action, String tooltip) {
        boolean hovered = enabled && contains(mouseX, mouseY, x, y, width, ROW_HEIGHT);
        fill(graphics, x, y, x + width, y + ROW_HEIGHT, enabled ? (hovered ? 0xAA263A48 : 0x8820272F) : 0x44191A1F);
        border(graphics, x, y, width, ROW_HEIGHT, hovered ? 0xAA66CCFF : 0x445A8DAA);

        // Center the label inside the actual button rectangle instead of using
        // a hand-tuned y+7 baseline. Different GUI scales make the old offset
        // look like the button has uneven top/bottom padding.
        int textY = y + (ROW_HEIGHT - this.font.lineHeight) / 2;
        textCentered(graphics, label, x, textY, width, enabled ? hovered ? TEXT_HOVER : TEXT_SECONDARY : TEXT_MUTED);
        clickZones.add(new ClickZone(x, y, width, ROW_HEIGHT, enabled, action, tooltip));
    }

    private void badge(GuiGraphicsExtractor graphics, int x, int y, int width, String label, int color) {
        fill(graphics, x, y, x + width, y + 14, 0x66000000);
        border(graphics, x, y, width, 14, color & 0xAAFFFFFF);
        textCentered(graphics, label, x, y + 4, width, color);
    }

    private void drawTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        ClickZone hovered = null;
        for (ClickZone zone : clickZones) {
            if (zone.tooltip != null && !zone.tooltip.isBlank() && zone.contains(mouseX, mouseY)) {
                hovered = zone;
                break;
            }
        }
        if (hovered == null) {
            return;
        }

        int maxTextWidth = Math.min(360, Math.max(220, this.width / 3));
        List<String> lines = wrapText(hovered.tooltip, maxTextWidth);
        if (lines.isEmpty()) {
            return;
        }

        int textWidth = 0;
        for (String line : lines) {
            textWidth = Math.max(textWidth, this.font.width(line));
        }

        int boxW = Math.min(maxTextWidth + 16, textWidth + 16);
        int boxH = 12 + lines.size() * 10;
        int x = Math.min(mouseX + 12, this.width - boxW - 8);
        int y = Math.min(mouseY + 12, this.height - boxH - 8);
        fill(graphics, x, y, x + boxW, y + boxH, 0xE0000000);
        border(graphics, x, y, boxW, boxH, 0xAA66CCFF);

        int lineY = y + 7;
        for (String line : lines) {
            text(graphics, line, x + 8, lineY, TEXT_SECONDARY);
            lineY += 10;
        }
    }

    private void beginReload(CompletableFuture<Void> reloadFuture, Component pendingMessage, Component successMessage) {
        reloadInProgress = true;
        statusMessage = pendingMessage;

        reloadFuture.whenComplete((ignored, throwable) -> Minecraft.getInstance().execute(() -> {
            reloadInProgress = false;

            if (throwable != null) {
                String message = throwable.getMessage();
                statusMessage = message == null || message.isBlank()
                        ? trc("screen.vulkanpostfx.shaderpacks.status.reload_failed")
                        : trc("screen.vulkanpostfx.shaderpacks.status.reload_failed.detail", message);
                VulkanPostFX.LOGGER.error("[{}] VPFX settings reload action failed", VulkanPostFX.MOD_ID, throwable);
            } else {
                statusMessage = successMessage;
            }

            if (VpfxScreenBridge.isCurrentScreen(this)) {
                rebuildWidgets();
            }
        }));
    }

    private void copyPackDiagnostics(VpfxPackListEntry pack) {
        String diagnostics = pack.diagnosticsText();
        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(diagnostics);
            statusMessage = trc("screen.vulkanpostfx.shaderpacks.status.diagnostics_copied", pack.name());
        } catch (Throwable t) {
            statusMessage = trc("screen.vulkanpostfx.shaderpacks.status.diagnostics_copy_failed", t.getClass().getSimpleName());
            VulkanPostFX.LOGGER.warn("[{}] Failed to copy VPFX pack diagnostics to clipboard", VulkanPostFX.MOD_ID, t);
        }
    }

    private String packBadge(VpfxPackListEntry pack) {
        if (pack.invalid()) {
            return tr("vulkanpostfx.pack.status.invalid");
        }
        if (pack.warning()) {
            return tr("vulkanpostfx.pack.status.warning");
        }
        return localizedBackendHint(pack.backendHint());
    }

    private int packBadgeColor(VpfxPackListEntry pack) {
        if (pack.invalid()) {
            return TEXT_ERROR;
        }
        if (pack.warning()) {
            return TEXT_WARN;
        }
        return switch (pack.backendHint()) {
            case "Native" -> TEXT_SUCCESS;
            case "PostChain" -> TEXT_WARN;
            case "Builtin" -> TEXT_ACCENT;
            default -> TEXT_SECONDARY;
        };
    }

    private String passTargetHint(VpfxPackListEntry pack) {
        if (pack.passCount() <= 0 && pack.targetCount() <= 0) {
            return pack.invalid() ? tr("screen.vulkanpostfx.shaderpacks.pack_hint.invalid") : tr("screen.vulkanpostfx.shaderpacks.pack_hint.non_native");
        }
        return tr("screen.vulkanpostfx.shaderpacks.pack_hint.pass_target", pack.passCount(), pack.targetCount());
    }

    private String packDescription(VpfxPackListEntry pack) {
        if (pack.invalid()) {
            return pack.diagnosticSummary() + " · " + tr("screen.vulkanpostfx.shaderpacks.pack_hint.click_copy_diagnostics");
        }
        if (pack.warning()) {
            return pack.diagnosticSummary() + " · " + passTargetHint(pack);
        }
        return pack.diagnosticSummary() + " · " + passTargetHint(pack);
    }

    private String packTooltip(VpfxPackListEntry pack) {
        StringBuilder tooltip = new StringBuilder();
        tooltip.append(pack.name()).append(" · ").append(pack.source()).append(" · ").append(passTargetHint(pack));
        tooltip.append("\n").append(tr("screen.vulkanpostfx.shaderpacks.tooltip.status", localizedPackStatus(pack)));
        tooltip.append("\n").append(tr("screen.vulkanpostfx.shaderpacks.tooltip.path", pack.sourcePath()));
        if (pack.invalid()) {
            tooltip.append("\n").append(tr("screen.vulkanpostfx.shaderpacks.pack_hint.click_copy_diagnostics"));
        } else {
            tooltip.append("\n").append(pack.diagnosticSummary());
        }
        return tooltip.toString();
    }


    private String backendSummary(VpfxUiState state) {
        String kind = state.nativeDirect() ? tr("vulkanpostfx.backend.kind.native") : state.postChainRuntime() ? tr("vulkanpostfx.backend.kind.postchain") : tr("vulkanpostfx.backend.kind.vanilla");
        return state.backendId() + " (" + kind + ")";
    }

    private int maxPage(int count) {
        if (count <= 0) {
            return 0;
        }
        return (count - 1) / PACKS_PER_PAGE;
    }

    private Layout layout() {
        int newWidth = this.width;
        if ((float) this.width / (float) this.height > 1.7777778F) {
            newWidth = (int) (this.height * 1.7777778F);
        }

        int outerWidth = Math.min(newWidth - Math.max(40, newWidth / 20), 960);
        int outerHeight = Math.min(this.height * 3 / 4 + 48, 620);
        outerWidth = Math.max(560, Math.min(outerWidth, this.width - 40));
        outerHeight = Math.max(330, Math.min(outerHeight, this.height - 40));

        int outerX = (this.width - outerWidth) / 2;
        int outerY = Math.max(12, (this.height - outerHeight) / 2);

        int contentInset = 10;
        int contentX = outerX + contentInset;
        int titleY = outerY + 9;
        int contentWidth = outerWidth - contentInset * 2;

        // Search bar must use the same left/right inset as the main content.
        // The old code used searchX = outerX + 10 but searchWidth = outerWidth,
        // so the bar was shifted right and overflowed the panel by 10 px.
        int searchX = outerX;
        int searchY = outerY - 26;
        int searchWidth = outerWidth;

        int frameX = contentX;
        int frameY = outerY + 34;
        int frameWidth = contentWidth;

        // Keep the frame above the footer. FOOTER_HEIGHT is no longer the old
        // compact 28 px value, so frameHeight must be derived from footerTop
        // instead of using the old hard-coded outerHeight - 68.
        int footerTop = outerY + outerHeight - FOOTER_HEIGHT;
        int statusLineReserve = this.font.lineHeight + 9;
        int frameBottom = footerTop - statusLineReserve;
        int frameHeight = Math.max(TAB_HEIGHT + 96, frameBottom - frameY);

        int bodyX = frameX + 8;
        int bodyY = frameY + TAB_HEIGHT + 9;
        int bodyWidth = frameWidth - 16;

        return new Layout(outerX, outerY, outerWidth, outerHeight, searchX, searchY, searchWidth,
                contentX, titleY, contentWidth, frameX, frameY, frameWidth, frameHeight, bodyX, bodyY, bodyWidth);
    }

    private static String yesNo(boolean value) {
        return value ? tr("vulkanpostfx.common.yes") : tr("vulkanpostfx.common.no");
    }

    private static String emptyAsNone(String value) {
        return value == null || value.isBlank() ? tr("vulkanpostfx.common.none") : value;
    }

    private static String shortPath(String value) {
        if (value == null || value.isBlank()) {
            return tr("vulkanpostfx.common.none");
        }
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        return slash >= 0 ? ".../" + value.substring(slash + 1) : value;
    }

    private String fitText(String value, int maxPixels) {
        if (value == null) {
            return "";
        }
        if (this.font.width(value) <= maxPixels) {
            return value;
        }
        String ellipsis = "…";
        int max = Math.max(1, value.length());
        while (max > 1 && this.font.width(value.substring(0, max - 1) + ellipsis) > maxPixels) {
            max--;
        }
        return value.substring(0, Math.max(1, max - 1)) + ellipsis;
    }

    private List<String> wrapText(String value, int maxPixels) {
        List<String> lines = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return lines;
        }
        for (String paragraph : value.replace("\r", "").split("\n")) {
            wrapParagraph(paragraph.trim(), maxPixels, lines);
        }
        return lines;
    }

    private void wrapParagraph(String value, int maxPixels, List<String> lines) {
        if (value.isBlank()) {
            return;
        }
        StringBuilder current = new StringBuilder();
        for (String word : value.split("\\s+")) {
            if (word.isBlank()) {
                continue;
            }
            if (this.font.width(word) > maxPixels) {
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                splitLongWord(word, maxPixels, lines);
                continue;
            }

            String candidate = current.isEmpty() ? word : current + " " + word;
            if (this.font.width(candidate) <= maxPixels) {
                current.setLength(0);
                current.append(candidate);
            } else {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
    }

    private void splitLongWord(String word, int maxPixels, List<String> lines) {
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            String candidate = current.toString() + word.charAt(i);
            if (!current.isEmpty() && this.font.width(candidate) > maxPixels) {
                lines.add(current.toString());
                current.setLength(0);
            }
            current.append(word.charAt(i));
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
    }

    private static String truncate(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(1, maxChars - 1)) + "…";
    }

    private void fill(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
        graphics.fill(RenderPipelines.GUI, x1, y1, x2, y2, color);
    }

    private void border(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        fill(graphics, x, y, x + width, y + 1, color);
        fill(graphics, x, y + height - 1, x + width, y + height, color);
        fill(graphics, x, y, x + 1, y + height, color);
        fill(graphics, x + width - 1, y, x + width, y + height, color);
    }

    private void text(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        graphics.text(this.font, text == null ? "" : text, x, y, color);
    }

    private void textRight(GuiGraphicsExtractor graphics, String text, int rightX, int y, int color) {
        String value = text == null ? "" : text;
        graphics.text(this.font, value, rightX - this.font.width(value), y, color);
    }

    private void textCentered(GuiGraphicsExtractor graphics, String text, int x, int y, int width, int color) {
        String value = text == null ? "" : text;
        graphics.text(this.font, value, x + (width - this.font.width(value)) / 2, y, color);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static String textOf(Component component) {
        return component == null ? "" : component.getString();
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static Component trc(String key, Object... args) {
        return Component.translatable(key, args);
    }

    private static String onOff(boolean value) {
        return tr(value ? "vulkanpostfx.common.on" : "vulkanpostfx.common.off");
    }

    private String localizedPackStatus(VpfxPackListEntry pack) {
        return switch (pack.status()) {
            case VALID -> tr("vulkanpostfx.pack.status.valid");
            case WARNING -> tr("vulkanpostfx.pack.status.warning");
            case INVALID -> tr("vulkanpostfx.pack.status.invalid");
        };
    }

    private String localizedBackendHint(String backendHint) {
        if (backendHint == null || backendHint.isBlank()) {
            return tr("vulkanpostfx.backend.kind.unknown");
        }
        return switch (backendHint) {
            case "Builtin" -> tr("vulkanpostfx.backend.kind.builtin");
            case "Native" -> tr("vulkanpostfx.backend.kind.native");
            case "PostChain" -> tr("vulkanpostfx.backend.kind.postchain");
            case "Invalid" -> tr("vulkanpostfx.pack.status.invalid");
            case "Unknown" -> tr("vulkanpostfx.backend.kind.unknown");
            default -> backendHint;
        };
    }

    private enum UiPage {
        PACKS("category.vulkanpostfx.packs", "screen.vulkanpostfx.shaderpacks.tabs.packs.tooltip"),
        GENERAL("category.vulkanpostfx.general", "screen.vulkanpostfx.shaderpacks.tabs.general.tooltip"),
        BACKEND("category.vulkanpostfx.backend", "screen.vulkanpostfx.shaderpacks.tabs.backend.tooltip"),
        DEBUG("category.vulkanpostfx.debug", "screen.vulkanpostfx.shaderpacks.tabs.debug.tooltip"),
        DEVELOPER("category.vulkanpostfx.developer", "screen.vulkanpostfx.shaderpacks.tabs.developer.tooltip"),
        ABOUT("category.vulkanpostfx.about", "screen.vulkanpostfx.shaderpacks.tabs.about.tooltip");

        private final String labelKey;
        private final String descriptionKey;

        UiPage(String labelKey, String descriptionKey) {
            this.labelKey = labelKey;
            this.descriptionKey = descriptionKey;
        }
    }

    private record Layout(
            int outerX,
            int outerY,
            int outerWidth,
            int outerHeight,
            int searchX,
            int searchY,
            int searchWidth,
            int contentX,
            int titleY,
            int contentWidth,
            int frameX,
            int frameY,
            int frameWidth,
            int frameHeight,
            int bodyX,
            int bodyY,
            int bodyWidth
    ) {
    }

    private record ClickZone(int x, int y, int width, int height, boolean enabled, Runnable action, String tooltip) {
        boolean contains(double mouseX, double mouseY) {
            return VpfxShaderPackSelectionScreen.contains(mouseX, mouseY, x, y, width, height);
        }
    }
}
