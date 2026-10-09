// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica;

import dev.tensin.prefablitematica.block.entity.BlueprintWorkbenchBlockEntity;
import dev.tensin.prefablitematica.blueprint.BlueprintManager;
import dev.tensin.prefablitematica.blueprint.BlueprintData;
import dev.tensin.prefablitematica.blueprint.BlueprintBlock;
import dev.tensin.prefablitematica.placement.BlueprintPaste;
import dev.tensin.prefablitematica.client.screen.BlueprintWorkbenchScreen;
import dev.tensin.prefablitematica.integration.litematica.LitematicaIntegration;
import dev.tensin.prefablitematica.placement.BlueprintPreviewScan;
import dev.tensin.prefablitematica.placement.BlueprintRotation;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.item.PrimedTnt;

/** Imports, charges and pastes the user's entire pearl cannon, including its headless pistons. */
public final class PearlSchematicClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        if (!LitematicaIntegration.available()) throw new AssertionError("Run this regression with -PwithLitematica");
        try (var world = context.worldBuilder().create()) {
            var schematicFile = context.computeOnClient(client -> {
                var directory = Files.createTempDirectory(LitematicaIntegration.schematicDirectory(), "blueprint-pearl-test-");
                try (var input = getClass().getResourceAsStream("/prefablitematica-test/pearl-headless-pistons.litematic")) {
                    if (input == null) throw new AssertionError("Missing pearl cannon regression fixture");
                    Files.copy(input, directory.resolve("pearl.litematic"));
                }
                return directory.resolve("pearl.litematic");
            });
            var position = world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst(); var pos = player.blockPosition().below();
                player.level().setBlockAndUpdate(pos, PrefabLitematicaMod.WORKBENCH.defaultBlockState());
                var bench = (BlueprintWorkbenchBlockEntity) player.level().getBlockEntity(pos);
                bench.setItem(0, new ItemStack(PrefabLitematicaMod.BLANK_BLUEPRINT)); player.openMenu(bench); return pos;
            });
            context.waitForScreen(BlueprintWorkbenchScreen.class);
            context.clickScreenButton(context.computeOnClient(client -> Component.translatable("gui.prefablitematica.import").getString()));
            context.runOnClient(client -> ((BlueprintWorkbenchScreen) client.gui.screen()).children().stream()
                    .filter(widget -> widget instanceof EditBox).map(widget -> (EditBox) widget).findFirst().orElseThrow().setValue("pearl.litematic"));
            context.waitFor(client -> client.gui.screen() instanceof BlueprintWorkbenchScreen screen && screen.children().stream()
                    .anyMatch(widget -> widget instanceof Button button && button.getMessage().getString().equals("pearl.litematic")));
            context.clickScreenButton("pearl.litematic");
            world.getServer().waitFor(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var bench = (BlueprintWorkbenchBlockEntity) player.level().getBlockEntity(position);
                return bench != null && bench.blueprint() != null;
            });
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var bench = (BlueprintWorkbenchBlockEntity) player.level().getBlockEntity(position); var data = bench.blueprint();
                if (data.blocks.size() != 2723 || data.sizeX != 32 || data.sizeY != 101 || data.sizeZ != 24)
                    throw new AssertionError("All pearl cannon blocks and original bounds must survive import");
                var positions = new BlockPos[]{new BlockPos(31, 89, 14), new BlockPos(4, 89, 23)};
                var directions = new Direction[]{Direction.EAST, Direction.SOUTH};
                for (int i = 0; i < positions.length; i++) {
                    var pos = positions[i]; var direction = directions[i];
                    var base = data.blocks.stream().filter(block -> block.relativePos().equals(pos)).findFirst().orElseThrow();
                    if (!base.state().is(Blocks.STICKY_PISTON) || !base.state().getValue(BlockStateProperties.EXTENDED)
                            || base.state().getValue(BlockStateProperties.FACING) != direction)
                        throw new AssertionError("Boundary piston must keep its original extended state at " + pos);
                    if (data.blocks.stream().anyMatch(block -> block.relativePos().equals(pos.relative(direction))))
                        throw new AssertionError("Import must not add an out-of-bounds head");
                }
                for (var block : new net.minecraft.world.level.block.Block[]{Blocks.PISTON, Blocks.STICKY_PISTON}) {
                    long bases = data.blocks.stream().filter(entry -> entry.state().is(block)).count();
                    var requirement = data.requirements.get("item:minecraft:" + (block == Blocks.PISTON ? "piston" : "sticky_piston"));
                    if (requirement == null || requirement.required != bases) throw new AssertionError("Each piston base must charge one item");
                }
                var restored = new BlueprintManager(server).get(data.id);
                if (restored == null || !restored.blocks.equals(data.blocks)) throw new AssertionError("Imported pearl cannon must persist without changing states");
                long comparators = data.blocks.stream().filter(block -> block.state().is(Blocks.COMPARATOR)
                        && block.blockEntity() != null && block.blockEntity().getIntOr("OutputSignal", -1) == 1).count();
                if (comparators != 60) throw new AssertionError("All 60 comparator output signals must survive import, got " + comparators);
                PrefabLitematicaMod.LOGGER.info("Pearl cannon regression imported {} blocks with both headless extended pistons intact", data.blocks.size());
                bench.setItem(1, new ItemStack(PrefabLitematicaMod.BATTERY)); player.containerMenu.broadcastChanges();
            });
            world.getConnection().waitForClientboundPackets(); context.waitTicks(4);
            context.clickScreenButton(context.computeOnClient(client -> Component.translatable("gui.prefablitematica.charge").getString()));
            world.getServer().waitFor(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var bench = (BlueprintWorkbenchBlockEntity) player.level().getBlockEntity(position);
                return bench != null && bench.blueprint().fullyCharged();
            });
            // Refresh a loaded legacy blueprint through the same real UI/network flow, retaining its paid charge.
            context.waitTicks(65);
            var legacyId = world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var bench = (BlueprintWorkbenchBlockEntity) player.level().getBlockEntity(position); var data = bench.blueprint();
                data.requiresReimport = true;
                dev.tensin.prefablitematica.network.BlueprintNetworking.sendStatus(player,
                        (dev.tensin.prefablitematica.screen.BlueprintWorkbenchScreenHandler) player.containerMenu, "");
                return data.id;
            });
            context.waitFor(client -> client.gui.screen() instanceof BlueprintWorkbenchScreen screen && screen.children().stream()
                    .anyMatch(widget -> widget instanceof Button button && button.active && button.getMessage().getString().equals(Component.translatable("gui.prefablitematica.import").getString())));
            context.clickScreenButton(context.computeOnClient(client -> Component.translatable("gui.prefablitematica.import").getString()));
            context.runOnClient(client -> ((BlueprintWorkbenchScreen) client.gui.screen()).children().stream()
                    .filter(widget -> widget instanceof EditBox).map(widget -> (EditBox) widget).findFirst().orElseThrow().setValue("pearl.litematic"));
            context.waitFor(client -> client.gui.screen() instanceof BlueprintWorkbenchScreen screen && screen.children().stream()
                    .anyMatch(widget -> widget instanceof Button button && button.getMessage().getString().equals("pearl.litematic")));
            context.clickScreenButton("pearl.litematic");
            world.getServer().waitFor(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var data = ((BlueprintWorkbenchBlockEntity) player.level().getBlockEntity(position)).blueprint();
                return data != null && data.id.equals(legacyId) && !data.requiresReimport && data.fullyCharged();
            });
            PrefabLitematicaMod.LOGGER.info("Legacy pearl cannon refresh kept its UUID and fully paid charge");
            var origin = world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var bench = (BlueprintWorkbenchBlockEntity) player.level().getBlockEntity(position); var data = bench.blueprint();
                var target = new BlockPos(player.blockPosition().getX() + 48, 140, player.blockPosition().getZ() + 48);
                for (int x = target.getX() >> 4; x <= (target.getX() + data.sizeZ - 1) >> 4; x++)
                    for (int z = target.getZ() >> 4; z <= (target.getZ() + data.sizeX - 1) >> 4; z++) player.level().getChunk(x, z);
                var scan = new BlueprintPreviewScan(player, data, target, BlueprintRotation.CW_90); while (!scan.tick(4096)) {}
                if (!scan.clear()) throw new AssertionError("Real pearl cannon must be ready to paste: " + scan.firstProblem());
                PrefabLitematicaMod.placements(server).start(player, bench.getItem(0), target, BlueprintRotation.CW_90);
                return target;
            });
            world.getServer().waitFor(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var data = ((BlueprintWorkbenchBlockEntity) player.level().getBlockEntity(position)).blueprint();
                return !data.locked;
            });
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var data = ((BlueprintWorkbenchBlockEntity) player.level().getBlockEntity(position)).blueprint();
                if (data.fullyCharged()) throw new AssertionError("Complete pearl cannon paste must consume its charge");
                for (var block : data.blocks) {
                    var target = origin.offset(BlueprintRotation.CW_90.apply(block.relativePos(), data.sizeX, data.sizeZ));
                    var expected = block.state().rotate(BlueprintRotation.CW_90.vanilla);
                    var actual = player.level().getBlockState(target);
                    if (actual != expected) throw new AssertionError("Pearl cannon paste changed " + block.relativePos() + ": expected " + expected + ", got " + actual);
                }
                var restored = new BlueprintManager(server).get(data.id);
                if (restored == null || restored.fullyCharged() || !restored.blocks.equals(data.blocks)) throw new AssertionError("Complete paste must save its debit and preserve the source schematic");
                PrefabLitematicaMod.LOGGER.info("Pearl cannon paste matched all {} rotated block states", data.blocks.size());
            });
            var original = context.computeOnClient(client -> Class.forName("fi.dy.masa.litematica.schematic.LitematicaSchematic")
                    .getMethod("createFromFile", java.nio.file.Path.class, String.class).invoke(null, schematicFile.getParent(), schematicFile.getFileName().toString()));
            var actual = world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var data = ((BlueprintWorkbenchBlockEntity) player.level().getBlockEntity(position)).blueprint();
                return snapshot(player.level(), data, origin);
            });
            var nativeTrace = world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var data = ((BlueprintWorkbenchBlockEntity) player.level().getBlockEntity(position)).blueprint();
                return new Trace(player.level(), data, origin);
            });
            world.getServer().waitFor(server -> nativeTrace.sample());
            var referenceTrace = world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var data = ((BlueprintWorkbenchBlockEntity) player.level().getBlockEntity(position)).blueprint();
                clear(player.level(), data, origin);
                pasteOriginal(player.level(), original, data, origin);
                var reference = snapshot(player.level(), data, origin);
                if (!actual.equals(reference)) throw new AssertionError("Actual Litematica-backed blueprint paste must match original schematic states AND block-entity NBT: " + difference(actual, reference));
                return new Trace(player.level(), data, origin);
            });
            world.getServer().waitFor(server -> referenceTrace.sample());
            referenceTrace.assertMatches(nativeTrace, "native blueprint");
            var portableTrace = world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var data = ((BlueprintWorkbenchBlockEntity) player.level().getBlockEntity(position)).blueprint();
                clear(player.level(), data, origin);
                var blocks = data.blocks.stream().map(block -> new BlueprintBlock(origin.offset(BlueprintRotation.CW_90.apply(block.relativePos(), data.sizeX, data.sizeZ)),
                        block.state().rotate(BlueprintRotation.CW_90.vanilla), block.blockEntity())).sorted(Comparator.comparingInt((BlueprintBlock b) -> b.relativePos().getY())
                        .thenComparingInt(b -> b.relativePos().getZ()).thenComparingInt(b -> b.relativePos().getX())).toList();
                BlueprintPaste.paste(player.level(), blocks);
                if (!actual.equals(snapshot(player.level(), data, origin))) throw new AssertionError("Portable server paste must match native Litematica block entities too");
                return new Trace(player.level(), data, origin);
            });
            world.getServer().waitFor(server -> portableTrace.sample());
            referenceTrace.assertMatches(portableTrace, "portable server");
            PrefabLitematicaMod.LOGGER.info("Pearl cannon direct paste equivalence passed: 2723 states, 72 block entities, 60 comparator outputs, {} TNT peak", referenceTrace.peak);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    private record Snapshot(Map<BlockPos, BlockState> states, Map<BlockPos, CompoundTag> entities) {}
    private static Snapshot snapshot(ServerLevel world, BlueprintData data, BlockPos origin) {
        var states = new HashMap<BlockPos, BlockState>(); var entities = new HashMap<BlockPos, CompoundTag>();
        for (var block : data.blocks) {
            var pos = origin.offset(BlueprintRotation.CW_90.apply(block.relativePos(), data.sizeX, data.sizeZ));
            states.put(block.relativePos(), world.getBlockState(pos));
            var entity = world.getBlockEntity(pos);
            if (entity != null) entities.put(block.relativePos(), entity.saveCustomOnly(world.registryAccess()));
        }
        return new Snapshot(states, entities);
    }
    private static String difference(Snapshot actual, Snapshot reference) {
        for (var entry : reference.entities.entrySet()) if (!Objects.equals(entry.getValue(), actual.entities.get(entry.getKey())))
            return entry.getKey() + " expected " + entry.getValue() + " got " + actual.entities.get(entry.getKey());
        return "block state mismatch";
    }
    private static void clear(ServerLevel world, BlueprintData data, BlockPos origin) {
        int flags = net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_SKIP_ALL_SIDEEFFECTS;
        for (var pos : BlockPos.betweenClosed(origin.offset(-4, -4, -4), origin.offset(data.sizeZ + 4, data.sizeY + 4, data.sizeX + 4))) world.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
        var bounds = new net.minecraft.world.level.levelgen.structure.BoundingBox(origin.getX() - 4, origin.getY() - 4, origin.getZ() - 4,
                origin.getX() + data.sizeZ + 4, origin.getY() + data.sizeY + 4, origin.getZ() + data.sizeX + 4);
        world.getBlockTicks().clearArea(bounds); world.getFluidTicks().clearArea(bounds); world.clearBlockEvents(bounds);
        world.getEntitiesOfClass(PrimedTnt.class, new AABB(origin).inflate(256)).forEach(net.minecraft.world.entity.Entity::discard);
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void pasteOriginal(ServerLevel world, Object schematic, BlueprintData data, BlockPos origin) throws Exception {
        var placementType = Class.forName("fi.dy.masa.litematica.schematic.placement.SchematicPlacement");
        var placement = placementType.getMethod("createTemporary", schematic.getClass(), BlockPos.class).invoke(null, schematic, origin.offset(data.sizeZ - 1, 0, 0));
        placementType.getMethod("setRotation", net.minecraft.world.level.block.Rotation.class, Class.forName("fi.dy.masa.malilib.gui.interfaces.IMessageConsumer")).invoke(placement, BlueprintRotation.CW_90.vanilla, null);
        var replace = (Class<? extends Enum>) Class.forName("fi.dy.masa.litematica.util.ReplaceBehavior");
        var layers = (Class<? extends Enum>) Class.forName("fi.dy.masa.litematica.util.PasteLayerBehavior");
        var method = Class.forName("fi.dy.masa.litematica.util.SchematicPlacingUtils").getMethod("placeToWorldWithinChunk", net.minecraft.world.level.Level.class, net.minecraft.world.level.ChunkPos.class, placementType, replace, layers, boolean.class);
        for (var chunk : (Set<?>) placementType.getMethod("getTouchedChunks").invoke(placement))
            if (!(boolean) method.invoke(null, world, chunk, placement, Enum.valueOf(replace, "WITH_NON_AIR"), Enum.valueOf(layers, "ALL"), false)) throw new AssertionError("Original direct paste failed");
    }
    private static final class Trace {
        private final ServerLevel world;
        private final BlueprintData data;
        private final BlockPos origin, plate;
        private final long start;
        private long last = -1;
        private boolean released;
        private record Frame(long tick, int tnt, Snapshot snapshot) {}
        private final List<Frame> frames = new ArrayList<>();
        private int peak;
        Trace(ServerLevel world, BlueprintData data, BlockPos origin) {
            this.world = world; this.data = data; this.origin = origin; start = world.getGameTime();
            var block = data.blocks.stream().filter(b -> b.state().is(Blocks.SPRUCE_PRESSURE_PLATE)).findFirst().orElseThrow();
            plate = origin.offset(BlueprintRotation.CW_90.apply(block.relativePos(), data.sizeX, data.sizeZ));
            world.getRandom().setSeed(812739L);
            world.setBlockAndUpdate(plate, world.getBlockState(plate).setValue(BlockStateProperties.POWERED, true));
        }
        boolean sample() {
            long elapsed = world.getGameTime() - start;
            if (last != elapsed) {
                last = elapsed;
                if (elapsed >= 10 && !released) { released = true; world.setBlockAndUpdate(plate, world.getBlockState(plate).setValue(BlockStateProperties.POWERED, false)); }
                int tnt = world.getEntitiesOfClass(PrimedTnt.class, new AABB(origin).inflate(256)).size(); peak = Math.max(peak, tnt);
                frames.add(new Frame(elapsed, tnt, snapshot(world, data, origin)));
            }
            if (elapsed >= 160) {
                if (peak == 0) throw new AssertionError("Firing regression must actually activate TNT");
                var concrete = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("minecraft:white_concrete"));
                for (var block : data.blocks) if (block.state().is(concrete) || block.state().is(Blocks.FURNACE)) {
                    var pos = origin.offset(BlueprintRotation.CW_90.apply(block.relativePos(), data.sizeX, data.sizeZ));
                    if (world.getBlockState(pos).getBlock() != block.state().getBlock()) throw new AssertionError("Cannon shot damaged its structure at " + block.relativePos());
                }
            }
            return elapsed >= 160;
        }
        void assertMatches(Trace other, String label) {
            if (!frames.equals(other.frames)) {
                for (int i = 0; i < Math.min(frames.size(), other.frames.size()); i++) if (!frames.get(i).equals(other.frames.get(i)))
                    throw new AssertionError(label + " firing diverged at frame " + i + ": reference TNT " + frames.get(i).tnt + " got " + other.frames.get(i).tnt
                            + "; " + difference(other.frames.get(i).snapshot, frames.get(i).snapshot));
                throw new AssertionError(label + " firing trace lengths differ");
            }
            PrefabLitematicaMod.LOGGER.info("{} matched original cannon for {} firing ticks; peak TNT {}", label, frames.size(), peak);
        }
    }
}
