package com.ionhex975.vulkanpostfx.client.runtime.nativevpfx.framegraph;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent native VPFX target storage.
 *
 * This is intentionally small and conservative:
 * - normal persistent targets keep a single TextureTarget across frames;
 * - history / ping-pong targets keep two TextureTargets and expose history:<id> as the previous frame;
 * - targets are recreated when size/depth policy changes.
 */
public final class VpfxNativeHistoryTargetStore {
    private static final Map<Key, Entry> ENTRIES = new ConcurrentHashMap<>();

    private VpfxNativeHistoryTargetStore() {
    }

    public static Binding acquire(
            String runtimeNamespace,
            String targetId,
            int width,
            int height,
            boolean useDepth,
            GpuFormat format,
            boolean history,
            boolean pingPong
    ) {
        Key key = new Key(runtimeNamespace, targetId);
        boolean doubleBuffered = history || pingPong;

        Entry entry = ENTRIES.get(key);
        if (entry == null || !entry.matches(width, height, useDepth, doubleBuffered)) {
            if (entry != null) {
                entry.destroy();
            }
            entry = Entry.create(runtimeNamespace, targetId, width, height, useDepth, format, doubleBuffered);
            ENTRIES.put(key, entry);
            VulkanPostFX.LOGGER.info(
                    "[{}] VPFX NR-2.2: allocated persistent native target: id={}, namespace={}, size={}x{}, useDepth={}, history={}, pingPong={}",
                    VulkanPostFX.MOD_ID,
                    targetId,
                    runtimeNamespace,
                    width,
                    height,
                    useDepth,
                    history,
                    pingPong
            );
        }

        return entry.createBinding(targetId);
    }

    public static void clearNamespace(String runtimeNamespace) {
        if (runtimeNamespace == null || runtimeNamespace.isBlank()) {
            return;
        }

        ENTRIES.entrySet().removeIf(entry -> {
            if (!runtimeNamespace.equals(entry.getKey().runtimeNamespace())) {
                return false;
            }
            entry.getValue().destroy();
            return true;
        });
    }

    public static void clearAll() {
        for (Entry entry : ENTRIES.values()) {
            entry.destroy();
        }
        ENTRIES.clear();
    }

    public static String historyAlias(String targetId) {
        return "history:" + targetId;
    }

    public record Binding(
            String targetId,
            RenderTarget writeTarget,
            RenderTarget readTarget,
            boolean doubleBuffered,
            Entry owner
    ) {
        public boolean hasHistoryInput() {
            return doubleBuffered && readTarget != null;
        }

        public void commit() {
            if (owner != null) {
                owner.commit();
            }
        }
    }

    private record Key(String runtimeNamespace, String targetId) {
    }

    static final class Entry {
        private final int width;
        private final int height;
        private final boolean useDepth;
        private final boolean doubleBuffered;
        private final RenderTarget[] targets;
        private int writeIndex;

        private Entry(int width, int height, boolean useDepth, boolean doubleBuffered, RenderTarget[] targets) {
            this.width = width;
            this.height = height;
            this.useDepth = useDepth;
            this.doubleBuffered = doubleBuffered;
            this.targets = targets;
            this.writeIndex = 0;
        }

        static Entry create(
                String runtimeNamespace,
                String targetId,
                int width,
                int height,
                boolean useDepth,
                GpuFormat format,
                boolean doubleBuffered
        ) {
            int count = doubleBuffered ? 2 : 1;
            RenderTarget[] targets = new RenderTarget[count];
            for (int i = 0; i < count; i++) {
                targets[i] = new TextureTarget(
                        "VPFX persistent target " + runtimeNamespace + ":" + targetId + "[" + i + "]",
                        width,
                        height,
                        useDepth,
                        format
                );
            }
            return new Entry(width, height, useDepth, doubleBuffered, targets);
        }

        boolean matches(int width, int height, boolean useDepth, boolean doubleBuffered) {
            return this.width == width
                    && this.height == height
                    && this.useDepth == useDepth
                    && this.doubleBuffered == doubleBuffered
                    && targets.length == (doubleBuffered ? 2 : 1);
        }

        Binding createBinding(String targetId) {
            RenderTarget write = targets[writeIndex];
            RenderTarget read = doubleBuffered ? targets[1 - writeIndex] : targets[writeIndex];
            return new Binding(targetId, write, read, doubleBuffered, this);
        }

        void commit() {
            if (doubleBuffered) {
                writeIndex = 1 - writeIndex;
            }
        }

        void destroy() {
            for (RenderTarget target : targets) {
                if (target == null) {
                    continue;
                }
                try {
                    target.destroyBuffers();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
