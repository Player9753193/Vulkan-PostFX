package com.ionhex975.vulkanpostfx;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VulkanPostFX implements ModInitializer {
	public static final String MOD_ID = "vulkanpostfx";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("[{}] Common bootstrap initialized", MOD_ID);
	}
}
