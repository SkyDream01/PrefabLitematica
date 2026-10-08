// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica;

import dev.tensin.prefablitematica.block.entity.BlueprintWorkbenchBlockEntity;
import dev.tensin.prefablitematica.client.screen.BlueprintWorkbenchScreen;
import dev.tensin.prefablitematica.integration.litematica.LitematicaIntegration;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import java.nio.file.Files;

/** Regression for the user-provided schematic through the real file picker and network import. */
public final class SwampSchematicClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        if (!LitematicaIntegration.available()) throw new AssertionError("Run this regression with -PwithLitematica");
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                var directory = Files.createTempDirectory(LitematicaIntegration.schematicDirectory(), "blueprint-swamp-test-");
                try (var input = getClass().getResourceAsStream("/prefablitematica-test/swamp-mob-farm.litematic")) {
                    if (input == null) throw new AssertionError("Missing swamp schematic regression fixture");
                    Files.copy(input, directory.resolve("swamp.litematic"));
                }
            });
            var position = world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst(); var pos = player.blockPosition().below();
                player.level().setBlockAndUpdate(pos, PrefabLitematicaMod.WORKBENCH.defaultBlockState());
                var bench = (BlueprintWorkbenchBlockEntity) player.level().getBlockEntity(pos);
                bench.setItem(0, new ItemStack(PrefabLitematicaMod.BLANK_BLUEPRINT)); player.openMenu(bench); return pos;
            });
            context.waitForScreen(BlueprintWorkbenchScreen.class);
            var loadLabel = context.computeOnClient(client -> net.minecraft.network.chat.Component.translatable("gui.prefablitematica.import").getString());
            context.clickScreenButton(loadLabel);
            context.runOnClient(client -> ((BlueprintWorkbenchScreen) client.gui.screen()).children().stream()
                    .filter(widget -> widget instanceof net.minecraft.client.gui.components.EditBox)
                    .map(widget -> (net.minecraft.client.gui.components.EditBox) widget).findFirst().orElseThrow().setValue("swamp.litematic"));
            context.waitFor(client -> client.gui.screen() instanceof BlueprintWorkbenchScreen screen && screen.children().stream()
                    .anyMatch(widget -> widget instanceof net.minecraft.client.gui.components.Button button && button.getMessage().getString().equals("swamp.litematic")));
            context.clickScreenButton("swamp.litematic");
            world.getServer().waitFor(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var bench = (BlueprintWorkbenchBlockEntity) player.level().getBlockEntity(position);
                return bench != null && bench.blueprint() != null;
            });
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst(); var bench = (BlueprintWorkbenchBlockEntity) player.level().getBlockEntity(position);
                var data = bench.blueprint();
                if (data.blocks.size() != 6047 || data.sizeX != 52 || data.sizeY != 131 || data.sizeZ != 37)
                    throw new AssertionError("Swamp schematic must import completely");
                if (data.blocks.stream().filter(block -> block.state().is(Blocks.BUBBLE_COLUMN)).count() != 7)
                    throw new AssertionError("All seven bubble columns must survive import");
                if (data.requirements.get("item:minecraft:water_bucket").required != 2
                        || data.requirements.get("item:minecraft:soul_sand").required != 1
                        || data.requirements.containsKey("item:minecraft:bubble_column"))
                    throw new AssertionError("Swamp bubble columns must charge only water and their soul-sand base");
                bench.setItem(1, new ItemStack(PrefabLitematicaMod.BATTERY)); player.containerMenu.broadcastChanges();
            });
            world.getConnection().waitForClientboundPackets(); context.waitTicks(4);
            var source = context.takeScreenshot("blueprint-swamp-mob-farm-imported");
            Files.copy(source, java.nio.file.Path.of(System.getProperty("prefablitematica.projectDir"), "docs", "screenshots", "blueprint-swamp-mob-farm-imported.png"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            var chargeLabel = context.computeOnClient(client -> net.minecraft.network.chat.Component.translatable("gui.prefablitematica.charge").getString());
            context.clickScreenButton(chargeLabel);
            world.getServer().waitFor(server -> {
                var bench = (BlueprintWorkbenchBlockEntity) server.getPlayerList().getPlayers().getFirst().level().getBlockEntity(position);
                return bench != null && bench.blueprint().fullyCharged();
            });
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
