package com.customcrosshair;

import net.fabricmc.api.ClientModInitializer;

public final class CustomCrosshair implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CrosshairConfig.INSTANCE.load();
    }
}
