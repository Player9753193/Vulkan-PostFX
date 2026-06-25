package com.ionhex975.vulkanpostfx.client.light.colored;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * CPU-side colored light collector used as the first stage for VPFX colored
 * light volume experiments.
 *
 * This class deliberately only collects light sources and exposes diagnostics.
 * VpfxColoredLightVolumeAtlas owns the separate atlas build/upload step so the
 * collector can remain a small, testable world-data query layer.
 */
public final class VpfxColoredLightCollector {
    public static final int DEFAULT_SCAN_RADIUS = 24;
    public static final int DEFAULT_MAX_LIGHTS = 128;
    public static final int DEFAULT_UPDATE_INTERVAL_TICKS = 4;

    private static VpfxColoredLightSnapshot lastSnapshot = VpfxColoredLightSnapshot.unavailable("not scanned yet");
    private static long lastScanGameTime = Long.MIN_VALUE;
    private static int lastOriginX = Integer.MIN_VALUE;
    private static int lastOriginY = Integer.MIN_VALUE;
    private static int lastOriginZ = Integer.MIN_VALUE;

    private VpfxColoredLightCollector() {
    }

    public static synchronized VpfxColoredLightSnapshot currentSnapshot() {
        return currentSnapshot(false);
    }

    public static synchronized VpfxColoredLightSnapshot currentSnapshot(boolean force) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            lastSnapshot = VpfxColoredLightSnapshot.unavailable("client level/player unavailable");
            return lastSnapshot;
        }

        Level level = minecraft.level;
        Player player = minecraft.player;
        BlockPos origin = player.blockPosition();
        long gameTime = level.getGameTime();

        boolean movedBlock = origin.getX() != lastOriginX || origin.getY() != lastOriginY || origin.getZ() != lastOriginZ;
        boolean intervalElapsed = lastScanGameTime == Long.MIN_VALUE
                || gameTime - lastScanGameTime >= DEFAULT_UPDATE_INTERVAL_TICKS;

        if (!force && !movedBlock && !intervalElapsed && lastSnapshot.enabled()) {
            return lastSnapshot;
        }

        lastOriginX = origin.getX();
        lastOriginY = origin.getY();
        lastOriginZ = origin.getZ();
        lastScanGameTime = gameTime;
        lastSnapshot = scan(level, player, origin, gameTime);

        return lastSnapshot;
    }

    public static synchronized void invalidate(String reason) {
        lastScanGameTime = Long.MIN_VALUE;
        lastOriginX = Integer.MIN_VALUE;
        lastOriginY = Integer.MIN_VALUE;
        lastOriginZ = Integer.MIN_VALUE;
        lastSnapshot = VpfxColoredLightSnapshot.unavailable(reason);
    }

    private static VpfxColoredLightSnapshot scan(Level level, Player player, BlockPos origin, long gameTime) {
        long startNanos = System.nanoTime();
        List<VpfxColoredLightInfo> lights = new ArrayList<>();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        int radius = DEFAULT_SCAN_RADIUS;

        for (int y = origin.getY() - radius; y <= origin.getY() + radius; y++) {
            int minY = level.getMinY();
            int maxY = level.getMaxY();
            if (y < minY || y > maxY) {
                continue;
            }

            for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z++) {
                for (int x = origin.getX() - radius; x <= origin.getX() + radius; x++) {
                    mutable.set(x, y, z);
                    BlockState state = level.getBlockState(mutable);
                    VpfxColoredLightInfo light = VpfxColoredLightRegistry.resolve(state, x, y, z);
                    if (light != null && light.enabled()) {
                        lights.add(light);
                    }
                }
            }
        }

        // Include local held lights as world-space samples. They are intentionally
        // separate from the screen-space held-light uniform so the later volume
        // builder can make the player's held light contribute to the same atlas.
        BlockPos playerLightPos = player.blockPosition().above();
        VpfxColoredLightInfo mainHand = VpfxColoredLightRegistry.resolveHeld(
                player.getMainHandItem(),
                playerLightPos.getX(),
                playerLightPos.getY(),
                playerLightPos.getZ(),
                "main_hand"
        );
        if (mainHand != null && mainHand.enabled()) {
            lights.add(mainHand);
        }
        VpfxColoredLightInfo offHand = VpfxColoredLightRegistry.resolveHeld(
                player.getOffhandItem(),
                playerLightPos.getX(),
                playerLightPos.getY(),
                playerLightPos.getZ(),
                "off_hand"
        );
        if (offHand != null && offHand.enabled()) {
            lights.add(offHand);
        }

        int rawCount = lights.size();
        lights.sort(Comparator.comparingDouble(light -> light.distanceSquaredTo(player.getX(), player.getY(), player.getZ())));
        boolean clipped = rawCount > DEFAULT_MAX_LIGHTS;
        if (lights.size() > DEFAULT_MAX_LIGHTS) {
            lights = new ArrayList<>(lights.subList(0, DEFAULT_MAX_LIGHTS));
        }

        long elapsedNanos = System.nanoTime() - startNanos;
        String reason = rawCount == 0 ? "no registered colored light sources in scan radius" : "ok";
        return new VpfxColoredLightSnapshot(
                true,
                radius,
                DEFAULT_MAX_LIGHTS,
                origin.getX(),
                origin.getY(),
                origin.getZ(),
                gameTime,
                elapsedNanos,
                rawCount,
                clipped,
                lights,
                reason
        );
    }
}
