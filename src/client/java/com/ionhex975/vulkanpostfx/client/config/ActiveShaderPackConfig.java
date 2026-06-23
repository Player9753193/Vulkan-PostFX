package com.ionhex975.vulkanpostfx.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ionhex975.vulkanpostfx.VulkanPostFX;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 当前活动光影包配置。
 *
 * active_pack_id:
 * - "auto" 或空字符串：自动启用 shaderpacks 目录里的第一个有效 VPFX ZIP 包；没有外部包时回退 builtin
 * - "builtin" / "none" / "builtin_debug_pack"：内置调试包选择器
 * - 其他非空字符串：显式启用对应 id 的外部包
 *
 * auto_select_external_pack:
 * - true：允许 auto/空字符串选择外部 ZIP 包
 * - false：auto/空字符串会回退 builtin
 *
 * force_builtin_pack:
 * - true：真正锁定 Builtin Debug Pack。只有 /vpfx reload builtin 这类显式动作才应该写 true。
 * - false：旧版遗留的 active_pack_id="builtin" 不再锁死内置包；如果 shaderpacks/ 有 auto-safe ZIP，启动时会迁移到 auto。
 */
public final class ActiveShaderPackConfig {
    public static final String SELECTOR_AUTO = "auto";
    public static final String SELECTOR_BUILTIN = "builtin";
    public static final String SELECTOR_NONE = "none";
    public static final String BUILTIN_PACK_ID = "builtin_debug_pack";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final String activePackId;
    private final boolean autoSelectExternalPack;
    private final boolean forceBuiltinPack;

    public ActiveShaderPackConfig(String activePackId) {
        this(activePackId, true, false);
    }

    public ActiveShaderPackConfig(String activePackId, boolean autoSelectExternalPack) {
        this(activePackId, autoSelectExternalPack, false);
    }

    public ActiveShaderPackConfig(String activePackId, boolean autoSelectExternalPack, boolean forceBuiltinPack) {
        this.activePackId = sanitizePackId(activePackId);
        this.autoSelectExternalPack = autoSelectExternalPack;
        this.forceBuiltinPack = forceBuiltinPack;
    }

    public String activePackId() {
        return activePackId;
    }

    public boolean autoSelectExternalPack() {
        return autoSelectExternalPack && isAutoSelector(activePackId);
    }

    public boolean forceBuiltinPack() {
        return forceBuiltinPack && isBuiltinSelector(activePackId);
    }

    /**
     * 旧配置迁移入口：active_pack_id 已经是 builtin，但没有 force_builtin_pack=true。
     * 这种配置大多来自旧 fallback 或旧默认值，不应该继续把用户卡在 debug_invert。
     */
    public boolean isLegacyBuiltinSelector() {
        return isBuiltinSelector(activePackId) && !forceBuiltinPack;
    }

    /**
     * 兼容旧调用名：这里表示“配置里写了明确外部包 id”。
     */
    public boolean usesExternalPack() {
        return hasExplicitExternalPackId();
    }

    public boolean hasExplicitExternalPackId() {
        return !activePackId.isBlank()
                && !isAutoSelector(activePackId)
                && !isBuiltinSelector(activePackId);
    }

    public boolean forcesBuiltinPack() {
        return forceBuiltinPack()
                || (!autoSelectExternalPack && isAutoSelector(activePackId));
    }

    public String selectionModeForLog() {
        if (forceBuiltinPack()) {
            return "builtin-forced";
        }
        if (isLegacyBuiltinSelector()) {
            return "builtin-legacy-migratable";
        }
        if (forcesBuiltinPack()) {
            return "builtin";
        }
        if (hasExplicitExternalPackId()) {
            return "explicit:" + activePackId;
        }
        return autoSelectExternalPack() ? "auto-external" : "builtin";
    }

    public static ActiveShaderPackConfig defaultConfig() {
        return new ActiveShaderPackConfig(SELECTOR_AUTO, true, false);
    }

    public static ActiveShaderPackConfig forcedBuiltinConfig() {
        return new ActiveShaderPackConfig(SELECTOR_BUILTIN, false, true);
    }

    public static ActiveShaderPackConfig loadOrCreate(Path configPath) {
        try {
            Files.createDirectories(configPath.getParent());

            if (!Files.exists(configPath)) {
                ActiveShaderPackConfig defaultConfig = defaultConfig();
                save(configPath, defaultConfig);
                VulkanPostFX.LOGGER.info(
                        "[{}] Created default shader pack config at {} (mode={})",
                        VulkanPostFX.MOD_ID,
                        configPath,
                        defaultConfig.selectionModeForLog()
                );
                return defaultConfig;
            }

            try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (parsed == null || !parsed.isJsonObject()) {
                    throw new IOException("Config root must be a JSON object");
                }

                JsonObject root = parsed.getAsJsonObject();
                String activePackId = readString(root, "active_pack_id", SELECTOR_AUTO);
                boolean autoSelectExternalPack = readBoolean(root, "auto_select_external_pack", true);
                boolean forceBuiltinPack = readBoolean(root, "force_builtin_pack", false);

                ActiveShaderPackConfig config = new ActiveShaderPackConfig(activePackId, autoSelectExternalPack, forceBuiltinPack);
                VulkanPostFX.LOGGER.info(
                        "[{}] Loaded shader pack config: active_pack_id='{}', auto_select_external_pack={}, force_builtin_pack={}, mode={}",
                        VulkanPostFX.MOD_ID,
                        config.activePackId(),
                        autoSelectExternalPack,
                        forceBuiltinPack,
                        config.selectionModeForLog()
                );
                return config;
            }
        } catch (Exception e) {
            VulkanPostFX.LOGGER.error(
                    "[{}] Failed to load shader pack config from {}, falling back to auto external selection",
                    VulkanPostFX.MOD_ID,
                    configPath,
                    e
            );
            return defaultConfig();
        }
    }

    public static void save(Path configPath, ActiveShaderPackConfig config) throws IOException {
        Files.createDirectories(configPath.getParent());

        JsonObject root = new JsonObject();
        root.addProperty("_comment", "active_pack_id can be 'auto', 'builtin', 'none', or a discovered external VPFX pack id. force_builtin_pack=true is only for explicitly locking the built-in debug pack.");
        root.addProperty("active_pack_id", config.activePackId().isBlank() ? SELECTOR_AUTO : config.activePackId());
        root.addProperty("auto_select_external_pack", config.autoSelectExternalPack);
        root.addProperty("force_builtin_pack", config.forceBuiltinPack);

        try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
    }

    private static String readString(JsonObject root, String key, String defaultValue) {
        if (!root.has(key) || !root.get(key).isJsonPrimitive()) {
            return defaultValue;
        }
        try {
            return root.get(key).getAsString();
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static boolean readBoolean(JsonObject root, String key, boolean defaultValue) {
        if (!root.has(key) || !root.get(key).isJsonPrimitive()) {
            return defaultValue;
        }
        try {
            return root.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static String sanitizePackId(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isAutoSelector(String value) {
        String normalized = normalizeSelector(value);
        return normalized.isBlank() || SELECTOR_AUTO.equals(normalized);
    }

    private static boolean isBuiltinSelector(String value) {
        String normalized = normalizeSelector(value);
        return SELECTOR_BUILTIN.equals(normalized)
                || SELECTOR_NONE.equals(normalized)
                || BUILTIN_PACK_ID.equals(normalized);
    }

    private static String normalizeSelector(String value) {
        return sanitizePackId(value).toLowerCase(Locale.ROOT);
    }
}
