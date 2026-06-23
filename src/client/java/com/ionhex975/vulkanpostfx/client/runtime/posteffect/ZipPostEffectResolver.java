package com.ionhex975.vulkanpostfx.client.runtime.posteffect;

import com.ionhex975.vulkanpostfx.client.effect.PostFxEffectRegistry;

/**
 * 把 ZIP 入口后处理配置，解析成当前 Loader 能执行的“内置效果键”。
 *
 * 当前是过渡桥：
 * - 还不直接执行 ZIP 内 shader
 * - 但 ZIP 的 post_effect 内容会决定当前可见效果
 *
 * 支持：
 * - 任意 namespace，只要 path 是 post/grayscale、post/invert、post/blit
 */
public final class ZipPostEffectResolver {
    private static final String PATH_GRAYSCALE = "post/grayscale";
    private static final String PATH_INVERT = "post/invert";
    private static final String PATH_BLIT = "post/blit";

    private ZipPostEffectResolver() {
    }

    public static String resolveEffectKey(ZipPostEffectConfig config) {
        if (config == null || config.passes().isEmpty()) {
            return PostFxEffectRegistry.DEBUG_INVERT;
        }

        for (ZipPostEffectPass pass : config.passes()) {
            String fragment = pass.fragmentShader();
            String fragmentPath = extractPath(fragment);

            if (PATH_BLIT.equals(fragmentPath)) {
                continue;
            }

            if (PATH_GRAYSCALE.equals(fragmentPath)) {
                return PostFxEffectRegistry.DEBUG_GRAYSCALE;
            }

            if (PATH_INVERT.equals(fragmentPath)) {
                return PostFxEffectRegistry.DEBUG_INVERT;
            }
        }

        return PostFxEffectRegistry.DEBUG_INVERT;
    }

    private static String extractPath(String shaderRef) {
        if (shaderRef == null) {
            return "";
        }

        int colon = shaderRef.indexOf(':');
        if (colon < 0 || colon == shaderRef.length() - 1) {
            return shaderRef;
        }

        return shaderRef.substring(colon + 1);
    }
}