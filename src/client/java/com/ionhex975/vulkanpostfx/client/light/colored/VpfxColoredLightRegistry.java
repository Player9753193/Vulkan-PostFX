package com.ionhex975.vulkanpostfx.client.light.colored;

import com.ionhex975.vulkanpostfx.client.light.VpfxHeldLightInfo;
import com.ionhex975.vulkanpostfx.client.light.VpfxHeldLightRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Artistic block/item -> RGB light table for VPFX colored-light experiments.
 *
 * These values are not vanilla light levels. They are deliberately tuned for a
 * future low-resolution volume atlas and screen-space raymarch pass.
 */
public final class VpfxColoredLightRegistry {
    private static final Map<Block, Template> BLOCK_LIGHTS = new IdentityHashMap<>();

    static {
        registerDefaults();
    }

    private VpfxColoredLightRegistry() {
    }

    public static VpfxColoredLightInfo resolve(BlockState state, int x, int y, int z) {
        if (state == null) {
            return null;
        }
        Template template = BLOCK_LIGHTS.get(state.getBlock());
        if (template == null) {
            return null;
        }
        if (state.hasProperty(BlockStateProperties.LIT) && !state.getValue(BlockStateProperties.LIT)) {
            return null;
        }
        return template.at(x, y, z, "block");
    }

    public static VpfxColoredLightInfo resolveHeld(ItemStack stack, int x, int y, int z, String source) {
        VpfxHeldLightInfo held = VpfxHeldLightRegistry.resolve(stack);
        if (held == null || !held.enabled()) {
            return null;
        }
        return VpfxColoredLightInfo.at(
                x,
                y,
                z,
                held.red(),
                held.green(),
                held.blue(),
                held.intensity(),
                Math.max(4.0F, held.radius() * 12.0F),
                held.debugName(),
                source
        );
    }

    public static void register(Block block, float red, float green, float blue, float intensity, float radius, String debugName) {
        if (block == null) {
            return;
        }
        BLOCK_LIGHTS.put(block, new Template(red, green, blue, intensity, radius, debugName));
    }

    public static int registeredBlockCount() {
        return BLOCK_LIGHTS.size();
    }

    private static void registerDefaults() {
        register(Blocks.TORCH, 1.00F, 0.62F, 0.28F, 1.00F, 14.0F, "torch");
        register(Blocks.WALL_TORCH, 1.00F, 0.62F, 0.28F, 1.00F, 14.0F, "wall_torch");
        register(Blocks.SOUL_TORCH, 0.35F, 0.70F, 1.00F, 0.85F, 12.0F, "soul_torch");
        register(Blocks.SOUL_WALL_TORCH, 0.35F, 0.70F, 1.00F, 0.85F, 12.0F, "soul_wall_torch");
        register(Blocks.COPPER_TORCH, 0.30F, 0.95F, 0.82F, 0.75F, 12.0F, "copper_torch");
        register(Blocks.COPPER_WALL_TORCH, 0.30F, 0.95F, 0.82F, 0.75F, 12.0F, "copper_wall_torch");
        register(Blocks.REDSTONE_TORCH, 1.00F, 0.12F, 0.08F, 0.45F, 8.0F, "redstone_torch");
        register(Blocks.REDSTONE_WALL_TORCH, 1.00F, 0.12F, 0.08F, 0.45F, 8.0F, "redstone_wall_torch");

        register(Blocks.LANTERN, 1.00F, 0.70F, 0.34F, 1.02F, 15.0F, "lantern");
        register(Blocks.SOUL_LANTERN, 0.35F, 0.78F, 1.00F, 0.90F, 13.0F, "soul_lantern");
        register(Blocks.GLOWSTONE, 1.00F, 0.88F, 0.55F, 1.10F, 15.0F, "glowstone");
        register(Blocks.SEA_LANTERN, 0.55F, 0.90F, 1.00F, 1.00F, 15.0F, "sea_lantern");
        register(Blocks.END_ROD, 0.86F, 0.82F, 1.00F, 0.92F, 12.0F, "end_rod");
        register(Blocks.JACK_O_LANTERN, 1.00F, 0.55F, 0.18F, 0.95F, 14.0F, "jack_o_lantern");

        register(Blocks.LAVA, 1.00F, 0.30F, 0.06F, 1.25F, 16.0F, "lava");
        register(Blocks.CAMPFIRE, 1.00F, 0.55F, 0.20F, 0.92F, 14.0F, "campfire");
        register(Blocks.SOUL_CAMPFIRE, 0.30F, 0.72F, 1.00F, 0.82F, 12.0F, "soul_campfire");
        register(Blocks.CANDLE, 1.00F, 0.68F, 0.35F, 0.35F, 6.0F, "candle");
    }

    private record Template(
            float red,
            float green,
            float blue,
            float intensity,
            float radius,
            String debugName
    ) {
        VpfxColoredLightInfo at(int x, int y, int z, String source) {
            return VpfxColoredLightInfo.at(x, y, z, red, green, blue, intensity, radius, debugName, source);
        }
    }
}
