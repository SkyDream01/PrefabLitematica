// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica;

import dev.tensin.prefablitematica.block.entity.BlueprintWorkbenchBlockEntity;
import dev.tensin.prefablitematica.blueprint.*;
import dev.tensin.prefablitematica.client.screen.BlueprintWorkbenchScreen;
import dev.tensin.prefablitematica.item.BlueprintItem;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import java.nio.file.*;
import java.util.*;

/** Exercises client prediction and real container packets for oversized material inputs. */
public final class MaterialInputClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.restoreDefaultGameOptions();
        try (var world = context.worldBuilder().create()) {
            var position = world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst(); var pos = player.blockPosition().below();
                player.level().setBlockAndUpdate(pos, PrefabLitematicaMod.WORKBENCH.defaultBlockState());
                var blocks = new ArrayList<BlueprintBlock>();
                for (int x = 0; x < 128; x++) blocks.add(new BlueprintBlock(new BlockPos(x, 0, 0), Blocks.STONE.defaultBlockState(), null));
                blocks.add(new BlueprintBlock(new BlockPos(128, 0, 0), Blocks.WATER.defaultBlockState(), null));
                var data = new BlueprintData(UUID.randomUUID(), "Bulk materials", 129, 1, 1, blocks, new LinkedHashMap<>());
                var manager = PrefabLitematicaMod.manager(server); var analysis = manager.new Analysis(data); while (!analysis.tick(4096)) {}
                manager.create(data); var bench = (BlueprintWorkbenchBlockEntity) player.level().getBlockEntity(pos);
                bench.setItem(0, BlueprintItem.loaded(data)); bench.setItem(1, new ItemStack(Items.STONE, 64));
                player.getInventory().setItem(9, new ItemStack(Items.STONE, 64)); player.openMenu(bench); return pos;
            });
            context.waitForScreen(BlueprintWorkbenchScreen.class); world.getConnection().waitForClientboundPackets();
            context.runOnClient(client -> client.gameMode.handleContainerInput(client.player.containerMenu.containerId, dev.tensin.prefablitematica.screen.BlueprintWorkbenchScreenHandler.PLAYER_START, 0, ContainerInput.QUICK_MOVE, client.player));
            world.getServer().waitFor(server -> ((BlueprintWorkbenchBlockEntity) server.getPlayerList().getPlayers().getFirst().level().getBlockEntity(position)).getItem(1).getCount() == 128);
            context.waitFor(client -> client.player.containerMenu.getSlot(1).getItem().getCount() == 128);
            keepScreenshot(context, "blueprint-material-input-128");
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst(); var bench = (BlueprintWorkbenchBlockEntity) player.level().getBlockEntity(position);
                bench.setItem(1, new ItemStack(Items.STONE, 4032)); player.getInventory().setItem(9, new ItemStack(Items.STONE, 64));
                var box = new ItemStack(Items.SHULKER_BOX);
                box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.DIAMOND, 7))));
                bench.setItem(2, box);
                for (int slot = 3; slot < 10; slot++) bench.setItem(slot, new ItemStack(Items.STONE, 4096));
                player.containerMenu.broadcastChanges();
            });
            world.getConnection().waitForClientboundPackets();
            context.waitFor(client -> client.player.containerMenu.getSlot(1).getItem().getCount() == 4032);
            context.runOnClient(client -> client.gameMode.handleContainerInput(client.player.containerMenu.containerId, dev.tensin.prefablitematica.screen.BlueprintWorkbenchScreenHandler.PLAYER_START, 0, ContainerInput.QUICK_MOVE, client.player));
            context.waitFor(client -> client.player.containerMenu.getSlot(1).getItem().getCount() == 4096);
            world.getServer().waitFor(server -> ((BlueprintWorkbenchBlockEntity) server.getPlayerList().getPlayers().getFirst().level().getBlockEntity(position)).getItem(1).getCount() == 4096);
            context.waitTicks(3); keepScreenshot(context, "blueprint-material-input-4096");
            context.runOnClient(client -> client.gameMode.handleContainerInput(client.player.containerMenu.containerId, 1, 0, ContainerInput.PICKUP, client.player));
            context.waitFor(client -> client.player.containerMenu.getCarried().getCount() == 64 && client.player.containerMenu.getSlot(1).getItem().getCount() == 4032);
            context.runOnClient(client -> client.gameMode.handleContainerInput(client.player.containerMenu.containerId, 1, 0, ContainerInput.PICKUP, client.player));
            context.waitFor(client -> client.player.containerMenu.getCarried().isEmpty() && client.player.containerMenu.getSlot(1).getItem().getCount() == 4096);
            var chargeLabel = context.computeOnClient(client -> net.minecraft.network.chat.Component.translatable("gui.prefablitematica.charge").getString());
            context.clickScreenButton(chargeLabel);
            world.getServer().waitFor(server -> ((BlueprintWorkbenchBlockEntity) server.getPlayerList().getPlayers().getFirst().level().getBlockEntity(position)).blueprint().fullyCharged());
            context.waitFor(client -> client.player.containerMenu.getSlot(1).getItem().getCount() == 3968 && client.player.containerMenu.getSlot(2).getItem().isEmpty() && client.player.containerMenu.getSlot(10).getItem().getCount() == 2 && client.player.containerMenu.getSlot(11).getItem().is(Items.SHULKER_BOX));
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst(); var bench = (BlueprintWorkbenchBlockEntity) player.level().getBlockEntity(position);
                if (!bench.getItem(11).is(Items.BUCKET)) throw new AssertionError("Boxed water buckets must return to output");
                ItemStack returned = bench.getItem(12);
                if (returned.isEmpty() || returned.get(DataComponents.CONTAINER).nonEmptyItemCopyStream().filter(stack -> stack.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum() != 7)
                    throw new AssertionError("The returned box must preserve all seven unrelated diamonds");
            });
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    private static void keepScreenshot(ClientGameTestContext context, String name) throws java.io.IOException {
        var source = context.takeScreenshot(name); var directory = Path.of(System.getProperty("prefablitematica.projectDir"), "docs", "screenshots");
        Files.createDirectories(directory); Files.copy(source, directory.resolve(name + ".png"), StandardCopyOption.REPLACE_EXISTING);
    }
}
