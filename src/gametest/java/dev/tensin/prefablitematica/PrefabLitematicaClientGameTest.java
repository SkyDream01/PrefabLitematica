// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica;

import dev.tensin.prefablitematica.client.screen.BlueprintWorkbenchScreen;
import dev.tensin.prefablitematica.integration.litematica.LitematicaIntegration;
import dev.tensin.prefablitematica.blueprint.BlueprintData;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.nbt.*;
import java.lang.reflect.Method;

/** Runs with the real optional Litematica jars via -PwithLitematica. */
public final class PrefabLitematicaClientGameTest implements FabricClientGameTest {
    private static int expectedPlacementCount;
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var position = world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst(); var pos = player.blockPosition().below();
                player.level().setBlockAndUpdate(pos, PrefabLitematicaMod.WORKBENCH.defaultBlockState());
                var bench = (dev.tensin.prefablitematica.block.entity.BlueprintWorkbenchBlockEntity) player.level().getBlockEntity(pos);
                bench.setItem(0, new ItemStack(PrefabLitematicaMod.BLANK_BLUEPRINT)); player.openMenu(bench); return pos;
            });
            context.waitForScreen(BlueprintWorkbenchScreen.class);
            keepScreenshot(context, "blueprint-workbench-empty");
            if (LitematicaIntegration.available()) {
                context.runOnClient(client -> fixture());
                context.clickScreenButton("Load building");
                context.waitFor(client -> client.gui.screen() instanceof BlueprintWorkbenchScreen screen && screen.children().stream()
                        .anyMatch(widget -> widget instanceof net.minecraft.client.gui.components.Button button && button.getMessage().getString().equals("unloaded.litematic")));
                keepScreenshot(context, "blueprint-building-list");
                context.clickScreenButton("unloaded.litematic");
                world.getServer().waitFor(server -> {
                    var bench = (dev.tensin.prefablitematica.block.entity.BlueprintWorkbenchBlockEntity) server.getPlayerList().getPlayers().getFirst().level().getBlockEntity(position);
                    return bench != null && bench.blueprint() != null;
                });
                world.getConnection().waitForClientboundPackets(); context.waitTicks(4);
                keepScreenshot(context, "blueprint-workbench-imported");
                world.getServer().runOnServer(server -> {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    var bench = (dev.tensin.prefablitematica.block.entity.BlueprintWorkbenchBlockEntity) player.level().getBlockEntity(position);
                    var data = bench.blueprint();
                    if (!bench.getItem(0).is(PrefabLitematicaMod.BLUEPRINT) || dev.tensin.prefablitematica.item.BlueprintItem.isBlank(bench.getItem(0)) || data == null || data.blocks.size() != 3)
                        throw new AssertionError("Server must convert blank input to a loaded building item");
                    if (data.blocks.get(1).blockEntity() != null || data.blocks.get(2).blockEntity() == null || !data.blocks.get(2).blockEntity().isEmpty())
                        throw new AssertionError("Stale stone NBT and chest contents must not survive import");
                    player.getInventory().setItem(9, new ItemStack(PrefabLitematicaMod.BATTERY, 2));
                });
                world.getConnection().waitForClientboundPackets();
                // Exercise the normal material entrance via the same quick-move path as shift-click.
                world.getServer().runOnServer(server -> {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    ((dev.tensin.prefablitematica.screen.BlueprintWorkbenchScreenHandler) player.containerMenu).quickMoveStack(player, dev.tensin.prefablitematica.screen.BlueprintWorkbenchScreenHandler.PLAYER_START);
                    player.containerMenu.broadcastChanges();
                });
                world.getConnection().waitForClientboundPackets(); context.clickScreenButton("Charge");
                world.getServer().waitFor(server -> {
                    var bench = (dev.tensin.prefablitematica.block.entity.BlueprintWorkbenchBlockEntity) server.getPlayerList().getPlayers().getFirst().level().getBlockEntity(position);
                    return bench != null && bench.blueprint().fullyCharged();
                });
                world.getConnection().waitForClientboundPackets(); context.waitTicks(4);
                keepScreenshot(context, "blueprint-workbench-charged");
                context.runOnClient(client -> {
                    Object manager = Class.forName("fi.dy.masa.litematica.data.DataManager").getMethod("getSchematicPlacementManager").invoke(null);
                    Object selected = manager.getClass().getMethod("getSelectedSchematicPlacement").invoke(manager);
                    if (selected != null) throw new AssertionError("File import must not select or spawn a Litematica projection");
                    if (((java.util.Collection<?>) manager.getClass().getMethod("getAllSchematicsPlacements").invoke(manager)).size() != expectedPlacementCount)
                        throw new AssertionError("File import must not add a world projection");
                });
                var reload = context.computeOnClient(client -> {
                    client.options.languageCode = "zh_cn"; client.getLanguageManager().setSelected("zh_cn"); return client.reloadResourcePacks();
                });
                context.waitFor(client -> reload.isDone() && client.gui.overlay() == null && client.gui.screen() instanceof BlueprintWorkbenchScreen);
                world.getServer().runOnServer(server -> {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    dev.tensin.prefablitematica.network.BlueprintNetworking.sendStatus(player, (dev.tensin.prefablitematica.screen.BlueprintWorkbenchScreenHandler) player.containerMenu, "Imported");
                });
                world.getConnection().waitForClientboundPackets(); context.waitTicks(4); keepScreenshot(context, "blueprint-workbench-charged-zh-cn");
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    private static void keepScreenshot(ClientGameTestContext context, String name) throws java.io.IOException {
        var source = context.takeScreenshot(name);
        var directory = java.nio.file.Path.of(System.getProperty("prefablitematica.projectDir"), "docs", "screenshots");
        java.nio.file.Files.createDirectories(directory); java.nio.file.Files.copy(source, directory.resolve(name + ".png"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
    private static void fixture() throws Exception {
        Class<?> areaType = Class.forName("fi.dy.masa.litematica.selection.AreaSelection");
        Class<?> boxType = Class.forName("fi.dy.masa.litematica.selection.Box");
        Class<?> schematicType = Class.forName("fi.dy.masa.litematica.schematic.LitematicaSchematic");
        Class<?> placementType = Class.forName("fi.dy.masa.litematica.schematic.placement.SchematicPlacement");
        Object area = areaType.getConstructor().newInstance();
        areaType.getMethod("setName", String.class).invoke(area, "Unloaded house");
        Object box = boxType.getConstructor(BlockPos.class, BlockPos.class, String.class).newInstance(BlockPos.ZERO, new BlockPos(2,0,0), "test");
        areaType.getMethod("addSubRegionBox", boxType, boolean.class).invoke(area, box, true);
        Object schematic = schematicType.getMethod("createEmptySchematic",areaType,String.class).invoke(null,area,"Test blueprint");
        Object container = schematicType.getMethod("getSubRegionContainer",String.class).invoke(schematic,"test");
        Method set = container.getClass().getMethod("set",int.class,int.class,int.class,net.minecraft.world.level.block.state.BlockState.class);
        set.invoke(container,0,0,0,Blocks.OAK_PLANKS.defaultBlockState()); set.invoke(container,1,0,0,Blocks.STONE.defaultBlockState()); set.invoke(container,2,0,0,Blocks.CHEST.defaultBlockState());
        var raw = TagParser.parseCompoundFully("{id:'minecraft:chest',x:1,y:0,z:0,Items:[{id:'minecraft:diamond',count:64}]}");
        var convert = Class.forName("fi.dy.masa.malilib.util.data.tag.converter.DataConverterNbt").getMethod("fromVanillaCompound", CompoundTag.class);
        Object converted = convert.invoke(null, raw); raw = raw.copy(); raw.putInt("x", 2); Object chestNbt = convert.invoke(null, raw);
        @SuppressWarnings("unchecked") var tiles = (java.util.Map<BlockPos, Object>) schematicType.getMethod("getBlockEntityMapForRegion", String.class).invoke(schematic, "test");
        tiles.put(new BlockPos(1,0,0), converted); tiles.put(new BlockPos(2,0,0), chestNbt);
        var directory = java.nio.file.Files.createTempDirectory(LitematicaIntegration.schematicDirectory(), "prefablitematica-test-");
        boolean written = (boolean) schematicType.getMethod("writeToFile", java.nio.file.Path.class, String.class, boolean.class).invoke(schematic, directory, "unloaded.litematic", true);
        if (!written) throw new AssertionError("Cannot save unloaded schematic fixture");
        var path = directory.resolve("unloaded.litematic");
        var fileSource = new LitematicaIntegration.Source("unloaded.litematic", path.toString(), LitematicaIntegration.SourceKind.FILE, null, null, path);
        var capture = new LitematicaIntegration.Capture(fileSource); while (!capture.tick(100)) {} BlueprintData captured = capture.finish();
        if (captured.blocks.size() != 3 || captured.sizeX != 3 || !captured.blocks.getFirst().state().is(Blocks.OAK_PLANKS)) throw new AssertionError("Unloaded file capture failed");
        Object placement = placementType.getMethod("createFor",schematicType,BlockPos.class,String.class,boolean.class,boolean.class).invoke(null,schematic,BlockPos.ZERO,"Disabled house",false,false);
        Object manager = Class.forName("fi.dy.masa.litematica.data.DataManager").getMethod("getSchematicPlacementManager").invoke(null);
        manager.getClass().getMethod("addSchematicPlacement",placementType,boolean.class).invoke(manager,placement,false);
        manager.getClass().getMethod("setSelectedSchematicPlacement",placementType).invoke(manager,(Object)null);
        var disabled = LitematicaIntegration.memorySources().stream().filter(source -> source.placement() == placement).findFirst().orElseThrow();
        var disabledCapture = new LitematicaIntegration.Capture(disabled); while (!disabledCapture.tick(100)) {}
        if (disabledCapture.finish().blocks.size() != 3 || (boolean) placementType.getMethod("isEnabled").invoke(placement)) throw new AssertionError("Disabled placement import must not enable its world projection");
        boolean memoryWritten = (boolean) schematicType.getMethod("writeToFile", java.nio.file.Path.class, String.class, boolean.class).invoke(schematic, directory, "memory.litematic", true);
        if (!memoryWritten) throw new AssertionError("Cannot save loaded schematic fixture");
        Object holder = Class.forName("fi.dy.masa.litematica.data.SchematicHolder").getMethod("getInstance").invoke(null);
        Object memory = holder.getClass().getMethod("getOrLoad", java.nio.file.Path.class).invoke(holder, directory.resolve("memory.litematic"));
        var memorySource = LitematicaIntegration.memorySources().stream().filter(source -> source.schematic() == memory).findFirst().orElseThrow();
        var memoryCapture = new LitematicaIntegration.Capture(memorySource); while (!memoryCapture.tick(100)) {}
        if (memoryCapture.finish().blocks.size() != 3) throw new AssertionError("Loaded schematic without projection must be importable");
        expectedPlacementCount = ((java.util.Collection<?>) manager.getClass().getMethod("getAllSchematicsPlacements").invoke(manager)).size();
    }
}
