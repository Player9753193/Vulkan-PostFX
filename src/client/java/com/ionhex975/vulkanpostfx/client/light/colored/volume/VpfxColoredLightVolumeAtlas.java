package com.ionhex975.vulkanpostfx.client.light.colored.volume;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.light.colored.VpfxColoredLightCollector;
import com.ionhex975.vulkanpostfx.client.light.colored.VpfxColoredLightInfo;
import com.ionhex975.vulkanpostfx.client.light.colored.VpfxColoredLightSnapshot;
import com.ionhex975.vulkanpostfx.client.runtime.texture.dynamic.VpfxRuntimeTextureBus;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * CPU-built low-resolution colored-light 3D volume packed into a 2D RGBA8 atlas.
 *
 * This intentionally avoids Iris-level custom images / SSBO / compute shaders.
 * It gives VPFX a conservative, PostChain-friendly data texture that later
 * raymarch shaders can sample as a first colored-light transport approximation.
 */
public final class VpfxColoredLightVolumeAtlas {
    public static final int VOLUME_SIZE_X = 32;
    public static final int VOLUME_SIZE_Y = 16;
    public static final int VOLUME_SIZE_Z = 32;
    public static final int TILES_PER_ROW = 4;
    public static final int ATLAS_WIDTH = VOLUME_SIZE_X * TILES_PER_ROW;
    public static final int ATLAS_HEIGHT = VOLUME_SIZE_Z * ((VOLUME_SIZE_Y + TILES_PER_ROW - 1) / TILES_PER_ROW);
    public static final float VOXEL_WORLD_SIZE = 1.5F;
    public static final boolean OCCLUSION_ENABLED = true;
    public static final int OCCLUSION_MAX_STEPS = 10;
    public static final float OCCLUSION_SOLID_TRANSMISSION = 0.18F;
    public static final float OCCLUSION_MIN_TRANSMISSION = 0.035F;
    public static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath(VulkanPostFX.MOD_ID, VpfxRuntimeTextureBus.COLORED_LIGHT_VOLUME);

    private static DynamicTexture texture;
    private static NativeImage image;
    private static VpfxColoredLightVolumeState state = VpfxColoredLightVolumeState.unavailable("not built yet");
    private static long lastBuiltCollectorFrame = Long.MIN_VALUE;
    private static int lastOriginX = Integer.MIN_VALUE;
    private static int lastOriginY = Integer.MIN_VALUE;
    private static int lastOriginZ = Integer.MIN_VALUE;
    private static boolean textureRegistered;
    private static final ThreadLocal<BlockPos.MutableBlockPos> OCCLUSION_SAMPLE_POS = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    private VpfxColoredLightVolumeAtlas() {
    }

    public static synchronized VpfxColoredLightVolumeState currentState() {
        return state;
    }

    public static synchronized void invalidate(String reason) {
        lastBuiltCollectorFrame = Long.MIN_VALUE;
        lastOriginX = Integer.MIN_VALUE;
        lastOriginY = Integer.MIN_VALUE;
        lastOriginZ = Integer.MIN_VALUE;
        state = VpfxColoredLightVolumeState.unavailable(reason);
        VpfxRuntimeTextureBus.markUnavailable(VpfxRuntimeTextureBus.COLORED_LIGHT_VOLUME, state.reason());
    }

    public static synchronized VpfxColoredLightVolumeState update(Minecraft minecraft, boolean force) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            invalidate("client level/player unavailable");
            return state;
        }

        if (!RenderSystem.isOnRenderThread()) {
            // DynamicTexture allocation/upload must stay on the render thread.
            state = VpfxColoredLightVolumeState.unavailable("not on render thread; atlas upload skipped");
            VpfxRuntimeTextureBus.markUnavailable(VpfxRuntimeTextureBus.COLORED_LIGHT_VOLUME, state.reason());
            return state;
        }

        VpfxColoredLightSnapshot snapshot = VpfxColoredLightCollector.currentSnapshot(force);
        if (!snapshot.enabled()) {
            clearTexture(minecraft, snapshot.reason());
            return state;
        }

        boolean sameCollectorFrame = snapshot.frameEpoch() == lastBuiltCollectorFrame;
        boolean sameOrigin = snapshot.originX() == lastOriginX
                && snapshot.originY() == lastOriginY
                && snapshot.originZ() == lastOriginZ;
        if (!force && sameCollectorFrame && sameOrigin && state.atlasReady()) {
            return state;
        }

        ensureTexture(minecraft);
        buildAtlas(minecraft.level, snapshot);
        texture.upload();

        lastBuiltCollectorFrame = snapshot.frameEpoch();
        lastOriginX = snapshot.originX();
        lastOriginY = snapshot.originY();
        lastOriginZ = snapshot.originZ();

        VpfxRuntimeTextureBus.markReady(
                VpfxRuntimeTextureBus.COLORED_LIGHT_VOLUME,
                ATLAS_WIDTH,
                ATLAS_HEIGHT,
                snapshot.frameEpoch(),
                "colored light volume atlas uploaded: " + state.summary()
        );
        return state;
    }

    private static void ensureTexture(Minecraft minecraft) {
        if (image == null || image.isClosed() || image.getWidth() != ATLAS_WIDTH || image.getHeight() != ATLAS_HEIGHT) {
            if (texture != null) {
                texture.close();
                texture = null;
                textureRegistered = false;
            }
            image = new NativeImage(ATLAS_WIDTH, ATLAS_HEIGHT, true);
            texture = new DynamicTexture(() -> "VPFX Colored Light Volume Atlas", image);
            textureRegistered = false;
        }

        if (!textureRegistered) {
            minecraft.getTextureManager().register(TEXTURE_ID, texture);
            textureRegistered = true;
        }
    }

    private static void clearTexture(Minecraft minecraft, String reason) {
        if (!RenderSystem.isOnRenderThread()) {
            state = VpfxColoredLightVolumeState.unavailable(reason);
            VpfxRuntimeTextureBus.markUnavailable(VpfxRuntimeTextureBus.COLORED_LIGHT_VOLUME, state.reason());
            return;
        }

        ensureTexture(minecraft);
        fillImage(0);
        texture.upload();
        state = new VpfxColoredLightVolumeState(
                true,
                true,
                ATLAS_WIDTH,
                ATLAS_HEIGHT,
                VOLUME_SIZE_X,
                VOLUME_SIZE_Y,
                VOLUME_SIZE_Z,
                TILES_PER_ROW,
                VOXEL_WORLD_SIZE,
                0.0D,
                0.0D,
                0.0D,
                minecraft.level == null ? -1L : minecraft.level.getGameTime(),
                0L,
                0,
                0,
                OCCLUSION_ENABLED,
                0,
                0,
                0,
                1.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                TEXTURE_ID,
                reason == null || reason.isBlank() ? "no colored lights" : reason
        );
        VpfxRuntimeTextureBus.markReady(
                VpfxRuntimeTextureBus.COLORED_LIGHT_VOLUME,
                ATLAS_WIDTH,
                ATLAS_HEIGHT,
                state.frameEpoch(),
                "colored light volume atlas blank: " + state.reason()
        );
    }

    private static void buildAtlas(Level level, VpfxColoredLightSnapshot snapshot) {
        long startNanos = System.nanoTime();
        List<VpfxColoredLightInfo> lights = snapshot.lights();
        double originX = snapshot.originX() + 0.5D - (VOLUME_SIZE_X * VOXEL_WORLD_SIZE) * 0.5D;
        double originY = snapshot.originY() + 1.0D - (VOLUME_SIZE_Y * VOXEL_WORLD_SIZE) * 0.5D;
        double originZ = snapshot.originZ() + 0.5D - (VOLUME_SIZE_Z * VOXEL_WORLD_SIZE) * 0.5D;

        int contributing = 0;
        OcclusionStats occlusionStats = new OcclusionStats();
        float maxR = 0.0F;
        float maxG = 0.0F;
        float maxB = 0.0F;
        float maxA = 0.0F;

        for (int y = 0; y < VOLUME_SIZE_Y; y++) {
            for (int z = 0; z < VOLUME_SIZE_Z; z++) {
                for (int x = 0; x < VOLUME_SIZE_X; x++) {
                    double wx = originX + (x + 0.5D) * VOXEL_WORLD_SIZE;
                    double wy = originY + (y + 0.5D) * VOXEL_WORLD_SIZE;
                    double wz = originZ + (z + 0.5D) * VOXEL_WORLD_SIZE;

                    float r = 0.0F;
                    float g = 0.0F;
                    float b = 0.0F;

                    for (VpfxColoredLightInfo light : lights) {
                        float radius = Math.max(0.001F, light.radius());
                        double lx = light.blockX() + 0.5D;
                        double ly = light.blockY() + 0.5D;
                        double lz = light.blockZ() + 0.5D;
                        double dx = wx - lx;
                        double dy = wy - ly;
                        double dz = wz - lz;
                        double distSq = dx * dx + dy * dy + dz * dz;
                        double radiusSq = (double) radius * radius;
                        if (distSq >= radiusSq) {
                            continue;
                        }

                        float dist = (float) Math.sqrt(distSq);
                        float falloff = 1.0F - dist / radius;
                        falloff = falloff * falloff;

                        float transmission = occlusionTransmission(level, light, lx, ly, lz, wx, wy, wz, dist, occlusionStats);
                        if (transmission <= OCCLUSION_MIN_TRANSMISSION) {
                            continue;
                        }

                        float energy = light.intensity() * falloff * transmission;

                        r += light.red() * energy;
                        g += light.green() * energy;
                        b += light.blue() * energy;
                    }

                    float alpha = Math.min(1.0F, (float) Math.sqrt(r * r + g * g + b * b) * 0.55F);
                    float outR = compress(r);
                    float outG = compress(g);
                    float outB = compress(b);
                    if (alpha > 0.003F) {
                        contributing++;
                    }
                    maxR = Math.max(maxR, outR);
                    maxG = Math.max(maxG, outG);
                    maxB = Math.max(maxB, outB);
                    maxA = Math.max(maxA, alpha);

                    setVoxel(x, y, z, outR, outG, outB, alpha);
                }
            }
        }

        long elapsed = System.nanoTime() - startNanos;
        state = new VpfxColoredLightVolumeState(
                true,
                true,
                ATLAS_WIDTH,
                ATLAS_HEIGHT,
                VOLUME_SIZE_X,
                VOLUME_SIZE_Y,
                VOLUME_SIZE_Z,
                TILES_PER_ROW,
                VOXEL_WORLD_SIZE,
                originX,
                originY,
                originZ,
                snapshot.frameEpoch(),
                elapsed,
                snapshot.lightCount(),
                contributing,
                OCCLUSION_ENABLED,
                occlusionStats.rayCount,
                occlusionStats.blockedRayCount,
                occlusionStats.sampleCount,
                occlusionStats.averageTransmission(),
                maxR,
                maxG,
                maxB,
                maxA,
                TEXTURE_ID,
                snapshot.lightCount() == 0 ? "blank atlas" : "ok"
        );
    }

    private static float occlusionTransmission(
            Level level,
            VpfxColoredLightInfo light,
            double lightX,
            double lightY,
            double lightZ,
            double sampleX,
            double sampleY,
            double sampleZ,
            float distance,
            OcclusionStats stats
    ) {
        if (!OCCLUSION_ENABLED || level == null || distance < 2.25F || light.source().contains("hand")) {
            return 1.0F;
        }

        stats.rayCount++;

        int steps = Math.max(2, Math.min(OCCLUSION_MAX_STEPS, (int) Math.ceil(distance / 1.35F)));
        double stepX = (sampleX - lightX) / (double) steps;
        double stepY = (sampleY - lightY) / (double) steps;
        double stepZ = (sampleZ - lightZ) / (double) steps;

        float transmission = 1.0F;
        boolean blocked = false;
        BlockPos.MutableBlockPos mutablePos = OCCLUSION_SAMPLE_POS.get();

        for (int i = 1; i < steps; i++) {
            double px = lightX + stepX * i;
            double py = lightY + stepY * i;
            double pz = lightZ + stepZ * i;
            int bx = (int) Math.floor(px);
            int by = (int) Math.floor(py);
            int bz = (int) Math.floor(pz);

            // Do not let the emitter block itself shadow the entire volume.
            if (bx == light.blockX() && by == light.blockY() && bz == light.blockZ()) {
                continue;
            }

            stats.sampleCount++;
            mutablePos.set(bx, by, bz);
            BlockState state = level.getBlockState(mutablePos);
            float blocker = blockerStrength(state);
            if (blocker <= 0.001F) {
                continue;
            }

            blocked = true;
            transmission *= Math.max(0.0F, 1.0F - blocker);
            if (transmission <= OCCLUSION_MIN_TRANSMISSION) {
                transmission = 0.0F;
                break;
            }
        }

        if (blocked) {
            stats.blockedRayCount++;
        }
        stats.transmissionSum += transmission;
        return transmission;
    }

    private static float blockerStrength(BlockState state) {
        if (state == null || state.isAir()) {
            return 0.0F;
        }
        if (state.isSolidRender()) {
            return 1.0F - OCCLUSION_SOLID_TRANSMISSION;
        }
        if (state.canOcclude()) {
            return 0.48F;
        }
        if (state.getLightEmission() > 0) {
            return 0.0F;
        }
        return 0.18F;
    }

    private static void setVoxel(int x, int y, int z, float red, float green, float blue, float alpha) {
        int tileX = y % TILES_PER_ROW;
        int tileY = y / TILES_PER_ROW;
        int atlasX = tileX * VOLUME_SIZE_X + x;
        int atlasY = tileY * VOLUME_SIZE_Z + z;
        int pixel = ARGB.color(toByte(alpha), toByte(red), toByte(green), toByte(blue));
        image.setPixel(atlasX, atlasY, pixel);
    }

    private static void fillImage(int pixel) {
        if (image == null || image.isClosed()) {
            return;
        }
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setPixel(x, y, pixel);
            }
        }
    }

    private static float compress(float value) {
        if (!(value > 0.0F)) {
            return 0.0F;
        }
        // Cheap HDR compression for RGBA8 storage. The later raymarch pass can
        // still treat this as perceptual colored-light density rather than raw energy.
        return 1.0F - (float) Math.exp(-value * 0.72F);
    }

    private static int toByte(float value) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        return Math.max(0, Math.min(255, Math.round(clamped * 255.0F)));
    }
    private static final class OcclusionStats {
        int rayCount;
        int blockedRayCount;
        int sampleCount;
        double transmissionSum;

        float averageTransmission() {
            if (rayCount <= 0) {
                return 1.0F;
            }
            return (float) Math.max(0.0D, Math.min(1.0D, transmissionSum / (double) rayCount));
        }
    }

}
