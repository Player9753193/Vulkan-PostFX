package com.ionhex975.vulkanpostfx.client.light;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Local client-side item -> screen-space held-light table.
 *
 * Values are intentionally artistic, not Minecraft light-level exact values.
 * They feed post effects only and never modify world light propagation.
 */
public final class VpfxHeldLightRegistry {
    private static final Map<Item, VpfxHeldLightInfo> LIGHTS = new IdentityHashMap<>();

    static {
        registerDefaults();
    }

    private VpfxHeldLightRegistry() {
    }

    public static VpfxHeldLightInfo resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return VpfxHeldLightInfo.NONE;
        }

        VpfxHeldLightInfo info = LIGHTS.get(stack.getItem());
        return info == null ? VpfxHeldLightInfo.NONE : info;
    }

    public static void register(Item item, VpfxHeldLightInfo info) {
        if (item == null || info == null) {
            return;
        }
        LIGHTS.put(item, info);
    }

    private static void registerDefaults() {
        register(Items.TORCH, VpfxHeldLightInfo.of(1.00F, 0.62F, 0.28F, 1.00F, 1.00F, "torch"));
        register(Items.SOUL_TORCH, VpfxHeldLightInfo.of(0.35F, 0.70F, 1.00F, 0.85F, 0.95F, "soul_torch"));
        register(Items.REDSTONE_TORCH, VpfxHeldLightInfo.of(1.00F, 0.12F, 0.08F, 0.45F, 0.78F, "redstone_torch"));
        register(Items.COPPER_TORCH, VpfxHeldLightInfo.of(0.30F, 0.95F, 0.82F, 0.75F, 0.90F, "copper_torch"));

        register(Items.LANTERN, VpfxHeldLightInfo.of(1.00F, 0.70F, 0.34F, 1.02F, 1.05F, "lantern"));
        register(Items.SOUL_LANTERN, VpfxHeldLightInfo.of(0.35F, 0.78F, 1.00F, 0.90F, 1.00F, "soul_lantern"));

        register(Items.GLOWSTONE, VpfxHeldLightInfo.of(1.00F, 0.88F, 0.55F, 1.10F, 1.12F, "glowstone"));
        register(Items.SEA_LANTERN, VpfxHeldLightInfo.of(0.55F, 0.90F, 1.00F, 1.00F, 1.08F, "sea_lantern"));
        register(Items.END_ROD, VpfxHeldLightInfo.of(0.86F, 0.82F, 1.00F, 0.92F, 1.00F, "end_rod"));
        register(Items.JACK_O_LANTERN, VpfxHeldLightInfo.of(1.00F, 0.55F, 0.18F, 0.95F, 1.00F, "jack_o_lantern"));

        register(Items.LAVA_BUCKET, VpfxHeldLightInfo.of(1.00F, 0.32F, 0.08F, 1.15F, 1.16F, "lava_bucket"));
        register(Items.CAMPFIRE, VpfxHeldLightInfo.of(1.00F, 0.55F, 0.20F, 0.92F, 1.00F, "campfire"));
        register(Items.SOUL_CAMPFIRE, VpfxHeldLightInfo.of(0.30F, 0.72F, 1.00F, 0.82F, 0.95F, "soul_campfire"));
        register(Items.CANDLE, VpfxHeldLightInfo.of(1.00F, 0.68F, 0.35F, 0.35F, 0.62F, "candle"));
    }
}
