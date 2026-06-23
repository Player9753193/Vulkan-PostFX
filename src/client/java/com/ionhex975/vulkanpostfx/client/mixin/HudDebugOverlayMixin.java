package com.ionhex975.vulkanpostfx.client.mixin;

import com.ionhex975.vulkanpostfx.client.debug.VpfxDebugHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudDebugOverlayMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void vulkanpostfx$appendVpfxDebugHud(
			GuiGraphicsExtractor graphics,
			DeltaTracker deltaTracker,
			CallbackInfo ci
	) {
		VpfxDebugHud.render(graphics);
	}
}
