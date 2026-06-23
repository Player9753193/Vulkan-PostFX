package com.ionhex975.vulkanpostfx.client.shader;

import com.ionhex975.vulkanpostfx.client.shader.include.VpfxShaderIncludeException;
import com.ionhex975.vulkanpostfx.client.shader.include.VpfxShaderIncludeProcessor;
import com.ionhex975.vulkanpostfx.client.shader.uniform.VpfxBuiltinUniformSourceInjector;

import java.util.zip.ZipFile;

/**
 * VPFX shader 源码预处理统一入口。
 *
 * 固定顺序：
 * 1. include 展开
 * 2. builtin uniform 声明注入（仅 .vsh/.fsh，不注入 .glsl include 文件）
 *
 * .glsl 文件是 include 库，不是独立 entry point，跳过注入，
 * 防止 #moj_import 引入时产生重复 uniform block 声明。
 */
public final class VpfxShaderSourcePreprocessor {
    private final VpfxShaderIncludeProcessor includeProcessor;

    public VpfxShaderSourcePreprocessor(ZipFile zipFile) {
        this.includeProcessor = new VpfxShaderIncludeProcessor(zipFile);
    }

    public String preprocess(String zipShaderPath) throws VpfxShaderIncludeException {
        String flattened = includeProcessor.process(zipShaderPath);
        if (zipShaderPath.endsWith(".glsl")) {
            return flattened;
        }
        return VpfxBuiltinUniformSourceInjector.inject(flattened);
    }
}