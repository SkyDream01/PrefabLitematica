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
import dev.tensin.prefablitematica.placement.BlueprintPreviewScan;
import dev.tensin.prefablitematica.network.BlueprintPreviewPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.component.ItemContainerContents;
import dev.tensin.prefablitematica.screen.BlueprintWorkbenchScreenHandler;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
    private static BlueprintData conversionBlueprint(GameTestHelper helper, BlockState... states) throws Exception {
        var blocks = new ArrayList<BlueprintBlock>();
        for (int i = 0; i < states.length; i++) blocks.add(new BlueprintBlock(new BlockPos(i, 0, 0), states[i], null));
        return create(helper, blocks, states.length, 1, 1);
    }
    private static ItemStack wornTool(Item item, int remaining) {
        var tool = new ItemStack(item); tool.setDamageValue(tool.getMaxDamage() - remaining); return tool;
    }
    @GameTest public void dirtConvertsGrassPathsAndFarmlandOnlyWithPaidTools(GameTestHelper helper) throws Exception {
        var farm = Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7);
        var path = Blocks.DIRT_PATH.defaultBlockState();
        var data = conversionBlueprint(helper, Blocks.GRASS_BLOCK.defaultBlockState(), path, path, farm, farm, farm);
        var player = helper.makeMockServerPlayerInLevel(); var bench = workbench(helper); bench.setItem(0, BlueprintItem.loaded(data));
        bench.setItem(8, new ItemStack(Items.DIRT, 8)); bench.setItem(1, new ItemStack(Items.IRON_PICKAXE)); bench.charge(player);
        helper.assertTrue(data.requirements.get("item:minecraft:grass_block").supplied == 1 && bench.getItem(8).getCount() == 7,
                "Only grass may use dirt without a suitable tool");
        helper.assertTrue(bench.getItem(1).getDamageValue() == 0 && !data.fullyCharged(), "Wrong tools must stay untouched");
        var shovel = new ItemStack(Items.IRON_SHOVEL); var hoe = new ItemStack(Items.IRON_HOE);
        hoe.enchant(helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING), 3);
        hoe.set(DataComponents.CUSTOM_NAME, Component.literal("Tiller"));
        bench.setItem(1, shovel); bench.setItem(2, hoe); bench.charge(player);
        helper.assertTrue(data.fullyCharged() && bench.getItem(8).getCount() == 2 && shovel.getDamageValue() == 2 && hoe.getDamageValue() == 3,
                "Every path/field must pay a dirt and exactly one durability even with Unbreaking and a creative player");
        bench.charge(player);
        helper.assertTrue(shovel.getDamageValue() == 2 && hoe.getDamageValue() == 3, "A full blueprint must never spend durability again");
        var saved = bench.saveCustomOnly(helper.getLevel().registryAccess()); bench.clearContent();
        bench.loadCustomOnly(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
        helper.assertTrue(bench.getItem(1).getDamageValue() == 2 && bench.getItem(2).getDamageValue() == 3
                && bench.getItem(2).getHoverName().getString().equals("Tiller") && bench.getItem(8).getCount() == 2,
                "Paid durability, names and surplus must persist");
        var restored = new BlueprintManager(helper.getLevel().getServer()).get(data.id);
        helper.assertTrue(restored != null && restored.fullyCharged() && restored.blocks.get(3).state() == farm,
                "Converted materials must preserve the original moisture and persisted progress");
        helper.succeed();
    }
    @GameTest public void finishedMaterialsWinBeforePumpkinCarving(GameTestHelper helper) throws Exception {
        var carved = Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST);
        var data = conversionBlueprint(helper, carved, carved, carved, Blocks.FARMLAND.defaultBlockState());
        var player = helper.makeMockServerPlayerInLevel(); var bench = workbench(helper); bench.setItem(0, BlueprintItem.loaded(data));
        var shears = new ItemStack(Items.SHEARS); var hoe = new ItemStack(Items.IRON_HOE);
        bench.setItem(1, new ItemStack(Items.PUMPKIN, 3)); bench.setItem(2, shears); bench.setItem(3, new ItemStack(Items.DIRT));
        bench.setItem(4, hoe); bench.setItem(8, new ItemStack(Items.CARVED_PUMPKIN)); bench.setItem(9, new ItemStack(Items.FARMLAND)); bench.charge(player);
        helper.assertTrue(data.fullyCharged() && shears.getDamageValue() == 2 && bench.getItem(1).getCount() == 1,
                "Finished carved pumpkins must be charged before consuming two raw pumpkins");
        helper.assertTrue(hoe.getDamageValue() == 0 && bench.getItem(3).getCount() == 1 && bench.getItem(8).isEmpty() && bench.getItem(9).isEmpty(),
                "Finished farmland must not spend dirt or hoe durability");
        helper.assertTrue(data.blocks.getFirst().state() == carved, "Carving must retain blueprint facing"); helper.succeed();
    }
    @GameTest public void toolBreakageAllowsPartialChargingAndMultipleTools(GameTestHelper helper) throws Exception {
        var carved = Blocks.CARVED_PUMPKIN.defaultBlockState();
        var data = conversionBlueprint(helper, carved, carved, carved, carved, carved);
        var player = helper.makeMockServerPlayerInLevel(); var bench = workbench(helper); bench.setItem(0, BlueprintItem.loaded(data));
        bench.setItem(1, new ItemStack(Items.PUMPKIN, 6)); bench.setItem(2, wornTool(Items.SHEARS, 1)); bench.setItem(3, wornTool(Items.SHEARS, 1));
        bench.charge(player);
        helper.assertTrue(data.requirements.get("item:minecraft:carved_pumpkin").supplied == 2 && bench.getItem(1).getCount() == 4
                && bench.getItem(2).isEmpty() && bench.getItem(3).isEmpty(), "Both tools may break, paying only two conversions");
        bench.charge(player);
        helper.assertTrue(bench.getItem(1).getCount() == 4 && data.charge() == .4, "No durability must leave the remaining raw material intact");
        var replacement = new ItemStack(Items.SHEARS); bench.setItem(3, replacement); bench.charge(player);
        helper.assertTrue(data.fullyCharged() && replacement.getDamageValue() == 3 && bench.getItem(1).getCount() == 1,
                "A replacement must complete the three unpaid conversions without double charging"); helper.succeed();
    }
    @GameTest public void multiStepTillingPoolsDurabilityWithoutEatingUnpaidDirt(GameTestHelper helper) throws Exception {
        var data = conversionBlueprint(helper, Blocks.FARMLAND.defaultBlockState());
        var player = helper.makeMockServerPlayerInLevel(); var bench = workbench(helper); bench.setItem(0, BlueprintItem.loaded(data));
        bench.setItem(1, new ItemStack(Items.COARSE_DIRT, 2)); var hoe = wornTool(Items.IRON_HOE, 1); bench.setItem(2, hoe); bench.charge(player);
        helper.assertTrue(data.charge() == 0 && bench.getItem(1).getCount() == 2 && MaterialConversionRegistry.capacity(hoe, MaterialConversionRegistry.Tool.HOE, 1) == 1,
                "A two-use conversion must reserve both uses before spending anything");
        bench.setItem(3, wornTool(Items.WOODEN_HOE, 1)); bench.charge(player);
        helper.assertTrue(data.fullyCharged() && bench.getItem(1).getCount() == 1 && bench.getItem(2).isEmpty() && bench.getItem(3).isEmpty(),
                "Two different hoes can pay one till each for coarse dirt to farmland"); helper.succeed();
    }
    @GameTest public void conversionsShareLooseAndBoxedToolsWithoutMutatingOriginalContents(GameTestHelper helper) throws Exception {
        var carved = Blocks.CARVED_PUMPKIN.defaultBlockState();
        var data = conversionBlueprint(helper, carved, carved, carved, carved);
        var player = helper.makeMockServerPlayerInLevel(); var bench = workbench(helper); bench.setItem(0, BlueprintItem.loaded(data));
        var materials = shulker(item("red_shulker_box"), new ItemStack(Items.PUMPKIN, 4), new ItemStack(Items.DIAMOND, 2));
        bench.setItem(1, materials.copy()); bench.charge(player);
        helper.assertTrue(ItemStack.matches(materials, bench.getItem(1)) && bench.getItem(11).isEmpty(), "A boxed source without a tool must stay in the input unchanged");
        var shears = new ItemStack(Items.SHEARS); shears.set(DataComponents.CUSTOM_NAME, Component.literal("Boxed carver"));
        var tools = shulker(item("blue_shulker_box"), shears); bench.setItem(2, tools.copy()); bench.setItem(9, new ItemStack(Items.CARVED_PUMPKIN)); bench.charge(player);
        helper.assertTrue(data.fullyCharged() && bench.getItem(1).isEmpty() && bench.getItem(2).isEmpty(), "Loose finished materials and tools in another box must work together");
        var left = bench.getItem(11).get(DataComponents.CONTAINER).itemCopies().toList();
        var returnedTool = bench.getItem(12).get(DataComponents.CONTAINER).itemCopies().toList().getFirst();
        helper.assertTrue(bench.getItem(11).is(item("red_shulker_box")) && left.get(0).getCount() == 1 && left.get(1).getCount() == 2,
                "Unused and unrelated boxed materials must retain their slots");
        helper.assertTrue(bench.getItem(12).is(item("blue_shulker_box")) && returnedTool.getDamageValue() == 3
                && returnedTool.getHoverName().getString().equals("Boxed carver"), "A box used only for its tool must return with updated durability and name");
        helper.assertTrue(materials.get(DataComponents.CONTAINER).itemCopies().toList().getFirst().getCount() == 4
                && tools.get(DataComponents.CONTAINER).itemCopies().toList().getFirst().getDamageValue() == 0, "Original components must never be mutated");
        helper.succeed();
    }
    @GameTest public void axesStripWoodAndPayEveryCopperScrapeAndWaxRemoval(GameTestHelper helper) throws Exception {
        var bulb = ((BlockItem) item("copper_bulb")).getBlock().defaultBlockState();
        var data = conversionBlueprint(helper, Blocks.STRIPPED_OAK_LOG.defaultBlockState(), Blocks.STRIPPED_BAMBOO_BLOCK.defaultBlockState(),
                Blocks.STRIPPED_OAK_WOOD.defaultBlockState(), bulb);
        var player = helper.makeMockServerPlayerInLevel(); var bench = workbench(helper); bench.setItem(0, BlueprintItem.loaded(data));
        bench.setItem(1, new ItemStack(Items.OAK_LOG)); bench.setItem(2, new ItemStack(Items.BAMBOO_BLOCK)); bench.setItem(3, new ItemStack(Items.OAK_WOOD));
        bench.setItem(4, new ItemStack(item("waxed_oxidized_copper_bulb"))); var axe = new ItemStack(Items.IRON_AXE); bench.setItem(9, axe); bench.charge(player);
        helper.assertTrue(data.fullyCharged() && axe.getDamageValue() == 7, "Three strips plus wax removal and three oxidation stages must cost seven durability");
        for (int slot = 1; slot <= 4; slot++) helper.assertTrue(bench.getItem(slot).isEmpty(), "Every converted raw material must be consumed exactly once");
        helper.assertTrue(data.blocks.get(3).state() == bulb, "Copper conversion must preserve the requested bulb state"); helper.succeed();
    }
    @GameTest public void strictConversionsRejectWrongSpeciesAndWoodShape(GameTestHelper helper) throws Exception {
        var data = conversionBlueprint(helper, Blocks.STRIPPED_OAK_WOOD.defaultBlockState());
        data.requirements.clear(); data.requirements.put("item:minecraft:stripped_oak_wood",
                new MaterialRequirement("item:minecraft:stripped_oak_wood", "minecraft:stripped_oak_wood", "exact", 1));
        PrefabLitematicaMod.manager(helper.getLevel().getServer()).saveProgress(data);
        var player = helper.makeMockServerPlayerInLevel(); var bench = workbench(helper); bench.setItem(0, BlueprintItem.loaded(data));
        var axe = new ItemStack(Items.IRON_AXE); bench.setItem(1, axe);
        bench.setItem(2, new ItemStack(Items.SPRUCE_WOOD)); bench.setItem(3, new ItemStack(Items.OAK_LOG)); bench.charge(player);
        helper.assertTrue(data.charge() == 0 && axe.getDamageValue() == 0 && bench.getItem(2).getCount() == 1 && bench.getItem(3).getCount() == 1,
                "Exact oak wood needs the correct species and full-bark shape before spending material or axe durability");
        bench.setItem(4, new ItemStack(Items.OAK_WOOD)); bench.charge(player);
        helper.assertTrue(data.fullyCharged() && axe.getDamageValue() == 1 && bench.getItem(4).isEmpty()
                && bench.getItem(2).getCount() == 1 && bench.getItem(3).getCount() == 1, "Only the matching raw wood may be converted"); helper.succeed();
    }
    @GameTest public void powderSnowBucketsChargeReturnPersistAndPlaceExactStates(GameTestHelper helper) throws Exception {
        var snow = Blocks.POWDER_SNOW.defaultBlockState();
        var cauldron = Blocks.POWDER_SNOW_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 2);
        var data = conversionBlueprint(helper, snow, snow, cauldron);
        helper.assertTrue(data.requirements.get("item:minecraft:powder_snow_bucket").required == 3
                && data.requirements.get("item:minecraft:cauldron").required == 1 && !data.requirements.containsKey("item:minecraft:water_bucket"),
                "Two snow blocks and a snow cauldron require three powder snow buckets and one cauldron");
        var player = helper.makeMockServerPlayerInLevel(); var bench = workbench(helper); var blueprint = BlueprintItem.loaded(data); bench.setItem(0, blueprint);
        bench.setItem(1, new ItemStack(Items.WATER_BUCKET, 2)); bench.charge(player);
        helper.assertTrue(data.charge() == 0 && bench.getItem(1).getCount() == 2, "Water buckets must not pay for powder snow");
        bench.setItem(1, new ItemStack(Items.POWDER_SNOW_BUCKET));
        bench.setItem(2, shulker(Items.SHULKER_BOX, new ItemStack(Items.POWDER_SNOW_BUCKET), new ItemStack(Items.POWDER_SNOW_BUCKET), new ItemStack(Items.CAULDRON)));
        bench.charge(player);
        helper.assertTrue(data.fullyCharged() && bench.getItem(11).is(Items.BUCKET) && bench.getItem(11).getCount() == 3
                && bench.getItem(12).is(Items.SHULKER_BOX), "Loose and boxed powder snow must return three buckets and the empty box");
        var restored = new BlueprintManager(helper.getLevel().getServer()).get(data.id);
        helper.assertTrue(restored != null && restored.fullyCharged() && restored.blocks.get(2).state() == cauldron,
                "Snow charging and cauldron fill level must persist");
        var origin = helper.absolutePos(new BlockPos(3, 3, 3));
        for (int i = 0; i < 3; i++) helper.getLevel().setBlockAndUpdate(origin.east(i), Blocks.AIR.defaultBlockState());
        var placements = PrefabLitematicaMod.placements(helper.getLevel().getServer()); placements.start(player, blueprint, origin, BlueprintRotation.NONE); placements.tick();
        helper.assertTrue(helper.getLevel().getBlockState(origin) == snow && helper.getLevel().getBlockState(origin.east()) == snow
                && helper.getLevel().getBlockState(origin.east(2)) == cauldron, "Placement must keep snow and the original cauldron level");
        helper.assertTrue(!data.fullyCharged() && !data.locked, "Snow placement must debit charge exactly once"); helper.succeed();
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
    @GameTest public void headlessExtendedPistonsKeepTheirStateThroughChargingRotationAndPlacement(GameTestHelper helper) throws Exception {
        var player = helper.makeMockServerPlayerInLevel(); var bench = workbench(helper);
        var placed = new LinkedHashMap<BlockPos, BlockState>();
        for (var block : List.of(Blocks.PISTON, Blocks.STICKY_PISTON)) for (var rotation : BlueprintRotation.values()) {
            var state = block.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST).setValue(BlockStateProperties.EXTENDED, true);
            var data = create(helper, List.of(new BlueprintBlock(BlockPos.ZERO, Blocks.REDSTONE_BLOCK.defaultBlockState(), null),
                    new BlueprintBlock(BlockPos.ZERO.east(), state, null)), 2, 1, 1);
            helper.assertTrue(data.requirements.get("item:minecraft:" + (block == Blocks.PISTON ? "piston" : "sticky_piston")).required == 1,
                    "A headless extended base must charge exactly one piston item");
            bench.clearContent(); var stack = BlueprintItem.loaded(data); bench.setItem(0, stack);
            bench.setItem(1, new ItemStack(Items.REDSTONE_BLOCK)); bench.setItem(2, new ItemStack(block.asItem())); bench.charge(player);
            helper.assertTrue(data.fullyCharged() && bench.getItem(1).isEmpty() && bench.getItem(2).isEmpty(), "Real materials must fully charge the headless piston");
            var origin = helper.absolutePos(new BlockPos(3 + rotation.ordinal() * 4, block == Blocks.PISTON ? 3 : 6, 3));
            var expected = state.rotate(rotation.vanilla);
            var pistonPos = origin.offset(rotation.apply(BlockPos.ZERO.east(), 2, 1));
            var headPos = pistonPos.relative(expected.getValue(BlockStateProperties.FACING));
            for (var pos : List.of(origin, origin.offset(rotation.apply(BlockPos.ZERO, 2, 1)), pistonPos, headPos))
                helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            var manager = PrefabLitematicaMod.placements(helper.getLevel().getServer()); manager.start(player, stack, origin, rotation); manager.tick();
            helper.assertTrue(helper.getLevel().getBlockState(pistonPos) == expected, "Placement must preserve the rotated extended base");
            helper.assertTrue(helper.getLevel().getBlockState(headPos).isAir(), "Placement must not synthesize a head outside the blueprint");
            helper.assertTrue(!data.fullyCharged() && !data.locked, "Successful placement must debit charge and release the reservation");
            var restored = new BlueprintManager(helper.getLevel().getServer()).get(data.id);
            helper.assertTrue(restored != null && restored.blocks.get(1).state() == state && restored.charge() == 0,
                    "Stored structure must retain the original extended state and consumed charge");
            placed.put(pistonPos, expected);
        }
        helper.runAfterDelay(3, () -> {
            placed.forEach((pos, state) -> {
                helper.assertTrue(helper.getLevel().getBlockState(pos) == state, "A powered headless extended base must remain extended after neighbor updates");
                helper.assertTrue(helper.getLevel().getBlockState(pos.relative(state.getValue(BlockStateProperties.FACING))).isAir(),
                        "Neighbor updates must not create an extra piston head");
            });
            helper.succeed();
        });
    }
    @GameTest public void movingPistonIsForbiddenBeforeMaterialAnalysis(GameTestHelper helper) throws Exception {
        var data = new BlueprintData(UUID.randomUUID(), "Moving piston", 1, 1, 1,
                List.of(new BlueprintBlock(BlockPos.ZERO, Blocks.MOVING_PISTON.defaultBlockState(), null)), new LinkedHashMap<>());
        boolean refused = false;
        try { BlueprintSerializer.decode(BlueprintSerializer.encode(data), data.id, PrefabLitematicaMod.CONFIG); }
        catch (java.io.IOException expected) { refused = expected.getMessage().contains("Forbidden technical block: minecraft:moving_piston"); }
        helper.assertTrue(refused, "Transient moving-piston block entities cannot be reconstructed from cosmetic NBT"); helper.succeed();
    }
    @GameTest public void pasteKeepsUnsupportedRedstoneGravityFireAndFluidStates(GameTestHelper helper) throws Exception {
        var states = List.of(
                Blocks.STICKY_PISTON.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.WEST).setValue(BlockStateProperties.EXTENDED, true),
                Blocks.REDSTONE_WIRE.defaultBlockState().setValue(BlockStateProperties.POWER, 15),
                Blocks.OBSERVER.defaultBlockState().setValue(BlockStateProperties.POWERED, true),
                Blocks.TORCH.defaultBlockState(), Blocks.SAND.defaultBlockState(), Blocks.FIRE.defaultBlockState(),
                Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true),
                Blocks.WATER.defaultBlockState().setValue(BlockStateProperties.LEVEL, 5));
        var blocks = new ArrayList<BlueprintBlock>();
        for (int x = 0; x < states.size(); x++) blocks.add(new BlueprintBlock(new BlockPos(x, 0, 0), states.get(x), null));
        var data = create(helper, blocks, states.size(), 1, 1); data.fill();
        var player = helper.makeMockServerPlayerInLevel(); var origin = helper.absolutePos(new BlockPos(2, 5, 2));
        for (var pos : BlockPos.betweenClosed(origin.offset(-1, -1, -1), origin.offset(states.size(), 1, 1)))
            helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        var scan = new BlueprintPreviewScan(player, data, origin, BlueprintRotation.NONE); while (!scan.tick(1)) {}
        helper.assertTrue(scan.clear(), "Paste preview must allow stored states that require update suppression");
        var manager = PrefabLitematicaMod.placements(helper.getLevel().getServer());
        manager.start(player, BlueprintItem.loaded(data), origin, BlueprintRotation.NONE); manager.tick();
        for (int x = 0; x < states.size(); x++) {
            var pos = origin.east(x); var state = states.get(x);
            helper.assertTrue(helper.getLevel().getBlockState(pos) == state, "Paste must copy the exact state at " + pos);
            helper.assertTrue(!helper.getLevel().getBlockTicks().hasScheduledTick(pos, state.getBlock()), "Paste must not manufacture block ticks");
            if (!state.getFluidState().isEmpty()) helper.assertTrue(!helper.getLevel().getFluidTicks().hasScheduledTick(pos, state.getFluidState().getType()), "Paste must not manufacture fluid ticks");
        }
        helper.runAfterDelay(5, () -> {
            for (int x = 0; x < states.size(); x++) helper.assertTrue(helper.getLevel().getBlockState(origin.east(x)) == states.get(x), "Stored states must survive ticks after paste");
            helper.assertTrue(helper.getLevel().getBlockState(origin.east(states.size())).isAir(), "Paste must not create blocks outside the blueprint");
            helper.succeed();
        });
    }
    @GameTest public void pasteDoesNotTriggerOutsidePistonsAndNormalUpdatesStillWork(GameTestHelper helper) throws Exception {
        var data = create(helper, List.of(new BlueprintBlock(BlockPos.ZERO, Blocks.REDSTONE_BLOCK.defaultBlockState(), null)), 1, 1, 1); data.fill();
        var player = helper.makeMockServerPlayerInLevel(); var origin = helper.absolutePos(new BlockPos(2, 4, 2));
        var pistonPos = origin.east();
        var piston = Blocks.PISTON.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST);
        for (var pos : BlockPos.betweenClosed(origin.offset(-1, -1, -1), origin.offset(3, 1, 1))) helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(pistonPos, piston);
        var manager = PrefabLitematicaMod.placements(helper.getLevel().getServer());
        manager.start(player, BlueprintItem.loaded(data), origin, BlueprintRotation.NONE); manager.tick();
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(helper.getLevel().getBlockState(pistonPos) == piston && helper.getLevel().getBlockState(pistonPos.east()).isAir(), "Paste must not activate a piston outside its reservation");
            helper.getLevel().updateNeighborsAt(origin, Blocks.REDSTONE_BLOCK);
            helper.runAfterDelay(3, () -> {
                helper.assertTrue(helper.getLevel().getBlockState(pistonPos).getValue(BlockStateProperties.EXTENDED), "Normal world updates must work after paste");
                helper.assertTrue(helper.getLevel().getBlockState(pistonPos.east()).is(Blocks.PISTON_HEAD), "A later external update may extend the piston normally");
                helper.succeed();
            });
        });
    }
    @GameTest public void replacePasteResetsIdenticalBlockEntitiesWithoutDropsOrComparatorUpdates(GameTestHelper helper) throws Exception {
        var player = helper.makeMockServerPlayerInLevel(); var origin = helper.absolutePos(new BlockPos(2, 4, 2));
        var chestState = Blocks.CHEST.defaultBlockState();
        var comparatorPos = origin.south();
        for (var pos : BlockPos.betweenClosed(origin.offset(-1, -1, -1), origin.offset(1, 1, 3))) helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(comparatorPos.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(origin, chestState);
        var old = (net.minecraft.world.level.block.entity.ChestBlockEntity) helper.getLevel().getBlockEntity(origin);
        var oldTag = new net.minecraft.nbt.CompoundTag(); oldTag.putString("CustomName", "Old inventory");
        old.loadCustomOnly(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), oldTag));
        old.setItem(0, new ItemStack(Items.DIAMOND, 64));
        helper.assertTrue(old.getCustomName() != null, "Replacement fixture must contain old metadata");
        var comparator = Blocks.COMPARATOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH).setValue(BlockStateProperties.POWERED, true);
        helper.getLevel().setBlock(comparatorPos, comparator, net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
        var data = create(helper, List.of(new BlueprintBlock(BlockPos.ZERO, chestState, new net.minecraft.nbt.CompoundTag())), 1, 1, 1); data.fill();
        String previousMode = PrefabLitematicaMod.CONFIG.placementMode;
        try {
            PrefabLitematicaMod.CONFIG.placementMode = "REPLACE";
            var manager = PrefabLitematicaMod.placements(helper.getLevel().getServer());
            manager.start(player, BlueprintItem.loaded(data), origin, BlueprintRotation.NONE); manager.tick();
        } finally { PrefabLitematicaMod.CONFIG.placementMode = previousMode; }
        var fresh = (net.minecraft.world.level.block.entity.ChestBlockEntity) helper.getLevel().getBlockEntity(origin);
        helper.assertTrue(fresh != old && fresh.isEmpty() && fresh.getCustomName() == null, "Identical chest paste must discard old inventory and metadata");
        helper.assertTrue(!helper.getLevel().getBlockTicks().hasScheduledTick(comparatorPos, Blocks.COMPARATOR), "Reset and NBT restoration must not notify outside comparators");
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(helper.getLevel().getBlockState(comparatorPos) == comparator, "Outside comparator must keep its state after paste");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(origin).inflate(2)).isEmpty(), "Replacement must not drop discarded inventory or the old chest");
            helper.succeed();
        });
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
        var data2=create(helper,List.of(new BlueprintBlock(BlockPos.ZERO,Blocks.OAK_PLANKS.defaultBlockState(),null),new BlueprintBlock(new BlockPos(16,0,0),Blocks.STONE.defaultBlockState(),null)),17,1,1);data2.fill();
        var origin2=new BlockPos(((origin.getX() >> 4) + 1) << 4, origin.getY(), origin.getZ() + 2);
        for(var pos:BlockPos.betweenClosed(origin2,origin2.east(16))) helper.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        var task=new dev.tensin.prefablitematica.placement.BlueprintPlacementTask(manager,player,item,data2,origin2,BlueprintRotation.NONE);
        int previous=0;boolean done=false;
        for(int tick=0;tick<100&&!done;tick++){
            done=task.tick(1);int placed=(helper.getLevel().getBlockState(origin2).isAir()?0:1)+(helper.getLevel().getBlockState(origin2.east(16)).isAir()?0:1);
            helper.assertTrue(placed-previous<=1,"A one-block target must not paste two separate chunks");previous=placed;
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
    @GameTest public void projectionFindsObstaclesInEmptyCellsWithoutLockingOrDebiting(GameTestHelper helper) throws Exception {
        var data = create(helper, List.of(new BlueprintBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null)), 3, 1, 1);
        data.fill(); var player = helper.makeMockServerPlayerInLevel(); var origin = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlockAndUpdate(origin, Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(origin.east(), Blocks.DIAMOND_BLOCK.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(origin.east(2), Blocks.AIR.defaultBlockState());
        var scan = new BlueprintPreviewScan(player, data, origin, BlueprintRotation.NONE);
        helper.assertTrue(!scan.tick(1), "Scan must obey its tick budget"); while (!scan.tick(1)) {}
        helper.assertTrue(scan.conflicts().get(1) && scan.conflicts().cardinality() == 1, "Occupied empty schematic cell must be marked");
        helper.assertTrue(data.fullyCharged() && !data.locked && helper.getLevel().getBlockState(origin).isAir(), "Preview must not lock, debit or place");
        helper.getLevel().setBlockAndUpdate(origin.east(), Blocks.AIR.defaultBlockState());
        scan = new BlueprintPreviewScan(player, data, origin, BlueprintRotation.NONE); while (!scan.tick(10)) {}
        helper.assertTrue(scan.clear(), "Removing the obstacle must allow a subsequent scan"); helper.succeed();
    }
    @GameTest public void projectionAllowsUnsupportedPasteStatesAndRotatesAllThreeAxes(GameTestHelper helper) throws Exception {
        var data = create(helper, List.of(new BlueprintBlock(BlockPos.ZERO, Blocks.TORCH.defaultBlockState(), null)), 1, 1, 1);
        var player = helper.makeMockServerPlayerInLevel(); var origin = helper.absolutePos(new BlockPos(1, 3, 1));
        helper.getLevel().setBlockAndUpdate(origin, Blocks.AIR.defaultBlockState()); helper.getLevel().setBlockAndUpdate(origin.below(), Blocks.AIR.defaultBlockState());
        var scan = new BlueprintPreviewScan(player, data, origin, BlueprintRotation.NONE); while (!scan.tick(10)) {}
        helper.assertTrue(scan.clear(), "Floating stored blocks must be allowed by paste preview");
        helper.getLevel().setBlockAndUpdate(origin.below(), Blocks.STONE.defaultBlockState());
        scan = new BlueprintPreviewScan(player, data, origin, BlueprintRotation.CW_90); while (!scan.tick(10)) {}
        helper.assertTrue(scan.clear(), "Supported blocks must also be allowed after rotation");
        var asymmetric = create(helper, List.of(new BlueprintBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null)), 2, 2, 3);
        var rotated = new BlueprintPreviewScan(player, asymmetric, origin.offset(1, 2, 3), BlueprintRotation.CW_90);
        helper.assertTrue(rotated.width == 3 && rotated.depth == 2 && rotated.position(11).equals(origin.offset(3, 3, 4)), "Rotated scan bounds and X/Y/Z coordinates must agree"); helper.succeed();
    }
    @GameTest public void onlyPanelCanStartPreviewAndConflictCannotConfirm(GameTestHelper helper) throws Exception {
        var data = create(helper, List.of(new BlueprintBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null)), 1, 1, 1); data.fill();
        var player = helper.makeMockServerPlayerInLevel(); var origin = helper.absolutePos(new BlockPos(1, 2, 1));
        var stack = BlueprintItem.loaded(data); player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        helper.getLevel().setBlockAndUpdate(origin, Blocks.DIAMOND_BLOCK.defaultBlockState());
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(origin.below()), net.minecraft.core.Direction.UP, origin.below(), false);
        stack.getItem().useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
        var previews = PrefabLitematicaMod.previews(helper.getLevel().getServer());
        helper.assertTrue(previews.session(player) == null && data.fullyCharged() && !data.locked, "Right-click must not start a projection or placement");
        stack.getItem().useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(previews.session(player) == null && BlueprintItem.rotation(stack) == 0, "Repeated use must not alter the building pose");
        previews.handle(player, new BlueprintPreviewPayload.Request(data.id, 0, BlueprintPreviewPayload.START, origin, 0));
        var session = previews.session(player);
        helper.assertTrue(session != null && !session.shown, "Opening a charged blueprint panel must initially hide the projection");
        previews.handle(player, new BlueprintPreviewPayload.Request(session.token, 0, BlueprintPreviewPayload.CONFIRM, origin, 0)); previews.tick();
        helper.assertTrue(!data.locked && !session.shown && data.fullyCharged(), "Execute must be rejected before showing the preview");
        previews.handle(player, new BlueprintPreviewPayload.Request(session.token, 0, BlueprintPreviewPayload.SHOW, origin, 0));
        previews.tick();
        previews.handle(player, new BlueprintPreviewPayload.Request(session.token, 0, BlueprintPreviewPayload.CONFIRM, origin, 0)); previews.tick();
        helper.assertTrue(previews.session(player) == session && !data.locked && data.fullyCharged() && helper.getLevel().getBlockState(origin).is(Blocks.DIAMOND_BLOCK), "Conflicted confirmation must preserve projection, terrain and charge");
        previews.handle(player, new BlueprintPreviewPayload.Request(session.token, 0, BlueprintPreviewPayload.CANCEL, origin, 0));
        helper.assertTrue(previews.session(player) == null && data.fullyCharged(), "Cancel must keep the charged blueprint"); helper.succeed();
    }
    @GameTest public void confirmationRechecksChangedTerrainBeforePlacement(GameTestHelper helper) throws Exception {
        var data = create(helper, List.of(new BlueprintBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null)), 1, 1, 1); data.fill();
        var player = helper.makeMockServerPlayerInLevel(); var origin = helper.absolutePos(new BlockPos(1, 2, 1));
        var stack = BlueprintItem.loaded(data); player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        helper.getLevel().setBlockAndUpdate(origin, Blocks.AIR.defaultBlockState());
        var previews = PrefabLitematicaMod.previews(helper.getLevel().getServer()); previews.begin(player, stack, origin, BlueprintRotation.NONE);
        var session = previews.session(player);
        previews.handle(player, new BlueprintPreviewPayload.Request(session.token, 0, BlueprintPreviewPayload.SHOW, origin, 0)); previews.tick();
        helper.assertTrue(session.scan.clear(), "Initial clear area should pass after showing the preview");
        helper.getLevel().setBlockAndUpdate(origin, Blocks.DIAMOND_BLOCK.defaultBlockState());
        previews.handle(player, new BlueprintPreviewPayload.Request(session.token, 0, BlueprintPreviewPayload.CONFIRM, origin, 0)); previews.tick();
        helper.assertTrue(previews.session(player) == session && !session.scan.clear() && data.fullyCharged() && !data.locked, "A stale green preview cannot overwrite a new obstacle");
        helper.getLevel().setBlockAndUpdate(origin, Blocks.AIR.defaultBlockState());
        previews.handle(player, new BlueprintPreviewPayload.Request(session.token, 1, BlueprintPreviewPayload.MOVE, origin, 1)); previews.tick();
        helper.assertTrue(session.scan.clear(), "Moving/rotating must rescan the new pose");
        previews.handle(player, new BlueprintPreviewPayload.Request(session.token, 0, BlueprintPreviewPayload.CONFIRM, origin, 0));
        helper.assertTrue(!data.locked, "An obsolete pose must not confirm the updated projection");
        previews.handle(player, new BlueprintPreviewPayload.Request(session.token, 1, BlueprintPreviewPayload.CONFIRM, origin, 1)); previews.tick();
        helper.assertTrue(previews.session(player) == null && data.locked, "Only an explicit valid confirmation may start placement");
        PrefabLitematicaMod.placements(helper.getLevel().getServer()).tick();
        helper.assertTrue(helper.getLevel().getBlockState(origin).is(Blocks.STONE) && !data.fullyCharged(), "Confirmed placement must debit charge exactly once"); helper.succeed();
    }
    @GameTest public void projectionOutsideBoundsRemainsInspectableAndRejectsForgedToken(GameTestHelper helper) throws Exception {
        var data = create(helper, List.of(new BlueprintBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null)), 1, 1, 1); data.fill();
        var player = helper.makeMockServerPlayerInLevel(); var stack = BlueprintItem.loaded(data); player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        var origin = new BlockPos(0, helper.getLevel().getMaxY() + 1, 0); var previews = PrefabLitematicaMod.previews(helper.getLevel().getServer());
        previews.begin(player, stack, origin, BlueprintRotation.NONE); var session = previews.session(player);
        previews.handle(player, new BlueprintPreviewPayload.Request(session.token, 0, BlueprintPreviewPayload.SHOW, origin, 0)); previews.tick();
        helper.assertTrue(session != null && session.scan.conflicts().get(0) && !data.locked, "Out-of-bounds area must produce a red projection");
        previews.handle(player, new BlueprintPreviewPayload.Request(UUID.randomUUID(), 1, BlueprintPreviewPayload.MOVE, BlockPos.ZERO, 1));
        helper.assertTrue(session.origin.equals(origin) && session.revision == 0, "Forged session token must not move another projection");
        previews.disconnect(player); helper.assertTrue(previews.session(player) == null && data.fullyCharged(), "Disconnect must release preview without charging"); helper.succeed();
    }
    @GameTest public void openingPanelRequiresFullyChargedHeldBlueprint(GameTestHelper helper) throws Exception {
        var data = create(helper, List.of(new BlueprintBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null)), 1, 1, 1);
        var player = helper.makeMockServerPlayerInLevel(); var stack = BlueprintItem.loaded(data);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        var previews = PrefabLitematicaMod.previews(helper.getLevel().getServer()); var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        previews.handle(player, new BlueprintPreviewPayload.Request(data.id, 0, BlueprintPreviewPayload.START, pos, 0));
        helper.assertTrue(previews.session(player) == null && !data.locked, "Uncharged blueprint must not open a placement session");
        data.fill(); previews.handle(player, new BlueprintPreviewPayload.Request(UUID.randomUUID(), 0, BlueprintPreviewPayload.START, pos, 0));
        helper.assertTrue(previews.session(player) == null, "Start request must refer to a blueprint actually held by the player");
        previews.handle(player, new BlueprintPreviewPayload.Request(data.id, 0, BlueprintPreviewPayload.START, pos, 0)); previews.tick();
        helper.assertTrue(previews.session(player) != null && !previews.session(player).shown && data.fullyCharged(), "Opening the panel must not render, reserve or debit");
        previews.disconnect(player); helper.succeed();
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
