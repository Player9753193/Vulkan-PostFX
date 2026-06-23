package com.ionhex975.vulkanpostfx.client.runtime.posteffect;

import java.util.Collections;
import java.util.List;

/**
 * ZIP post_effect 中 shader 引用校验结果。
 */
public final class ZipShaderReferenceValidationResult {
    private final int checkedCount;
    private final List<String> missingReferences;

    public ZipShaderReferenceValidationResult(int checkedCount, List<String> missingReferences) {
        this.checkedCount = checkedCount;
        this.missingReferences = List.copyOf(missingReferences);
    }

    public int checkedCount() {
        return checkedCount;
    }

    public List<String> missingReferences() {
        return Collections.unmodifiableList(missingReferences);
    }

    public boolean isValid() {
        return missingReferences.isEmpty();
    }
}