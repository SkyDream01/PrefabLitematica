// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica;

import dev.tensin.prefablitematica.blueprint.*;
import dev.tensin.prefablitematica.item.BlueprintItem;
import dev.tensin.prefablitematica.placement.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.resources.Identifier;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Release regressions using real server worlds, tags, storage and placement tasks. */
public final class BlueprintReleaseGameTests {
    private static final List<Block> PRESERVED = List.of(Blocks.BEDROCK, Blocks.END_PORTAL_FRAME,
            Blocks.END_PORTAL, Blocks.END_GATEWAY, Blocks.BUDDING_AMETHYST, Blocks.TRIAL_SPAWNER,
            Blocks.VAULT, Blocks.SPAWNER);
    private static final AtomicReference<BlockPos> DENIED = new AtomicReference<>();
    static { PlacementValidator.ALLOW.register((player, pos) -> !pos.equals(DENIED.get())); }

    private static BlueprintData create(GameTestHelper helper, List<BlueprintBlock> blocks, int x, int y, int z) throws Exception {
        var manager = PrefabLitematicaMod.manager(helper.getLevel().getServer());
        var data = new BlueprintData(UUID.randomUUID(), "Release regression", x, y, z, blocks, new LinkedHashMap<>());
        data = BlueprintSerializer.decode(BlueprintSerializer.encode(data), data.id, PrefabLitematicaMod.CONFIG);
        var analysis = manager.new Analysis(data); while (!analysis.tick(128)) {}
        manager.create(data); return data;
    }
    private static void finish(GameTestHelper helper, BlueprintData data) {
        var placements = PrefabLitematicaMod.placements(helper.getLevel().getServer());
        for (int i = 0; i < 100 && data.locked; i++) placements.tick();
        helper.assertTrue(!data.locked, "Placement must finish within the test budget");
    }
    private static BlueprintPreviewScan scan(ServerPlayer player, BlueprintData data, BlockPos origin, BlueprintRotation rotation) {
        var scan = new BlueprintPreviewScan(player, data, origin, rotation); while (!scan.tick(128)) {} return scan;
    }

    @GameTest public void registeredRecipesCraftWorkbenchAndBlankBlueprint(GameTestHelper helper) {
        var recipes = helper.getLevel().getServer().getRecipeManager();
        var workbench = net.minecraft.world.item.crafting.CraftingInput.of(3, 3, List.of(ItemStack.EMPTY, new ItemStack(Items.BOOK), ItemStack.EMPTY,
                new ItemStack(Items.LAPIS_BLOCK), new ItemStack(Items.INK_SAC), new ItemStack(Items.REDSTONE_BLOCK),
                ItemStack.EMPTY, new ItemStack(Items.OBSIDIAN), ItemStack.EMPTY));
        var result = recipes.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, workbench, helper.getLevel()).orElseThrow()
                .value().assemble(workbench);
        helper.assertTrue(result.is(PrefabLitematicaMod.WORKBENCH_ITEM) && result.getCount() == 1, "Documented shaped recipe must craft one workbench");
        var blank = net.minecraft.world.item.crafting.CraftingInput.of(2, 2, List.of(new ItemStack(Items.INK_SAC), new ItemStack(Items.REDSTONE),
                new ItemStack(Items.BOOK), new ItemStack(Items.LAPIS_LAZULI)));
        result = recipes.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, blank, helper.getLevel()).orElseThrow()
                .value().assemble(blank);
        helper.assertTrue(result.is(PrefabLitematicaMod.BLANK_BLUEPRINT) && result.getCount() == 1 && BlueprintItem.isBlank(result),
                "Documented shapeless recipe must craft one unbound blueprint"); helper.succeed();
    }

    @GameTest public void breakingWorkbenchReturnsBulkInputsAndAllContainerComponents(GameTestHelper helper) throws Exception {
        var pos = helper.absolutePos(new BlockPos(1, 2, 1)); var player = helper.makeMockServerPlayerInLevel();
        helper.getLevel().setBlockAndUpdate(pos, PrefabLitematicaMod.WORKBENCH.defaultBlockState());
        var bench = (dev.tensin.prefablitematica.block.entity.BlueprintWorkbenchBlockEntity) helper.getLevel().getBlockEntity(pos);
        var data = create(helper, List.of(new BlueprintBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null)), 1, 1, 1); data.fill();
        PrefabLitematicaMod.manager(helper.getLevel().getServer()).saveProgress(data);
        var box = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:blue_shulker_box")));
        box.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Break regression"));
        box.set(net.minecraft.core.component.DataComponents.CONTAINER,
                net.minecraft.world.item.component.ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 7))));
        bench.setItem(0, BlueprintItem.loaded(data)); bench.setItem(1, new ItemStack(Items.STONE, 4096)); bench.setItem(19, box.copy());
        helper.getLevel().destroyBlock(pos, true, player);
        var drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(2));
        helper.assertTrue(drops.stream().filter(e -> e.getItem().is(Items.STONE)).mapToInt(e -> e.getItem().getCount()).sum() == 4096,
                "Breaking a workbench must return every item from bulk inputs");
        helper.assertTrue(drops.stream().anyMatch(e -> ItemStack.matches(e.getItem(), box)), "Last return slot must drop its box with name and inventory intact");
        helper.assertTrue(drops.stream().anyMatch(e -> data.id.equals(BlueprintItem.id(e.getItem()))) && new BlueprintManager(helper.getLevel().getServer()).get(data.id).fullyCharged(),
                "Breaking must retain the loaded blueprint identity and paid charge"); helper.succeed();
    }

    @GameTest public void preservedBlocksAreFreeAndKeepWorldStateAndInventoryInEveryRotation(GameTestHelper helper) throws Exception {
        var blocks = new ArrayList<BlueprintBlock>();
        for (int x = 0; x < PRESERVED.size(); x++) blocks.add(new BlueprintBlock(new BlockPos(x, 0, 0), PRESERVED.get(x).defaultBlockState(), null));
        blocks.add(new BlueprintBlock(new BlockPos(8, 0, 0), Blocks.STONE.defaultBlockState(), null));
        var data = create(helper, blocks, 9, 1, 1);
        helper.assertTrue(data.requirements.size() == 1 && data.requirements.values().iterator().next().required == 1,
                "All eight preserved blocks must pass real forbidden tags and require no materials");
        var player = helper.makeMockServerPlayerInLevel(); var placements = PrefabLitematicaMod.placements(helper.getLevel().getServer());
        var mode = PrefabLitematicaMod.CONFIG.placementMode;
        try {
            for (String current : List.of("SAFE", "REPLACE")) for (var rotation : BlueprintRotation.values()) {
                PrefabLitematicaMod.CONFIG.placementMode = current;
                var origin = helper.absolutePos(new BlockPos(1, 3, 1)); var snapshots = new HashMap<BlockPos, net.minecraft.nbt.CompoundTag>();
                for (int x = 0; x < PRESERVED.size(); x++) {
                    var pos = origin.offset(rotation.apply(new BlockPos(x, 0, 0), 9, 1));
                    helper.getLevel().setBlock(pos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
                    var chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(pos); chest.setItem(0, new ItemStack(Items.DIAMOND, x + 1));
                    snapshots.put(pos, chest.saveWithFullMetadata(helper.getLevel().registryAccess()));
                }
                var stone = origin.offset(rotation.apply(new BlockPos(8, 0, 0), 9, 1));
                helper.getLevel().setBlock(stone, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
                helper.assertTrue(scan(player, data, origin, rotation).clear(), "Occupied preserved cells must never conflict");
                data.fill(); placements.start(player, BlueprintItem.loaded(data), origin, rotation); finish(helper, data);
                helper.assertTrue(helper.getLevel().getBlockState(stone).is(Blocks.STONE), "Paid block must be placed");
                for (var entry : snapshots.entrySet()) helper.assertTrue(entry.getValue().equals(helper.getLevel().getBlockEntity(entry.getKey())
                        .saveWithFullMetadata(helper.getLevel().registryAccess())), "Preserved cells must retain existing block entities and inventory");
                helper.assertTrue(!data.fullyCharged(), "Placement must consume only the paid block charge");
            }
        } finally { PrefabLitematicaMod.CONFIG.placementMode = mode; }
        helper.succeed();
    }

    @GameTest public void explicitPortalAirClearsReplaceContainersWhileUnlistedAirStays(GameTestHelper helper) throws Exception {
        var data = create(helper, List.of(new BlueprintBlock(BlockPos.ZERO, Blocks.AIR.defaultBlockState(), null),
                new BlueprintBlock(new BlockPos(2, 0, 0), Blocks.OBSIDIAN.defaultBlockState(), null)), 3, 1, 1);
        helper.assertTrue(data.requirements.size() == 1, "Explicit portal air must be free");
        var player = helper.makeMockServerPlayerInLevel(); var origin = helper.absolutePos(new BlockPos(1, 3, 1));
        helper.getLevel().setBlockAndUpdate(origin, Blocks.CHEST.defaultBlockState());
        ((ChestBlockEntity) helper.getLevel().getBlockEntity(origin)).setItem(0, new ItemStack(Items.DIAMOND, 64));
        helper.getLevel().setBlockAndUpdate(origin.east(), Blocks.GOLD_BLOCK.defaultBlockState());
        var mode = PrefabLitematicaMod.CONFIG.placementMode;
        try {
            PrefabLitematicaMod.CONFIG.placementMode = "SAFE";
            helper.assertTrue(!scan(player, data, origin, BlueprintRotation.NONE).clear(), "Explicit air remains subject to SAFE validation");
            PrefabLitematicaMod.CONFIG.placementMode = "REPLACE"; data.fill();
            PrefabLitematicaMod.placements(helper.getLevel().getServer()).start(player, BlueprintItem.loaded(data), origin, BlueprintRotation.NONE); finish(helper, data);
            helper.assertTrue(helper.getLevel().getBlockState(origin).isAir() && helper.getLevel().getBlockEntity(origin) == null,
                    "Explicit portal air must clear a container and its NBT");
            helper.assertTrue(helper.getLevel().getBlockState(origin.east()).is(Blocks.GOLD_BLOCK), "Unlisted air must not erase the world");
        } finally { PrefabLitematicaMod.CONFIG.placementMode = mode; }
        helper.succeed();
    }

    @GameTest public void permissionAndUnbreakableCellsRejectBeforeDebiting(GameTestHelper helper) throws Exception {
        var data = create(helper, List.of(new BlueprintBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null)), 2, 1, 1); data.fill();
        var player = helper.makeMockServerPlayerInLevel(); var origin = helper.absolutePos(new BlockPos(1, 3, 1));
        helper.getLevel().setBlockAndUpdate(origin, Blocks.AIR.defaultBlockState()); helper.getLevel().setBlockAndUpdate(origin.east(), Blocks.AIR.defaultBlockState());
        try {
            DENIED.set(origin.east());
            helper.assertTrue(!scan(player, data, origin, BlueprintRotation.NONE).clear(), "Protection must cover empty bounding cells");
            var placements = PrefabLitematicaMod.placements(helper.getLevel().getServer());
            placements.start(player, BlueprintItem.loaded(data), origin, BlueprintRotation.NONE); finish(helper, data);
            helper.assertTrue(data.fullyCharged() && helper.getLevel().getBlockState(origin).isAir(), "Final protection rejection must retain charge and terrain");
        } finally { DENIED.set(null); }
        var mode = PrefabLitematicaMod.CONFIG.placementMode;
        try {
            PrefabLitematicaMod.CONFIG.placementMode = "REPLACE"; helper.getLevel().setBlockAndUpdate(origin.east(), Blocks.BEDROCK.defaultBlockState());
            helper.assertTrue(!scan(player, data, origin, BlueprintRotation.NONE).clear(), "World bedrock cannot be replaced by ordinary blueprint cells");
        } finally { PrefabLitematicaMod.CONFIG.placementMode = mode; }
        helper.succeed();
    }

    @GameTest public void reservationsStopWorldWritesAndCancellationKeepsUnspentCharge(GameTestHelper helper) throws Exception {
        var data = create(helper, List.of(new BlueprintBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null)), 2, 1, 1); data.fill();
        var player = helper.makeMockServerPlayerInLevel(); var origin = helper.absolutePos(new BlockPos(1, 3, 1));
        helper.getLevel().setBlockAndUpdate(origin, Blocks.AIR.defaultBlockState()); helper.getLevel().setBlockAndUpdate(origin.east(), Blocks.AIR.defaultBlockState());
        var placements = PrefabLitematicaMod.placements(helper.getLevel().getServer());
        placements.start(player, BlueprintItem.loaded(data), origin, BlueprintRotation.NONE);
        helper.assertTrue(!helper.getLevel().setBlockAndUpdate(origin.east(), Blocks.GOLD_BLOCK.defaultBlockState()), "Reserved empty cells must veto outside writes");
        helper.assertTrue(!scan(player, data, origin, BlueprintRotation.NONE).clear(), "Another projection must see the reservation");
        placements.stop();
        helper.assertTrue(!data.locked && data.fullyCharged() && helper.getLevel().getBlockState(origin).isAir(), "Pre-debit cancellation must unlock without loss");
        helper.assertTrue(helper.getLevel().setBlockAndUpdate(origin.east(), Blocks.GOLD_BLOCK.defaultBlockState()), "Stopping must release world reservations");
        helper.succeed();
    }

    @GameTest public void consumeOptionRetiresAllCopiesAndPersists(GameTestHelper helper) throws Exception {
        var data = create(helper, List.of(new BlueprintBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null)), 1, 1, 1); data.fill();
        var player = helper.makeMockServerPlayerInLevel(); var origin = helper.absolutePos(new BlockPos(1, 3, 1));
        helper.getLevel().setBlockAndUpdate(origin, Blocks.AIR.defaultBlockState()); var stack = BlueprintItem.loaded(data);
        boolean consume = PrefabLitematicaMod.CONFIG.consumeBlueprintAfterPlacement;
        try {
            PrefabLitematicaMod.CONFIG.consumeBlueprintAfterPlacement = true;
            PrefabLitematicaMod.placements(helper.getLevel().getServer()).start(player, stack, origin, BlueprintRotation.NONE); finish(helper, data);
            helper.assertTrue(stack.isEmpty() && data.retired && PrefabLitematicaMod.manager(helper.getLevel().getServer()).get(data.id) == null,
                    "Consume option must remove the stack and invalidate copied UUIDs");
            helper.assertTrue(new BlueprintManager(helper.getLevel().getServer()).get(data.id) == null, "Retirement must survive disk reload");
        } finally { PrefabLitematicaMod.CONFIG.consumeBlueprintAfterPlacement = consume; }
        helper.succeed();
    }

    @GameTest public void savedBlockAndFluidTicksAreScheduledAtRotatedCoordinates(GameTestHelper helper) throws Exception {
        var blocks = List.of(new BlueprintBlock(BlockPos.ZERO, Blocks.OBSERVER.defaultBlockState().setValue(BlockStateProperties.POWERED, true), null),
                new BlueprintBlock(BlockPos.ZERO.east(), Blocks.WATER.defaultBlockState(), null));
        var data = new BlueprintData(UUID.randomUUID(), "Scheduled regression", 2, 1, 1, blocks,
                List.of(new BlueprintScheduledTick(BlockPos.ZERO, false, Identifier.parse("minecraft:observer"), helper.getLevel().getGameTime() + 1000, -3, 41),
                        new BlueprintScheduledTick(BlockPos.ZERO.east(), true, Identifier.parse("minecraft:water"), helper.getLevel().getGameTime() + 1000, 2, 42)), new LinkedHashMap<>());
        var manager = PrefabLitematicaMod.manager(helper.getLevel().getServer()); var analysis = manager.new Analysis(data); while (!analysis.tick(128)) {} manager.create(data); data.fill();
        var player = helper.makeMockServerPlayerInLevel(); var origin = helper.absolutePos(new BlockPos(1, 3, 1));
        helper.getLevel().setBlockAndUpdate(origin, Blocks.AIR.defaultBlockState()); helper.getLevel().setBlockAndUpdate(origin.south(), Blocks.AIR.defaultBlockState());
        PrefabLitematicaMod.placements(helper.getLevel().getServer()).start(player, BlueprintItem.loaded(data), origin, BlueprintRotation.CW_90); finish(helper, data);
        helper.assertTrue(helper.getLevel().getBlockTicks().hasScheduledTick(origin, Blocks.OBSERVER), "Imported observer tick must be scheduled");
        helper.assertTrue(helper.getLevel().getFluidTicks().hasScheduledTick(origin.south(), Fluids.WATER), "Imported water tick must rotate with the blueprint");
        helper.succeed();
    }
}
