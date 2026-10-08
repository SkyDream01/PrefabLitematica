// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica;

import dev.tensin.prefablitematica.material.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.*;
import dev.tensin.prefablitematica.blueprint.*;
import dev.tensin.prefablitematica.block.entity.BlueprintWorkbenchBlockEntity;
import dev.tensin.prefablitematica.item.BlueprintItem;
import dev.tensin.prefablitematica.placement.BlueprintRotation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.component.ItemContainerContents;
import dev.tensin.prefablitematica.screen.BlueprintWorkbenchScreenHandler;
import net.minecraft.world.level.block.Blocks;
import java.util.*;

public final class BlueprintGameTests {
    private static Item item(String name) { return net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("minecraft:" + name)); }
    @GameTest public void materialTagsRespectShapesAndColors(GameTestHelper helper) {
        var registry=new MaterialEquivalenceRegistry();
        String oak=registry.resolve(item("oak_planks"),true).key();
        helper.assertTrue(oak.equals(registry.resolve(item("spruce_planks"),true).key()),"Spruce and oak planks must substitute");
        helper.assertTrue(!oak.equals(registry.resolve(item("oak_slab"),true).key()),"Slab must not substitute planks");
        helper.assertTrue(!registry.resolve(item("oak_slab"),true).key().equals(registry.resolve(item("oak_stairs"),true).key()),"Slabs and stairs must remain separate");
        helper.assertTrue(registry.resolve(item("red_wool"),true).key().equals(registry.resolve(item("blue_wool"),true).key()),"Wool colors must substitute");
        helper.assertTrue(!registry.resolve(item("white_concrete"),true).key().equals(registry.resolve(item("white_concrete_powder"),true).key()),"Concrete and powder must remain separate");
        helper.assertTrue(!registry.resolve(item("red_stained_glass"),true).key().equals(registry.resolve(item("red_stained_glass_pane"),true).key()),"Glass and pane must remain separate");
        helper.assertTrue(registry.resolve(item("stone_slab"),true).key().equals(registry.resolve(item("andesite_slab"),true).key()),"Stone slabs must substitute");
        helper.succeed();
    }
    private static BlueprintData create(GameTestHelper helper, List<BlueprintBlock> blocks, int x, int y, int z) throws Exception {
        var manager=PrefabLitematicaMod.manager(helper.getLevel().getServer());
        var data=new BlueprintData(UUID.randomUUID(),"Integration house",x,y,z,blocks,new LinkedHashMap<>());
        var analysis=manager.new Analysis(data);while(!analysis.tick(4096)){}
        manager.create(data);return data;
    }
    private static BlueprintWorkbenchBlockEntity workbench(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(pos, PrefabLitematicaMod.WORKBENCH.defaultBlockState());
        return (BlueprintWorkbenchBlockEntity) helper.getLevel().getBlockEntity(pos);
    }
    private static ItemStack shulker(Item item, ItemStack... contents) {
        var box = new ItemStack(item); box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(Arrays.asList(contents))); return box;
    }
    private static List<BlueprintBlock> stoneBlocks(int count) {
        var blocks = new ArrayList<BlueprintBlock>();
        for (int x = 0; x < count; x++) blocks.add(new BlueprintBlock(new BlockPos(x, 0, 0), Blocks.STONE.defaultBlockState(), null));
        return blocks;
    }
    @GameTest public void repeatedMaterialInputsMergeTo4096AndPersist(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel(); var bench = workbench(helper);
        var menu = new BlueprintWorkbenchScreenHandler(1, player.getInventory(), bench);
        menu.setCarried(new ItemStack(Items.STONE, 64)); menu.clicked(1, 0, ContainerInput.PICKUP, player);
        menu.setCarried(new ItemStack(Items.STONE, 64)); menu.clicked(1, 0, ContainerInput.PICKUP, player);
        helper.assertTrue(bench.getItem(1).getCount() == 128 && menu.getCarried().isEmpty() && bench.getItem(2).isEmpty(), "64 plus 64 must be 128 in the same input slot");
        for (int batch = 2; batch < 64; batch++) {
            player.getInventory().setItem(9, new ItemStack(Items.STONE, 64)); menu.quickMoveStack(player, dev.tensin.prefablitematica.screen.BlueprintWorkbenchScreenHandler.PLAYER_START);
        }
        helper.assertTrue(bench.getItem(1).getCount() == 4096 && bench.getItem(2).isEmpty(), "Shift-click batches must accumulate in one slot up to 4096");
        menu.setCarried(new ItemStack(Items.STONE, 64)); menu.clicked(1, 0, ContainerInput.PICKUP, player);
        helper.assertTrue(bench.getItem(1).getCount() == 4096 && menu.getCarried().getCount() == 64, "Full input must preserve the excess on the cursor");
        menu.setCarried(ItemStack.EMPTY); player.getInventory().setItem(9, new ItemStack(Items.STONE, 64)); menu.quickMoveStack(player, dev.tensin.prefablitematica.screen.BlueprintWorkbenchScreenHandler.PLAYER_START);
        helper.assertTrue(bench.getItem(2).getCount() == 64 && bench.getItem(1).getCount() == 4096, "Overflow must enter another input without exceeding 4096");
        bench.getItem(1).set(DataComponents.CUSTOM_NAME, Component.literal("Bulk stone"));
        var saved = bench.saveCustomOnly(helper.getLevel().registryAccess()); bench.clearContent();
        bench.loadCustomOnly(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
        helper.assertTrue(bench.getItem(1).getCount() == 4096 && bench.getItem(1).getHoverName().getString().equals("Bulk stone") && bench.getItem(2).getCount() == 64, "Large counts and components must survive vanilla NBT saving");
        var clientMenu = new BlueprintWorkbenchScreenHandler(2, player.getInventory()); clientMenu.getSlot(1).set(bench.getItem(1).copy());
        helper.assertTrue(clientMenu.getSlot(1).getItem().getCount() == 4096 && clientMenu.getSlot(1).getMaxStackSize(new ItemStack(Items.STONE)) == 4096, "Client inventory must retain synchronized large counts");
        helper.assertTrue(menu.getSlot(0).getMaxStackSize(new ItemStack(PrefabLitematicaMod.BLANK_BLUEPRINT)) == 1 && menu.getSlot(10).getMaxStackSize(new ItemStack(Items.BUCKET)) == new ItemStack(Items.BUCKET).getMaxStackSize(), "Blueprint and return slots must retain normal limits");
        helper.succeed();
    }
    @GameTest public void largeMaterialStacksWithdrawAsNormalStacksWithoutLoss(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel(); var bench = workbench(helper);
        var menu = new BlueprintWorkbenchScreenHandler(1, player.getInventory(), bench); bench.setItem(1, new ItemStack(Items.STONE, 4096));
        menu.clicked(1, 0, ContainerInput.PICKUP, player);
        helper.assertTrue(menu.getCarried().getCount() == 64 && bench.getItem(1).getCount() == 4032, "Picking up bulk input must produce a legal cursor stack");
        menu.clicked(1, 0, ContainerInput.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && bench.getItem(1).getCount() == 4096, "Putting the stack back must merge into the bulk input");
        menu.clicked(1, 0, ContainerInput.SWAP, player);
        helper.assertTrue(player.getInventory().getItem(0).getCount() == 64 && bench.getItem(1).getCount() == 4032, "Number-key extraction must keep hotbar stacks legal");
        menu.setCarried(new ItemStack(Items.DIRT)); menu.clicked(1, 0, ContainerInput.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(Items.DIRT) && bench.getItem(1).getCount() == 4032, "Swapping a different cursor item must not export a bulk stack"); menu.setCarried(ItemStack.EMPTY);
        menu.quickMoveStack(player, 1);
        helper.assertTrue(player.getInventory().countItem(Items.STONE) == 2304 && bench.getItem(1).getCount() == 1792, "Shift-click withdrawal must fill normal inventory stacks and preserve overflow");
        for (int slot = 0; slot < 36; slot++) helper.assertTrue(player.getInventory().getItem(slot).getCount() <= player.getInventory().getItem(slot).getMaxStackSize(), "No player slot may contain a bulk stack");
        bench.setItem(1, new ItemStack(Items.WATER_BUCKET)); player.getInventory().setItem(9, new ItemStack(Items.WATER_BUCKET)); menu.quickMoveStack(player, dev.tensin.prefablitematica.screen.BlueprintWorkbenchScreenHandler.PLAYER_START);
        helper.assertTrue(bench.getItem(1).getCount() == 2, "Fluid bucket inputs must also merge despite their vanilla limit of one"); helper.succeed();
    }
    @GameTest public void bulkChargingConsumesOnlyRemainingRequirements(GameTestHelper helper) throws Exception {
        var data = create(helper, stoneBlocks(128), 128, 1, 1); var player = helper.makeMockServerPlayerInLevel(); var bench = workbench(helper);
        bench.setItem(0, BlueprintItem.loaded(data)); bench.setItem(1, new ItemStack(Items.STONE, 4096)); bench.charge(player);
        helper.assertTrue(data.fullyCharged() && bench.getItem(1).getCount() == 3968, "Bulk charging must leave all 3968 surplus materials in the input");
        bench.charge(player); helper.assertTrue(bench.getItem(1).getCount() == 3968, "Repeated charging of a full blueprint must consume nothing");
        var restored = new BlueprintManager(helper.getLevel().getServer()).get(data.id);
        helper.assertTrue(restored != null && restored.fullyCharged(), "Bulk charge progress must persist"); helper.succeed();
    }
    @GameTest public void shulkerInputsPreserveUnusedContentsAndReturnFluidBuckets(GameTestHelper helper) throws Exception {
        var blocks = stoneBlocks(70); blocks.add(new BlueprintBlock(new BlockPos(70, 0, 0), Blocks.OAK_PLANKS.defaultBlockState(), null));
        blocks.add(new BlueprintBlock(new BlockPos(71, 0, 0), Blocks.WATER.defaultBlockState(), null));
        var data = create(helper, blocks, 72, 1, 1); var player = helper.makeMockServerPlayerInLevel(); var bench = workbench(helper); bench.setItem(0, BlueprintItem.loaded(data));
        var contents = net.minecraft.core.NonNullList.withSize(27, ItemStack.EMPTY);
        contents.set(5, new ItemStack(Items.STONE, 64)); contents.set(17, new ItemStack(Items.SPRUCE_PLANKS, 5));
        contents.set(23, new ItemStack(Items.WATER_BUCKET)); contents.set(24, new ItemStack(Items.WATER_BUCKET)); contents.set(26, new ItemStack(Items.DIAMOND, 7));
        var box = new ItemStack(item("red_shulker_box")); box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents)); box.set(DataComponents.CUSTOM_NAME, Component.literal("Materials"));
        var original = box.copy(); bench.setItem(1, box); bench.setItem(2, new ItemStack(Items.STONE, 64)); bench.charge(player);
        helper.assertTrue(data.fullyCharged() && bench.getItem(1).isEmpty() && bench.getItem(2).getCount() == 58, "Box contents and loose materials must share the same remaining requirements");
        helper.assertTrue(bench.getItem(11).is(Items.BUCKET) && bench.getItem(11).getCount() == 2, "Water buckets consumed inside the box must return two buckets");
        ItemStack returned = bench.getItem(12);
        helper.assertTrue(returned.is(item("red_shulker_box")) && returned.getHoverName().getString().equals("Materials"), "Box color and name must survive return to the next output slot");
        var remainder = net.minecraft.core.NonNullList.withSize(27, ItemStack.EMPTY); returned.get(DataComponents.CONTAINER).copyInto(remainder);
        helper.assertTrue(remainder.get(5).isEmpty() && remainder.get(17).getCount() == 4 && remainder.get(23).isEmpty() && remainder.get(24).isEmpty() && remainder.get(26).getCount() == 7, "Surplus and unrelated contents must retain their original slots");
        helper.assertTrue(original.get(DataComponents.CONTAINER).nonEmptyItemCopyStream().mapToInt(ItemStack::getCount).sum() == 78, "Charging must not mutate shared component contents"); helper.succeed();
    }
    @GameTest public void usedShulkerReturnsEmptyAndUnmatchedShulkerStays(GameTestHelper helper) throws Exception {
        var data = create(helper, stoneBlocks(64), 64, 1, 1); var player = helper.makeMockServerPlayerInLevel(); var bench = workbench(helper); bench.setItem(0, BlueprintItem.loaded(data));
        var unmatched = shulker(item("blue_shulker_box"), new ItemStack(Items.DIAMOND, 4)); bench.setItem(1, unmatched.copy()); bench.charge(player);
        helper.assertTrue(data.charge() == 0 && ItemStack.matches(bench.getItem(1), unmatched) && bench.getItem(11).isEmpty(), "A box with no useful material must stay unchanged");
        bench.setItem(1, shulker(item("blue_shulker_box"), new ItemStack(Items.STONE, 64))); bench.charge(player);
        helper.assertTrue(data.fullyCharged() && bench.getItem(1).isEmpty() && bench.getItem(11).is(item("blue_shulker_box")) && bench.getItem(11).get(DataComponents.CONTAINER).nonEmptyItemCopyStream().findAny().isEmpty(), "A fully consumed box must return empty to the output");
        helper.assertTrue(new BlueprintWorkbenchScreenHandler(1, player.getInventory(), bench).getSlot(1).getMaxStackSize(bench.getItem(11)) == 1, "Shulker boxes must remain individual containers"); helper.succeed();
    }
    @GameTest public void containerReturnsDropOnlyOverflowWhenInventoryIsFull(GameTestHelper helper) throws Exception {
        var data = create(helper, List.of(new BlueprintBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null), new BlueprintBlock(new BlockPos(1, 0, 0), Blocks.WATER.defaultBlockState(), null)), 2, 1, 1);
        var player = (net.minecraft.server.level.ServerPlayer) helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var bench = workbench(helper); bench.setItem(0, BlueprintItem.loaded(data));
        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        int bucketLimit = new ItemStack(Items.BUCKET).getMaxStackSize(); player.getInventory().setItem(9, new ItemStack(Items.BUCKET, bucketLimit - 1));
        for (int slot = BlueprintWorkbenchBlockEntity.OUTPUT_SLOT; slot < BlueprintWorkbenchBlockEntity.OUTPUT_END; slot++) bench.setItem(slot, new ItemStack(Items.BUCKET, bucketLimit));
        bench.setItem(1, new ItemStack(Items.WATER_BUCKET, 2)); bench.setItem(2, shulker(Items.SHULKER_BOX, new ItemStack(Items.STONE))); bench.charge(player);
        helper.assertTrue(data.fullyCharged() && player.getInventory().getItem(9).getCount() == bucketLimit && bench.getItem(11).getCount() == bucketLimit, "Return must fill the one available inventory space without altering full output");
        helper.runAfterDelay(1, () -> {
            var drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(bench.getBlockPos()).inflate(2));
            helper.assertTrue(drops.stream().filter(entity -> entity.getItem().is(Items.BUCKET)).mapToInt(entity -> entity.getItem().getCount()).sum() == 1, "Exactly one bucket must drop after partial inventory insertion: " + drops.stream().map(entity -> entity.getItem().toString()).toList());
            helper.assertTrue(drops.stream().filter(entity -> entity.getItem().is(Items.SHULKER_BOX)).count() == 1, "The box must drop beside the bench when output and inventory are full"); helper.succeed();
        });
    }
    @GameTest public void nineShulkerReturnsPersistAndShiftClickUsesBothGrids(GameTestHelper helper) throws Exception {
        var data = create(helper, stoneBlocks(9), 9, 1, 1); var player = helper.makeMockServerPlayerInLevel(); var bench = workbench(helper);
        bench.setItem(0, BlueprintItem.loaded(data));
        for (int slot = 1; slot < 10; slot++) {
            var box = shulker(Items.SHULKER_BOX, new ItemStack(Items.STONE)); box.set(DataComponents.CUSTOM_NAME, Component.literal("Box " + slot)); bench.setItem(slot, box);
        }
        bench.charge(player);
        helper.assertTrue(data.fullyCharged() && player.getInventory().countItem(Items.SHULKER_BOX) == 0, "All nine boxes must fit in the return grid before using the inventory");
        var saved = bench.saveCustomOnly(helper.getLevel().registryAccess()); bench.clearContent();
        bench.loadCustomOnly(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
        var menu = new BlueprintWorkbenchScreenHandler(1, player.getInventory(), bench);
        for (int i = 0; i < 9; i++) {
            var output = bench.getItem(11 + i); var slot = menu.getSlot(BlueprintWorkbenchScreenHandler.RETURN_START + i);
            helper.assertTrue(output.is(Items.SHULKER_BOX) && output.getHoverName().getString().equals("Box " + (i + 1)) && output.get(DataComponents.CONTAINER).nonEmptyItemCopyStream().findAny().isEmpty(), "Every named return must persist in its own slot");
            helper.assertTrue(slot.getContainerSlot() == 11 + i && slot.x == 104 + (i % 3) * 18 && slot.y == 62 + (i / 3) * 18 && !slot.mayPlace(new ItemStack(Items.STONE)) && !bench.canPlaceItem(11 + i, new ItemStack(Items.STONE)), "All nine outputs must form a read-only 3x3 grid");
        }
        menu.quickMoveStack(player, BlueprintWorkbenchScreenHandler.RETURN_END - 1);
        helper.assertTrue(bench.getItem(19).isEmpty() && player.getInventory().countItem(Items.SHULKER_BOX) == 1, "Shift-click from the final output must enter the player inventory");
        player.getInventory().setItem(9, new ItemStack(Items.STONE, 64)); menu.quickMoveStack(player, BlueprintWorkbenchScreenHandler.PLAYER_START);
        helper.assertTrue(bench.getItem(1).getCount() == 64 && bench.getItem(19).isEmpty(), "Shift-click from the player inventory must enter inputs and leave returns alone"); helper.succeed();
    }
    @GameTest public void returnGridMergesExistingBucketsBeforeUsingEmptySlots(GameTestHelper helper) throws Exception {
        var data = create(helper, List.of(new BlueprintBlock(BlockPos.ZERO, Blocks.WATER.defaultBlockState(), null)), 1, 1, 1);
        var player = helper.makeMockServerPlayerInLevel(); var bench = workbench(helper); bench.setItem(0, BlueprintItem.loaded(data));
        int limit = new ItemStack(Items.BUCKET).getMaxStackSize(); bench.setItem(12, new ItemStack(Items.BUCKET, limit - 1));
        bench.setItem(1, new ItemStack(Items.WATER_BUCKET, 2)); bench.charge(player);
        helper.assertTrue(bench.getItem(12).getCount() == limit && bench.getItem(11).is(Items.BUCKET) && bench.getItem(11).getCount() == 1, "The grid must fill an existing bucket stack before starting another");
        helper.assertTrue(player.getInventory().countItem(Items.BUCKET) == 0 && bench.getItem(13).isEmpty(), "No return may overflow while grid space remains"); helper.succeed();
    }
    @GameTest public void bubbleColumnsKeepTheirStateThroughChargingAndPlacement(GameTestHelper helper) throws Exception {
        var player = helper.makeMockServerPlayerInLevel(); var benchPos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(benchPos, PrefabLitematicaMod.WORKBENCH.defaultBlockState());
        var bench = (BlueprintWorkbenchBlockEntity) helper.getLevel().getBlockEntity(benchPos);
        for (boolean drag : new boolean[]{false, true}) {
            var base = drag ? Blocks.MAGMA_BLOCK : Blocks.SOUL_SAND;
            var bubble = Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DRAG, drag);
            var data = create(helper, List.of(new BlueprintBlock(BlockPos.ZERO, base.defaultBlockState(), null),
                    new BlueprintBlock(BlockPos.ZERO.above(), bubble, null), new BlueprintBlock(BlockPos.ZERO.above(2), bubble, null)), 1, 3, 1);
            helper.assertTrue(data.requirements.size() == 2 && data.requirements.get("item:minecraft:water_bucket").required == 2,
                    "Bubble columns must charge only their base and two water buckets");
            bench.clearContent(); var item = BlueprintItem.loaded(data); bench.setItem(0, item);
            bench.setItem(1, new ItemStack(base.asItem())); bench.setItem(2, new ItemStack(Items.WATER_BUCKET)); bench.setItem(3, new ItemStack(Items.WATER_BUCKET));
            bench.charge(player);
            helper.assertTrue(data.fullyCharged() && bench.getItem(11).getCount() == 2, "Water charging must return exactly two buckets");
            var origin = helper.absolutePos(new BlockPos(drag ? 5 : 3, 2, 3));
            for (int y = 0; y < 3; y++) helper.getLevel().setBlockAndUpdate(origin.above(y), Blocks.AIR.defaultBlockState());
            var manager = PrefabLitematicaMod.placements(helper.getLevel().getServer()); manager.start(player, item, origin, BlueprintRotation.NONE); manager.tick();
            helper.assertTrue(helper.getLevel().getBlockState(origin).is(base), "Column base must be placed first");
            for (int y = 1; y < 3; y++) helper.assertTrue(helper.getLevel().getBlockState(origin.above(y)) == bubble, "Up/down bubble column state must survive placement");
            var restored = new BlueprintManager(helper.getLevel().getServer()).get(data.id);
            helper.assertTrue(restored != null && restored.blocks.get(1).state() == bubble && !restored.fullyCharged(), "Column state and consumed charge must persist");
        }
        helper.succeed();
    }
    @GameTest public void movingPistonIsForbiddenBeforeMaterialAnalysis(GameTestHelper helper) throws Exception {
        var data = new BlueprintData(UUID.randomUUID(), "Moving piston", 1, 1, 1,
                List.of(new BlueprintBlock(BlockPos.ZERO, Blocks.MOVING_PISTON.defaultBlockState(), null)), new LinkedHashMap<>());
        boolean refused = false;
        try { BlueprintSerializer.decode(BlueprintSerializer.encode(data), data.id, PrefabLitematicaMod.CONFIG); }
        catch (java.io.IOException expected) { refused = expected.getMessage().contains("Forbidden technical block: minecraft:moving_piston"); }
        helper.assertTrue(refused, "Transient moving-piston block entities cannot be reconstructed from cosmetic NBT"); helper.succeed();
    }
    @GameTest public void substitutedMaterialsPreserveStoredBlockState(GameTestHelper helper) throws Exception {
        var state=Blocks.OAK_PLANKS.defaultBlockState();
        var data=create(helper,List.of(new BlueprintBlock(BlockPos.ZERO,state,null)),1,1,1);
        var player=helper.makeMockServerPlayerInLevel();var pos=helper.absolutePos(new BlockPos(1,1,1));
        helper.getLevel().setBlockAndUpdate(pos,PrefabLitematicaMod.WORKBENCH.defaultBlockState());
        var bench=(BlueprintWorkbenchBlockEntity)helper.getLevel().getBlockEntity(pos);
        var blueprint=new ItemStack(PrefabLitematicaMod.BLUEPRINT);BlueprintItem.bind(blueprint,data);bench.setItem(0,blueprint);
        bench.setItem(1,new ItemStack(Items.SPRUCE_PLANKS));bench.charge(player);
        helper.assertTrue(data.fullyCharged(),"Spruce should fill oak requirement");helper.assertTrue(bench.getItem(1).isEmpty(),"Exactly one item consumed");
        helper.assertTrue(data.blocks.getFirst().state()==state,"Substitution must not modify stored oak state");helper.succeed();
    }
    @GameTest public void fluidBucketsReturnAndBatteryDoesNotMintBuckets(GameTestHelper helper) throws Exception {
        var data=create(helper,List.of(new BlueprintBlock(BlockPos.ZERO,Blocks.WATER.defaultBlockState(),null),new BlueprintBlock(new BlockPos(1,0,0),Blocks.LAVA.defaultBlockState(),null)),2,1,1);
        var player=helper.makeMockServerPlayerInLevel();var pos=helper.absolutePos(new BlockPos(1,1,1));
        helper.getLevel().setBlockAndUpdate(pos,PrefabLitematicaMod.WORKBENCH.defaultBlockState());var bench=(BlueprintWorkbenchBlockEntity)helper.getLevel().getBlockEntity(pos);
        var blueprint=new ItemStack(PrefabLitematicaMod.BLUEPRINT);BlueprintItem.bind(blueprint,data);bench.setItem(0,blueprint);
        bench.setItem(1,new ItemStack(Items.WATER_BUCKET));bench.setItem(2,new ItemStack(Items.WATER_BUCKET));bench.setItem(3,new ItemStack(Items.LAVA_BUCKET));bench.charge(player);
        helper.assertTrue(data.fullyCharged(),"Fluid requirements should be full");helper.assertTrue(bench.getItem(11).is(Items.BUCKET)&&bench.getItem(11).getCount()==3,"All three buckets returned");
        data.reset();bench.setItem(11,ItemStack.EMPTY);bench.setItem(9,new ItemStack(PrefabLitematicaMod.BATTERY));bench.charge(player);
        helper.assertTrue(data.fullyCharged()&&bench.getItem(9).isEmpty(),"One creative battery consumed from material input");helper.assertTrue(bench.getItem(11).isEmpty(),"Battery must never create empty buckets");helper.succeed();
    }
    @GameTest public void safeModeValidatesEverythingBeforeChangingWorld(GameTestHelper helper) throws Exception {
        var data=create(helper,List.of(new BlueprintBlock(BlockPos.ZERO,Blocks.OAK_PLANKS.defaultBlockState(),null),new BlueprintBlock(new BlockPos(1,0,0),Blocks.STONE.defaultBlockState(),null)),2,1,1);
        data.fill();PrefabLitematicaMod.manager(helper.getLevel().getServer()).saveProgress(data);
        var player=helper.makeMockServerPlayerInLevel();var origin=helper.absolutePos(new BlockPos(1,2,1));
        helper.getLevel().setBlockAndUpdate(origin,Blocks.AIR.defaultBlockState());helper.getLevel().setBlockAndUpdate(origin.east(),Blocks.DIAMOND_BLOCK.defaultBlockState());
        var item=new ItemStack(PrefabLitematicaMod.BLUEPRINT);BlueprintItem.bind(item,data);
        var manager=PrefabLitematicaMod.placements(helper.getLevel().getServer());manager.start(player,item,origin,BlueprintRotation.NONE);manager.tick();
        helper.assertTrue(helper.getLevel().getBlockState(origin).isAir(),"First position must stay unchanged when second is occupied");
        helper.assertTrue(helper.getLevel().getBlockState(origin.east()).is(Blocks.DIAMOND_BLOCK),"Obstacle must remain intact");helper.assertTrue(data.fullyCharged()&&!data.locked,"Failed preflight must preserve charge and release lock");helper.succeed();
    }
    @GameTest public void placementRotatesAndResetsSharedCharge(GameTestHelper helper) throws Exception {
        var data=create(helper,List.of(new BlueprintBlock(BlockPos.ZERO,Blocks.OAK_PLANKS.defaultBlockState(),null),new BlueprintBlock(new BlockPos(1,0,0),Blocks.STONE.defaultBlockState(),null)),2,1,1);
        data.fill();PrefabLitematicaMod.manager(helper.getLevel().getServer()).saveProgress(data);
        var player=helper.makeMockServerPlayerInLevel();var origin=helper.absolutePos(new BlockPos(1,2,1));
        helper.getLevel().setBlockAndUpdate(origin,Blocks.AIR.defaultBlockState());helper.getLevel().setBlockAndUpdate(origin.south(),Blocks.AIR.defaultBlockState());
        var item=new ItemStack(PrefabLitematicaMod.BLUEPRINT);BlueprintItem.bind(item,data);
        var manager=PrefabLitematicaMod.placements(helper.getLevel().getServer());manager.start(player,item,origin,BlueprintRotation.CW_90);manager.tick();
        helper.assertTrue(helper.getLevel().getBlockState(origin).is(Blocks.OAK_PLANKS),"Original oak state must be placed");
        helper.assertTrue(helper.getLevel().getBlockState(origin.south()).is(Blocks.STONE),"Second block must rotate east to south");
        helper.assertTrue(!data.fullyCharged()&&!data.locked,"Charge must reset on completion");
        var restored=new BlueprintManager(helper.getLevel().getServer()).get(data.id);helper.assertTrue(restored!=null&&restored.charge()==0,"Reset must persist on disk");helper.succeed();
    }
    @GameTest public void perTickBudgetAndDuplicateBlueprintLock(GameTestHelper helper) throws Exception {
        var data=create(helper,List.of(new BlueprintBlock(BlockPos.ZERO,Blocks.OAK_PLANKS.defaultBlockState(),null),new BlueprintBlock(new BlockPos(1,0,0),Blocks.STONE.defaultBlockState(),null)),2,1,1);
        data.fill();var player=helper.makeMockServerPlayerInLevel();var origin=helper.absolutePos(new BlockPos(1,2,1));
        helper.getLevel().setBlockAndUpdate(origin,Blocks.AIR.defaultBlockState());helper.getLevel().setBlockAndUpdate(origin.east(),Blocks.AIR.defaultBlockState());
        var item=new ItemStack(PrefabLitematicaMod.BLUEPRINT);BlueprintItem.bind(item,data);
        var manager=PrefabLitematicaMod.placements(helper.getLevel().getServer());manager.start(player,item,origin,BlueprintRotation.NONE);
        boolean refused=false;try{manager.start(player,item.copy(),origin,BlueprintRotation.NONE);}catch(IllegalArgumentException expected){refused=true;}
        helper.assertTrue(refused,"Same UUID must be locked, including copied stacks");manager.tick();
        var data2=create(helper,List.of(new BlueprintBlock(BlockPos.ZERO,Blocks.OAK_PLANKS.defaultBlockState(),null),new BlueprintBlock(new BlockPos(1,0,0),Blocks.STONE.defaultBlockState(),null)),2,1,1);data2.fill();
        var origin2=origin.south(2);helper.getLevel().setBlockAndUpdate(origin2,Blocks.AIR.defaultBlockState());helper.getLevel().setBlockAndUpdate(origin2.east(),Blocks.AIR.defaultBlockState());
        var task=new dev.tensin.prefablitematica.placement.BlueprintPlacementTask(manager,player,item,data2,origin2,BlueprintRotation.NONE);
        int previous=0;boolean done=false;
        for(int tick=0;tick<100&&!done;tick++){
            done=task.tick(1);int placed=(helper.getLevel().getBlockState(origin2).isAir()?0:1)+(helper.getLevel().getBlockState(origin2.east()).isAir()?0:1);
            helper.assertTrue(placed-previous<=1,"A one-block budget must never place two blocks");previous=placed;
        }
        helper.assertTrue(done&&previous==2,"Budgeted task must eventually finish");task.close();helper.succeed();
    }
    @GameTest public void blankAndLoadedBlueprintsAreDistinctAndLegacyBlanksWork(GameTestHelper helper) throws Exception {
        var blank = new ItemStack(PrefabLitematicaMod.BLANK_BLUEPRINT);
        helper.assertTrue(BlueprintItem.isBlank(blank) && BlueprintItem.id(blank) == null, "Blank blueprint must have no building UUID");
        helper.assertTrue(BlueprintItem.isBlank(new ItemStack(PrefabLitematicaMod.BLUEPRINT)), "Old unused blueprints must remain valid inputs");
        var data = create(helper, List.of(new BlueprintBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null)), 1, 1, 1);
        var loaded = BlueprintItem.loaded(data);
        helper.assertTrue(loaded.is(PrefabLitematicaMod.BLUEPRINT) && !loaded.is(blank.getItem()) && !BlueprintItem.isBlank(loaded), "Import must produce a different, loaded item");
        helper.assertTrue(data.id.equals(BlueprintItem.id(loaded)), "Loaded item must reference server data");
        helper.succeed();
    }
    @GameTest public void batteryUsesMaterialGridWithoutConsumingOtherMaterials(GameTestHelper helper) throws Exception {
        var data = create(helper, List.of(new BlueprintBlock(BlockPos.ZERO, Blocks.OAK_PLANKS.defaultBlockState(), null)), 1, 1, 1);
        var player = helper.makeMockServerPlayerInLevel(); var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(pos, PrefabLitematicaMod.WORKBENCH.defaultBlockState());
        var bench = (BlueprintWorkbenchBlockEntity) helper.getLevel().getBlockEntity(pos); bench.setItem(0, BlueprintItem.loaded(data));
        var boxed = shulker(Items.SHULKER_BOX, new ItemStack(Items.SPRUCE_PLANKS, 64)); bench.setItem(3, boxed.copy());
        bench.setItem(2, new ItemStack(Items.SPRUCE_PLANKS, 64)); bench.setItem(9, new ItemStack(PrefabLitematicaMod.BATTERY, 2)); bench.charge(player);
        helper.assertTrue(data.fullyCharged() && bench.getItem(9).getCount() == 1, "Exactly one battery from the last input consumed");
        helper.assertTrue(bench.getItem(2).getCount() == 64 && bench.getItem(11).isEmpty(), "Battery charging must keep other materials and mint no buckets");
        helper.assertTrue(ItemStack.matches(bench.getItem(3), boxed), "Battery priority must preserve boxed materials too");
        data.reset(); bench.setItem(9, ItemStack.EMPTY); bench.setItem(1, new ItemStack(PrefabLitematicaMod.BATTERY));
        boolean enabled = PrefabLitematicaMod.CONFIG.creativeBatteryEnabled;
        try { PrefabLitematicaMod.CONFIG.creativeBatteryEnabled = false; bench.setItem(2, ItemStack.EMPTY); bench.setItem(3, ItemStack.EMPTY); bench.charge(player); }
        finally { PrefabLitematicaMod.CONFIG.creativeBatteryEnabled = enabled; }
        helper.assertTrue(!data.fullyCharged() && bench.getItem(1).getCount() == 1, "Disabled batteries must not be consumed"); helper.succeed();
    }
    @GameTest public void oldBatterySlotMigratesAndShiftClickUsesMaterialInput(GameTestHelper helper) throws Exception {
        var player = helper.makeMockServerPlayerInLevel(); var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(pos, PrefabLitematicaMod.WORKBENCH.defaultBlockState());
        var bench = (BlueprintWorkbenchBlockEntity) helper.getLevel().getBlockEntity(pos);
        bench.setItem(10, new ItemStack(PrefabLitematicaMod.BATTERY, 3)); bench.setItem(11, new ItemStack(Items.BUCKET, 4));
        var saved = bench.saveCustomOnly(helper.getLevel().registryAccess());
        bench.clearContent(); bench.loadCustomOnly(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
        helper.assertTrue(bench.getItem(10).isEmpty() && bench.getItem(1).is(PrefabLitematicaMod.BATTERY) && bench.getItem(1).getCount() == 3, "Old dedicated battery slot must migrate without loss");
        helper.assertTrue(bench.getItem(11).getCount() == 4, "Saved bucket output must retain its index");
        bench.setItem(1, ItemStack.EMPTY); player.getInventory().setItem(9, new ItemStack(PrefabLitematicaMod.BATTERY, 2));
        var menu = new dev.tensin.prefablitematica.screen.BlueprintWorkbenchScreenHandler(1, player.getInventory(), bench); menu.quickMoveStack(player, dev.tensin.prefablitematica.screen.BlueprintWorkbenchScreenHandler.PLAYER_START);
        helper.assertTrue(bench.getItem(1).is(PrefabLitematicaMod.BATTERY) && bench.getItem(1).getCount() == 2 && bench.getItem(10).isEmpty(), "Shift-clicked batteries must enter the common material grid");
        helper.assertTrue(menu.slots.size() == 55 && menu.slots.get(10).getContainerSlot() == 11, "Menu indices must match both 3x3 grids and the legacy first output"); helper.succeed();
    }
}
