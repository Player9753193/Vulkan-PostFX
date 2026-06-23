package com.ionhex975.vulkanpostfx.client.runtime;

import com.ionhex975.vulkanpostfx.client.runtime.posteffect.ZipPostEffectConfig;
import com.ionhex975.vulkanpostfx.client.runtime.posteffect.ZipShaderReferenceValidationResult;

/**
 * 当前活动入口后处理源。
 *
 * 当前阶段记录：
 * - sourceKind: builtin / zip / none
 * - displayPath: 便于日志展示
 * - rawJson: ZIP 入口文件原始内容（builtin 暂时留空）
 * - parsedConfig: ZIP 入口文件的最小运行时中间表示（builtin 暂时留空）
 * - validationResult: shader 引用校验结果
 */
public final class ActivePostEffectSource {
    public static final ActivePostEffectSource NONE =
            new ActivePostEffectSource("none", "", "", null, null);

    private final String sourceKind;
    private final String displayPath;
    private final String rawJson;
    private final ZipPostEffectConfig parsedConfig;
    private final ZipShaderReferenceValidationResult validationResult;

    public ActivePostEffectSource(
            String sourceKind,
            String displayPath,
            String rawJson,
            ZipPostEffectConfig parsedConfig,
            ZipShaderReferenceValidationResult validationResult
    ) {
        this.sourceKind = sourceKind;
        this.displayPath = displayPath;
        this.rawJson = rawJson;
        this.parsedConfig = parsedConfig;
        this.validationResult = validationResult;
    }

    public String sourceKind() {
        return sourceKind;
    }

    public String displayPath() {
        return displayPath;
    }

    public String rawJson() {
        return rawJson;
    }

    public ZipPostEffectConfig parsedConfig() {
        return parsedConfig;
    }

    public ZipShaderReferenceValidationResult validationResult() {
        return validationResult;
    }

    public boolean isPresent() {
        return !"none".equals(sourceKind);
    }
}