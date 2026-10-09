// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.client;

import dev.tensin.prefablitematica.PrefabLitematicaMod;
import dev.tensin.prefablitematica.client.screen.BlueprintWorkbenchScreen;
import dev.tensin.prefablitematica.network.BlueprintPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;

public final class PrefabLitematicaClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        BlueprintProjectionClient.register();
        MenuScreens.register(PrefabLitematicaMod.WORKBENCH_MENU, BlueprintWorkbenchScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(BlueprintPayload.Response.TYPE, (response, context) -> {
            if (context.client().gui.screen() instanceof BlueprintWorkbenchScreen screen && screen.getMenu().containerId == response.value().syncId()) screen.receive(response.value());
        });
    }
}
