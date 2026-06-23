package com.ionhex975.vulkanpostfx.client.mixin;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.runtime.zip.RuntimeZipPackLocator;
import com.ionhex975.vulkanpostfx.client.runtime.zip.RuntimeZipPackState;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

@Mixin(ClientPackSource.class)
public abstract class RuntimeZipPackProfileInjectorMixin {
    @Inject(method = "populatePackList", at = @At("TAIL"))
    private void vulkanpostfx$injectRuntimeZipPack(
            BiConsumer<String, Function<String, Pack>> discoveredPacks,
            CallbackInfo ci
    ) {
        if (!RuntimeZipPackLocator.isReady()) {
            return;
        }

        String packId = RuntimeZipPackState.getPackId();
        Path root = RuntimeZipPackLocator.getRuntimeRootOrThrow();

        discoveredPacks.accept(packId, ignored -> {
            PackLocationInfo location = new PackLocationInfo(
                    packId,
                    Component.literal("VulkanPostFX Runtime ZIP Pack"),
                    PackSource.BUILT_IN,
                    Optional.of(KnownPack.vanilla(packId))
            );

            Pack.ResourcesSupplier supplier = new PathPackResources.PathResourcesSupplier(root);

            // 开发阶段：强制默认启用，先让包真正进入资源系统
            PackSelectionConfig selectionConfig = new PackSelectionConfig(
                    true,
                    Pack.Position.TOP,
                    false
            );

            Pack pack = Pack.readMetaAndCreate(
                    location,
                    supplier,
                    PackType.CLIENT_RESOURCES,
                    selectionConfig
            );

            VulkanPostFX.LOGGER.info(
                    "[{}] Injected runtime zip resource pack: id={}, root={}, packCreated={}",
                    VulkanPostFX.MOD_ID,
                    packId,
                    root,
                    pack != null
            );

            return pack;
        });
    }
}