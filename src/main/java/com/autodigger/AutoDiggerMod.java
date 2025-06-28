package com.autodigger;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoDiggerMod implements ModInitializer {
    public static final String MOD_ID = "auto-digger";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Auto Digger mod инициализируется!");
    }
} 