// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica;

import com.google.gson.JsonObject;
import dev.tensin.prefablitematica.block.entity.BlueprintWorkbenchBlockEntity;
import dev.tensin.prefablitematica.blueprint.*;
import dev.tensin.prefablitematica.client.screen.BlueprintWorkbenchScreen;
import dev.tensin.prefablitematica.item.BlueprintItem;
import dev.tensin.prefablitematica.network.BlueprintNetworking;
import dev.tensin.prefablitematica.screen.BlueprintWorkbenchScreenHandler;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import java.nio.file.*;
import java.util.*;

/** Verifies paginated material ordering and both 3x3 grids through the real client. */
public final class WorkbenchLayoutClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var position = world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst(); var pos = player.blockPosition().below();
                player.level().setBlockAndUpdate(pos, PrefabLitematicaMod.WORKBENCH.defaultBlockState());
                var states = List.of(Blocks.STONE, Blocks.OAK_PLANKS, Blocks.DIRT, Blocks.GLASS, Blocks.TORCH, Blocks.REDSTONE_WIRE);
                var blocks = new ArrayList<BlueprintBlock>();
                for (int x = 0; x < states.size(); x++) blocks.add(new BlueprintBlock(new BlockPos(x, 0, 0), states.get(x).defaultBlockState(), null));
                var data = new BlueprintData(UUID.randomUUID(), "Materials and returns", 6, 1, 1, blocks, new LinkedHashMap<>());
                var manager = PrefabLitematicaMod.manager(server); var analysis = manager.new Analysis(data); while (!analysis.tick(4096)) {}
                var requirements = new ArrayList<>(data.requirements.values()); requirements.get(0).offer(1); requirements.get(1).offer(1); manager.create(data);
                var bench = (BlueprintWorkbenchBlockEntity) player.level().getBlockEntity(pos); bench.setItem(0, BlueprintItem.loaded(data));
                var materials = new ItemStack(Items.SHULKER_BOX);
                materials.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIRT), new ItemStack(Items.DIAMOND, 3))));
                bench.setItem(1, materials); bench.setItem(2, new ItemStack(Items.STONE, 4096)); bench.setItem(3, new ItemStack(Items.OAK_PLANKS, 4096));
                bench.setItem(11, new ItemStack(Items.SHULKER_BOX)); bench.setItem(12, new ItemStack(Items.BUCKET, 16));
                var last = new ItemStack(Items.SHULKER_BOX); last.set(DataComponents.CUSTOM_NAME, Component.literal("Last return")); bench.setItem(19, last);
                player.getInventory().setItem(0, new ItemStack(PrefabLitematicaMod.BLANK_BLUEPRINT));
                player.getInventory().setItem(1, BlueprintItem.loaded(data));
                player.getInventory().setItem(2, new ItemStack(PrefabLitematicaMod.BATTERY));
                player.getInventory().setItem(3, new ItemStack(PrefabLitematicaMod.WORKBENCH));
                player.openMenu(bench); return pos;
            });
            context.waitForScreen(BlueprintWorkbenchScreen.class);
            context.waitFor(client -> status((BlueprintWorkbenchScreen) client.gui.screen()).has("materials"));
            context.runOnClient(client -> {
                var screen = (BlueprintWorkbenchScreen) client.gui.screen(); var rows = status(screen).getAsJsonArray("materials");
                if (status(screen).get("pages").getAsInt() != 2 || !rows.get(0).getAsJsonObject().get("item").getAsString().equals("minecraft:dirt")) throw new AssertionError("Outstanding dirt must precede completed stone and wood");
                for (int row = 0; row < 4; row++) if (rows.get(row).getAsJsonObject().get("remaining").getAsInt() == 0) throw new AssertionError("Outstanding materials must occupy the first four rows");
                if (rows.get(4).getAsJsonObject().get("remaining").getAsInt() != 0 || client.player.containerMenu.slots.size() != 55 || !client.player.containerMenu.getSlot(18).getItem().getHoverName().getString().equals("Last return")) throw new AssertionError("The completed tail and ninth return must synchronize");
            });
            context.waitTicks(3); keepScreenshot(context, "blueprint-3x3-returns-material-icons");
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst(); var menu = (BlueprintWorkbenchScreenHandler) player.containerMenu;
                menu.page = 1; BlueprintNetworking.sendStatus(player, menu, "");
            });
            context.waitFor(client -> status((BlueprintWorkbenchScreen) client.gui.screen()).get("page").getAsInt() == 1);
            context.runOnClient(client -> {
                var rows = status((BlueprintWorkbenchScreen) client.gui.screen()).getAsJsonArray("materials");
                if (rows.size() != 1 || !rows.get(0).getAsJsonObject().get("item").getAsString().equals("minecraft:oak_planks")) throw new AssertionError("Completed wood must move to the second page");
            });
            var chargeLabel = context.computeOnClient(client -> Component.translatable("gui.prefablitematica.charge").getString()); context.clickScreenButton(chargeLabel);
            context.waitFor(client -> {
                var updated = status((BlueprintWorkbenchScreen) client.gui.screen());
                return updated.get("page").getAsInt() == 0 && updated.getAsJsonArray("materials").get(0).getAsJsonObject().get("item").getAsString().equals("minecraft:glass");
            });
            context.waitFor(client -> client.player.containerMenu.getSlot(1).getItem().isEmpty() && client.player.containerMenu.getSlot(12).getItem().is(Items.SHULKER_BOX));
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst(); var bench = (BlueprintWorkbenchBlockEntity) player.level().getBlockEntity(position);
                if (bench.getItem(2).getCount() != 4096 || bench.getItem(3).getCount() != 4096 || bench.getItem(13).get(DataComponents.CONTAINER).nonEmptyItemCopyStream().filter(stack -> stack.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum() != 3) throw new AssertionError("Completed inputs and unused boxed contents must remain intact");
            });
            context.waitTicks(3); keepScreenshot(context, "blueprint-materials-resorted-after-charge");
            context.runOnClient(client -> client.gameMode.handleContainerInput(client.player.containerMenu.containerId, BlueprintWorkbenchScreenHandler.RETURN_END - 1, 0, ContainerInput.QUICK_MOVE, client.player));
            world.getServer().waitFor(server -> ((BlueprintWorkbenchBlockEntity) server.getPlayerList().getPlayers().getFirst().level().getBlockEntity(position)).getItem(19).isEmpty());
            context.waitFor(client -> client.player.containerMenu.getSlot(18).getItem().isEmpty());
            // Inspect the custom block beside vanilla workstations in the actual world renderer.
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst(); player.closeContainer();
                var display = position.above();
                player.level().setBlockAndUpdate(display, PrefabLitematicaMod.WORKBENCH.defaultBlockState());
                player.level().setBlockAndUpdate(display.west(2), Blocks.CRAFTING_TABLE.defaultBlockState());
                player.level().setBlockAndUpdate(display.east(2), Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
                player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                player.teleportTo(player.level(), position.getX() + 3.5, position.getY() + 2.5,
                        position.getZ() - 4.5, Set.of(), 31, 24, true);
            });
            world.getConnection().waitForClientboundPackets();
            context.waitFor(client -> client.gui.screen() == null && client.player.isSpectator());
            context.waitTicks(15);
            boolean wasHidden = context.computeOnClient(client -> { boolean previous = client.gui.hud.isHidden(); if (!previous) client.gui.hud.toggle(); return previous; });
            try { context.waitTicks(3); keepScreenshot(context, "blueprint-workbench-vanilla-style"); }
            finally { context.runOnClient(client -> { if (client.gui.hud.isHidden() != wasHidden) client.gui.hud.toggle(); }); }
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    private static JsonObject status(BlueprintWorkbenchScreen screen) {
        try { var field = BlueprintWorkbenchScreen.class.getDeclaredField("status"); field.setAccessible(true); return (JsonObject) field.get(screen); }
        catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }
    private static void keepScreenshot(ClientGameTestContext context, String name) throws java.io.IOException {
        var source = context.takeScreenshot(name); var directory = Path.of(System.getProperty("prefablitematica.projectDir"), "docs", "screenshots");
        Files.createDirectories(directory); Files.copy(source, directory.resolve(name + ".png"), StandardCopyOption.REPLACE_EXISTING);
    }
}
