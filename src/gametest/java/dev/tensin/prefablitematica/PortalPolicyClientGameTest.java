// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica;

import dev.tensin.prefablitematica.block.entity.BlueprintWorkbenchBlockEntity;
import dev.tensin.prefablitematica.blueprint.*;
import dev.tensin.prefablitematica.client.screen.BlueprintWorkbenchScreen;
import dev.tensin.prefablitematica.integration.litematica.LitematicaIntegration;
import dev.tensin.prefablitematica.item.BlueprintItem;
import dev.tensin.prefablitematica.placement.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;
import java.nio.file.*;
import java.util.*;

/** Real Litematica capture, UI/network import and native paste for portal and preserved-block policies. */
public final class PortalPolicyClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        if (!LitematicaIntegration.available()) throw new AssertionError("Run with -PwithLitematica");
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                for (String language : List.of("en_us", "zh_cn")) {
                    var resource = getClass().getResourceAsStream("/assets/prefablitematica/lang/" + language + ".json");
                    if (resource == null) throw new AssertionError("Missing language resources");
                    try (resource) {
                        var translations = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(resource, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                        for (String key : List.of("gui.prefablitematica.portal_ignite_short", "gui.prefablitematica.portal_slicing_short"))
                            if (client.font.split(Component.literal(translations.get(key).getAsString()), 114).size() > 2)
                                throw new AssertionError("Portal action must fit the two visible status lines in " + language);
                    }
                }
                for (Direction.Axis axis : List.of(Direction.Axis.X, Direction.Axis.Z)) {
                    var normal = capture(fixture(axis, 2, 3, false, false), "normal");
                    if (!normal.portalNeedsIgnition || normal.portalNeedsSlicing || normal.blocks.stream().filter(b -> b.state().isAir()).count() != 6)
                        throw new AssertionError("Complete cornerless portal must clear six cells and require ignition on " + axis);
                    var sliced = capture(fixture(axis, 2, 3, true, false), "sliced");
                    if (!sliced.portalNeedsIgnition || !sliced.portalNeedsSlicing) throw new AssertionError("Missing frame must require manual slicing");
                    for (int[] shape : List.of(new int[]{1, 3}, new int[]{2, 2}, new int[]{22, 3})) {
                        if (!capture(fixture(axis, shape[0], shape[1], false, false), "unsupported").portalNeedsSlicing)
                            throw new AssertionError("Unsupported portal dimensions must require manual handling");
                    }
                }
                var schem = fixture(Direction.Axis.X, 2, 3, false, true);
                var captured = capture(schem, "ticks");
                if (!captured.scheduledTicks.isEmpty()) throw new AssertionError("Portal ticks must be removed with their portal states");
                var placementType = Class.forName("fi.dy.masa.litematica.schematic.placement.SchematicPlacement");
                var placement = placementType.getMethod("createTemporary", schem.getClass(), BlockPos.class).invoke(null, schem, BlockPos.ZERO);
                var messages = Class.forName("fi.dy.masa.malilib.gui.interfaces.IMessageConsumer");
                placementType.getMethod("setRotation", Rotation.class, messages).invoke(placement, Rotation.CLOCKWISE_90, null);
                placementType.getMethod("setMirror", Mirror.class, messages).invoke(placement, Mirror.FRONT_BACK, null);
                var moved = new LitematicaIntegration.Capture(new LitematicaIntegration.Source("Rotated portal", "", LitematicaIntegration.SourceKind.PLACEMENT, placement, null, null));
                while (!moved.tick(16)) {}
                if (moved.finish().portalNeedsSlicing) throw new AssertionError("Rotated and mirrored complete portal must retain a valid frame");
            });
            context.runOnClient(client -> {
                var dir = Files.createTempDirectory(LitematicaIntegration.schematicDirectory(), "portal-policy-test-");
                write(fixture(Direction.Axis.X, 2, 3, false, true), dir, "portal.litematic");
                write(fixture(Direction.Axis.Z, 2, 3, true, false), dir, "sliced.litematic");
            });
            var benchPos = world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst(); var pos = player.blockPosition().below();
                player.level().setBlockAndUpdate(pos, PrefabLitematicaMod.WORKBENCH.defaultBlockState());
                var bench = (BlueprintWorkbenchBlockEntity) player.level().getBlockEntity(pos); bench.setItem(0, new ItemStack(PrefabLitematicaMod.BLANK_BLUEPRINT));
                player.openMenu(bench); return pos;
            });
            importFile(context, "portal.litematic");
            world.getServer().waitFor(server -> ((BlueprintWorkbenchBlockEntity) server.getPlayerList().getPlayers().getFirst().level().getBlockEntity(benchPos)).blueprint() != null);
            world.getConnection().waitForClientboundPackets();
            context.waitFor(client -> notice(client.gui.screen()).equals(Component.translatable("gui.prefablitematica.portal_ignite").getString()));
            keepScreenshot(context, "blueprint-portal-ignite");
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst(); var bench = (BlueprintWorkbenchBlockEntity) player.level().getBlockEntity(benchPos); var data = bench.blueprint();
                if (!data.portalNeedsIgnition || data.portalNeedsSlicing || data.requirements.size() != 2
                        || data.requirements.get("item:minecraft:obsidian").required != 10 || data.scheduledTicks.size() != 0)
                    throw new AssertionError("Portal warnings, exemptions and frame cost must survive network import");
                var restored = new BlueprintManager(server).get(data.id);
                if (restored == null || !restored.portalNeedsIgnition || restored.portalNeedsSlicing) throw new AssertionError("Portal warnings must persist");
                bench.setItem(1, new ItemStack(PrefabLitematicaMod.BATTERY)); player.containerMenu.broadcastChanges();
            });
            world.getConnection().waitForClientboundPackets();
            context.clickScreenButton(context.computeOnClient(client -> Component.translatable("gui.prefablitematica.charge").getString()));
            world.getServer().waitFor(server -> ((BlueprintWorkbenchBlockEntity) server.getPlayerList().getPlayers().getFirst().level().getBlockEntity(benchPos)).blueprint().fullyCharged());
            var previewOrigin = world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst(); var bench = (BlueprintWorkbenchBlockEntity) player.level().getBlockEntity(benchPos);
                var origin = new BlockPos(player.blockPosition().getX() + 16, 100, player.blockPosition().getZ() + 16);
                for (int x = origin.getX() >> 4; x <= (origin.getX() + bench.blueprint().sizeX - 1) >> 4; x++) player.level().getChunk(x, origin.getZ() >> 4);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, bench.getItem(0).copy()); player.closeContainer(); return origin;
            });
            context.waitForScreen(null); context.waitFor(client -> BlueprintItem.id(client.player.getMainHandItem()) != null);
            context.getInput().pressKey(dev.tensin.prefablitematica.client.BlueprintProjectionClient.OPEN_UI);
            context.waitForScreen(dev.tensin.prefablitematica.client.screen.BlueprintProjectionScreen.class);
            context.runOnClient(client -> {
                var axes = client.gui.screen().children().stream().filter(w -> w instanceof net.minecraft.client.gui.components.EditBox)
                        .map(w -> (net.minecraft.client.gui.components.EditBox) w).toList();
                axes.get(0).setValue(Integer.toString(previewOrigin.getX())); axes.get(1).setValue(Integer.toString(previewOrigin.getY())); axes.get(2).setValue(Integer.toString(previewOrigin.getZ()));
            });
            context.waitFor(client -> dev.tensin.prefablitematica.client.BlueprintProjectionClient.INSTANCE.origin().equals(previewOrigin));
            context.clickScreenButton("projection.prefablitematica.show");
            context.waitFor(client -> dev.tensin.prefablitematica.client.BlueprintProjectionClient.INSTANCE.litematicaVisible()
                    && dev.tensin.prefablitematica.client.BlueprintProjectionClient.INSTANCE.canConfirm());
            context.runOnClient(client -> {
                var preview = dev.tensin.prefablitematica.client.BlueprintProjectionClient.INSTANCE; var data = preview.data();
                if (data.blocks.stream().filter(b -> BlueprintBlockPolicy.isPreserved(b.state())).count() != 8
                        || data.blocks.stream().filter(b -> b.state().isAir()).count() != 6) throw new AssertionError("Projection network must retain preserved cells and portal air");
                var placement = preview.projection().placement(); var schematic = placement.getClass().getMethod("getSchematic").invoke(placement);
                var container = schematic.getClass().getMethod("getSubRegionContainer", String.class).invoke(schematic, "blueprint");
                var get = container.getClass().getMethod("get", int.class, int.class, int.class);
                for (var block : data.blocks) {
                    var pos = block.relativePos();
                    if (get.invoke(container, pos.getX(), pos.getY(), pos.getZ()) != block.state()) throw new AssertionError("Actual Litematica projection must display each preserved block");
                }
            });
            context.clickScreenButton("projection.prefablitematica.cancel");
            context.waitFor(client -> !dev.tensin.prefablitematica.client.BlueprintProjectionClient.INSTANCE.active());
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst(); var bench = (BlueprintWorkbenchBlockEntity) player.level().getBlockEntity(benchPos); var data = bench.blueprint();
                var mode = PrefabLitematicaMod.CONFIG.placementMode;
                try {
                    int index = 0;
                    for (String current : List.of("SAFE", "REPLACE")) for (var rotation : BlueprintRotation.values()) {
                        PrefabLitematicaMod.CONFIG.placementMode = current;
                        var origin = new BlockPos(player.blockPosition().getX() + 48 + index++ * 32, 100, player.blockPosition().getZ() + 48);
                        int width = rotation.ordinal() % 2 == 0 ? data.sizeX : data.sizeZ, depth = rotation.ordinal() % 2 == 0 ? data.sizeZ : data.sizeX;
                        for (int x = origin.getX() >> 4; x <= (origin.getX() + width - 1) >> 4; x++)
                            for (int z = origin.getZ() >> 4; z <= (origin.getZ() + depth - 1) >> 4; z++) player.level().getChunk(x, z);
                        var snapshots = new HashMap<BlockPos, net.minecraft.nbt.CompoundTag>();
                        for (var block : data.blocks) {
                            var target = origin.offset(rotation.apply(block.relativePos(), data.sizeX, data.sizeZ));
                            if (BlueprintBlockPolicy.isPreserved(block.state()) || current.equals("REPLACE") && block.state().isAir()) {
                                player.level().setBlock(target, Blocks.CHEST.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
                                var chest = (ChestBlockEntity) player.level().getBlockEntity(target); chest.setItem(0, new ItemStack(Items.DIAMOND, 64));
                                if (BlueprintBlockPolicy.isPreserved(block.state())) snapshots.put(target, chest.saveWithFullMetadata(player.level().registryAccess()));
                            }
                        }
                        var scan = new BlueprintPreviewScan(player, data, origin, rotation); while (!scan.tick(128)) {}
                        if (!scan.clear()) throw new AssertionError("Preserved native-paste cells must not conflict: " + scan.firstProblem());
                        data.fill(); var placements = PrefabLitematicaMod.placements(server); placements.start(player, BlueprintItem.loaded(data), origin, rotation);
                        for (int i = 0; i < 100 && data.locked; i++) placements.tick();
                        if (data.locked || data.fullyCharged()) throw new AssertionError("Native policy paste must finish and debit charge");
                        for (var block : data.blocks) {
                            var target = origin.offset(rotation.apply(block.relativePos(), data.sizeX, data.sizeZ));
                            if (BlueprintBlockPolicy.isPreserved(block.state())) {
                                if (!snapshots.get(target).equals(player.level().getBlockEntity(target).saveWithFullMetadata(player.level().registryAccess())))
                                    throw new AssertionError("Native paste modified preserved world inventory");
                            } else if (player.level().getBlockState(target) != block.state().rotate(rotation.vanilla))
                                throw new AssertionError("Native paste failed portal policy at " + target);
                        }
                    }
                } finally { PrefabLitematicaMod.CONFIG.placementMode = mode; }
                PrefabLitematicaMod.LOGGER.info("Portal native paste passed SAFE/REPLACE in all four rotations; all eight preserved inventories unchanged");
            });
            context.waitTicks(65);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst(); var bench = (BlueprintWorkbenchBlockEntity) player.level().getBlockEntity(benchPos);
                bench.setItem(0, new ItemStack(PrefabLitematicaMod.BLANK_BLUEPRINT)); player.closeContainer(); player.openMenu(bench);
            });
            context.waitForScreen(BlueprintWorkbenchScreen.class); importFile(context, "sliced.litematic");
            world.getServer().waitFor(server -> {
                var data = ((BlueprintWorkbenchBlockEntity) server.getPlayerList().getPlayers().getFirst().level().getBlockEntity(benchPos)).blueprint();
                return data != null && data.portalNeedsSlicing;
            });
            world.getConnection().waitForClientboundPackets();
            context.waitFor(client -> notice(client.gui.screen()).equals(Component.translatable("gui.prefablitematica.portal_slicing").getString()));
            keepScreenshot(context, "blueprint-portal-slicing");
            context.waitTicks(65);
            boolean survivalImports = world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst(); var bench = (BlueprintWorkbenchBlockEntity) player.level().getBlockEntity(benchPos);
                boolean previous = PrefabLitematicaMod.CONFIG.allowSurvivalImports; PrefabLitematicaMod.CONFIG.allowSurvivalImports = false;
                bench.setItem(0, new ItemStack(PrefabLitematicaMod.BLANK_BLUEPRINT)); player.closeContainer();
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); player.openMenu(bench); return previous;
            });
            try {
                context.waitForScreen(BlueprintWorkbenchScreen.class); importFile(context, "portal.litematic");
                context.waitFor(client -> notice(client.gui.screen()).equals("Only creative imports are enabled"));
                world.getServer().runOnServer(server -> {
                    var player = server.getPlayerList().getPlayers().getFirst(); var bench = (BlueprintWorkbenchBlockEntity) player.level().getBlockEntity(benchPos);
                    if (bench.blueprint() != null || !bench.getItem(0).is(PrefabLitematicaMod.BLANK_BLUEPRINT))
                        throw new AssertionError("Rejected survival import must preserve the blank blueprint");
                });
            } finally { world.getServer().runOnServer(server -> PrefabLitematicaMod.CONFIG.allowSurvivalImports = survivalImports); }
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    private static void importFile(ClientGameTestContext context, String name) {
        context.waitForScreen(BlueprintWorkbenchScreen.class);
        context.waitFor(client -> client.gui.screen() instanceof BlueprintWorkbenchScreen screen && screen.children().stream()
                .anyMatch(widget -> widget instanceof net.minecraft.client.gui.components.Button button && button.active
                        && button.getMessage().getString().equals(Component.translatable("gui.prefablitematica.import").getString())));
        context.clickScreenButton(context.computeOnClient(client -> Component.translatable("gui.prefablitematica.import").getString()));
        context.waitFor(client -> client.gui.screen() instanceof BlueprintWorkbenchScreen screen && screen.children().stream()
                .anyMatch(widget -> widget instanceof net.minecraft.client.gui.components.EditBox));
        context.runOnClient(client -> ((BlueprintWorkbenchScreen) client.gui.screen()).children().stream().filter(widget -> widget instanceof net.minecraft.client.gui.components.EditBox)
                .map(widget -> (net.minecraft.client.gui.components.EditBox) widget).findFirst().orElseThrow().setValue(name));
        context.waitFor(client -> client.gui.screen() instanceof BlueprintWorkbenchScreen screen && screen.children().stream()
                .anyMatch(widget -> widget instanceof net.minecraft.client.gui.components.Button button && button.getMessage().getString().equals(name)));
        context.clickScreenButton(name);
    }
    private static String notice(Object screen) {
        if (!(screen instanceof BlueprintWorkbenchScreen)) return "";
        try {
            var field = BlueprintWorkbenchScreen.class.getDeclaredField("message"); field.setAccessible(true); return (String) field.get(screen);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void keepScreenshot(ClientGameTestContext context, String name) throws Exception {
        Files.copy(context.takeScreenshot(name), Path.of(System.getProperty("prefablitematica.projectDir"), "docs", "screenshots", name + ".png"), StandardCopyOption.REPLACE_EXISTING);
    }
    private static BlueprintData capture(Object schematic, String name) throws Exception {
        var capture = new LitematicaIntegration.Capture(new LitematicaIntegration.Source(name, "", LitematicaIntegration.SourceKind.LOADED_SCHEMATIC, null, schematic, null));
        while (!capture.tick(16)) {} return capture.finish();
    }
    private static void write(Object schematic, Path directory, String name) throws Exception {
        if (!(boolean) schematic.getClass().getMethod("writeToFile", Path.class, String.class, boolean.class).invoke(schematic, directory, name, true))
            throw new AssertionError("Cannot write portal fixture");
    }
    @SuppressWarnings("unchecked")
    private static Object fixture(Direction.Axis axis, int width, int height, boolean missingFrame, boolean ticks) throws Exception {
        var areaType = Class.forName("fi.dy.masa.litematica.selection.AreaSelection"); var boxType = Class.forName("fi.dy.masa.litematica.selection.Box");
        var schematicType = Class.forName("fi.dy.masa.litematica.schematic.LitematicaSchematic");
        var area = areaType.getConstructor().newInstance(); areaType.getMethod("setName", String.class).invoke(area, "Portal fixture");
        var maximum = portalPos(axis, width + 10, height + 1);
        areaType.getMethod("addSubRegionBox", boxType, boolean.class).invoke(area,
                boxType.getConstructor(BlockPos.class, BlockPos.class, String.class).newInstance(BlockPos.ZERO, maximum, "test"), true);
        var schematic = schematicType.getMethod("createEmptySchematic", areaType, String.class).invoke(null, area, "Policy regression");
        var container = schematicType.getMethod("getSubRegionContainer", String.class).invoke(schematic, "test");
        var set = container.getClass().getMethod("set", int.class, int.class, int.class, BlockState.class);
        for (int x = 1; x <= width; x++) for (int y = 1; y <= height; y++) {
            var pos = portalPos(axis, x, y); set.invoke(container, pos.getX(), pos.getY(), pos.getZ(), Blocks.NETHER_PORTAL.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS, axis));
        }
        for (int x = 1; x <= width; x++) for (int y : new int[]{0, height + 1}) {
            var pos = portalPos(axis, x, y); set.invoke(container, pos.getX(), pos.getY(), pos.getZ(), Blocks.OBSIDIAN.defaultBlockState());
        }
        for (int y = 1; y <= height; y++) for (int x : new int[]{0, width + 1}) {
            if (missingFrame && x == 0 && y == 1) continue;
            var pos = portalPos(axis, x, y); set.invoke(container, pos.getX(), pos.getY(), pos.getZ(), Blocks.OBSIDIAN.defaultBlockState());
        }
        var preserved = List.of(Blocks.BEDROCK, Blocks.END_PORTAL_FRAME, Blocks.END_PORTAL, Blocks.END_GATEWAY,
                Blocks.BUDDING_AMETHYST, Blocks.TRIAL_SPAWNER, Blocks.VAULT, Blocks.SPAWNER);
        for (int i = 0; i < preserved.size(); i++) {
            var pos = portalPos(axis, width + 2 + i, 0); set.invoke(container, pos.getX(), pos.getY(), pos.getZ(), preserved.get(i).defaultBlockState());
        }
        var paid = portalPos(axis, width + 10, 0); set.invoke(container, paid.getX(), paid.getY(), paid.getZ(), Blocks.OAK_PLANKS.defaultBlockState());
        if (ticks) {
            var saved = (Map<BlockPos, ScheduledTick<Block>>) schematicType.getMethod("getScheduledBlockTicksForRegion", String.class).invoke(schematic, "test");
            var pos = portalPos(axis, 1, 1); saved.put(pos, new ScheduledTick<>(Blocks.NETHER_PORTAL, pos, 3, TickPriority.NORMAL, 1));
        }
        return schematic;
    }
    private static BlockPos portalPos(Direction.Axis axis, int horizontal, int y) {
        return axis == Direction.Axis.X ? new BlockPos(horizontal, y, 0) : new BlockPos(0, y, horizontal);
    }
}
