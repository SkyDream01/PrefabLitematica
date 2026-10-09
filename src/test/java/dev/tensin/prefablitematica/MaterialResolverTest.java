// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica;

import com.google.gson.JsonParser;
import dev.tensin.prefablitematica.material.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import org.junit.jupiter.api.*;
import java.io.InputStreamReader;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MaterialResolverTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private final BlockMaterialResolver resolver = new BlockMaterialResolver();

    @Test void powderSnowAndCauldronChargeSeparateBucketsForEveryFillLevel() {
        assertEquals(new BlockMaterialResolver.Cost(Items.POWDER_SNOW_BUCKET, 1),
                resolver.resolve(Blocks.POWDER_SNOW.defaultBlockState(), Map.of(), BlockPos.ZERO));
        for (var state : Blocks.POWDER_SNOW_CAULDRON.getStateDefinition().getPossibleStates()) {
            assertEquals(List.of(new BlockMaterialResolver.Cost(Items.CAULDRON, 1),
                    new BlockMaterialResolver.Cost(Items.POWDER_SNOW_BUCKET, 1)), resolver.resolveAll(state, Map.of(), BlockPos.ZERO));
            var fluids = new FluidMaterialResolver(); fluids.accept(state);
            var requirements = new LinkedHashMap<String, MaterialRequirement>(); fluids.finish(requirements);
            assertTrue(requirements.isEmpty(), "Powder snow must not add water bucket charges");
        }
    }

    @Test void conversionsChargeEveryUseAndNeverMixWoodShapes() {
        var rules = MaterialConversionRegistry.recipes();
        assertTrue(rules.contains(new MaterialConversionRegistry.Recipe(Items.DIRT, Items.GRASS_BLOCK,
                MaterialConversionRegistry.Tool.NONE, 0, "grass")));
        assertTrue(rules.contains(new MaterialConversionRegistry.Recipe(Items.PUMPKIN, Items.CARVED_PUMPKIN,
                MaterialConversionRegistry.Tool.SHEARS, 1, "carve")));
        assertTrue(rules.contains(new MaterialConversionRegistry.Recipe(Items.COARSE_DIRT, Items.FARMLAND,
                MaterialConversionRegistry.Tool.HOE, 2, "farmland_twice")));
        assertTrue(rules.stream().anyMatch(r -> r.source() == Items.OAK_LOG && r.target() == Items.STRIPPED_OAK_LOG && r.durability() == 1));
        assertTrue(rules.stream().anyMatch(r -> r.source() == Items.OAK_WOOD && r.target() == Items.STRIPPED_OAK_WOOD && r.durability() == 1));
        assertFalse(rules.stream().anyMatch(r -> r.source() == Items.OAK_LOG && r.target() == Items.STRIPPED_OAK_WOOD));
        var source = BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("waxed_oxidized_copper_bulb"));
        var target = BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("copper_bulb"));
        assertTrue(rules.stream().anyMatch(r -> r.source() == source && r.target() == target && r.durability() == 4));
    }

    @Test void bubbleColumnsChargeWaterOnceWithoutAnExtraBlockItem() {
        var fluids = new FluidMaterialResolver();
        for (boolean drag : new boolean[]{false, true}) {
            var state = Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BlockStateProperties.DRAG, drag);
            assertEquals(0, resolver.resolve(state, Map.of(), BlockPos.ZERO).count());
            for (int i = 0; i < 7; i++) fluids.accept(state);
        }
        var requirements = new LinkedHashMap<String, MaterialRequirement>(); fluids.finish(requirements);
        assertEquals(1, requirements.size());
        assertEquals(2, requirements.get("item:minecraft:water_bucket").required);
    }

    @Test void plantBodyAndGrowthStatesUseThePlantingItem() {
        var plantingItems = Map.of(Blocks.KELP_PLANT, Items.KELP,
                Blocks.WEEPING_VINES_PLANT, Items.WEEPING_VINES,
                Blocks.TWISTING_VINES_PLANT, Items.TWISTING_VINES,
                Blocks.CAVE_VINES_PLANT, Items.GLOW_BERRIES, Blocks.CAVE_VINES, Items.GLOW_BERRIES,
                Blocks.BAMBOO_SAPLING, Items.BAMBOO);
        plantingItems.forEach((block, item) -> {
            for (var state : block.getStateDefinition().getPossibleStates()) {
                var cost = resolver.resolve(state, Map.of(), BlockPos.ZERO);
                assertEquals(item, cost.item()); assertEquals(1, cost.count());
            }
        });
    }

    @Test void tallSeagrassChargesOneSeagrassAndRequiresBothHalves() {
        var lower = Blocks.TALL_SEAGRASS.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
        var upper = lower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
        var structure = Map.of(BlockPos.ZERO, lower, BlockPos.ZERO.above(), upper);
        assertEquals(new BlockMaterialResolver.Cost(Items.SEAGRASS, 1), resolver.resolve(lower, structure, BlockPos.ZERO));
        assertEquals(0, resolver.resolve(upper, structure, BlockPos.ZERO.above()).count());
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(lower, Map.of(), BlockPos.ZERO));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(upper, Map.of(), BlockPos.ZERO.above()));
    }

    @Test void frostedIceChargesIceForEveryAge() {
        for (var state : Blocks.FROSTED_ICE.getStateDefinition().getPossibleStates())
            assertEquals(new BlockMaterialResolver.Cost(Items.ICE, 1), resolver.resolve(state, Map.of(), BlockPos.ZERO));
    }

    @Test void extendedPistonsChargeOnlyTheMatchingBaseInEveryDirection() {
        for (var direction : Direction.values()) for (var type : PistonType.values()) {
            var block = type == PistonType.STICKY ? Blocks.STICKY_PISTON : Blocks.PISTON;
            var base = block.defaultBlockState().setValue(BlockStateProperties.FACING, direction).setValue(BlockStateProperties.EXTENDED, true);
            var head = Blocks.PISTON_HEAD.defaultBlockState().setValue(BlockStateProperties.FACING, direction).setValue(BlockStateProperties.PISTON_TYPE, type);
            var headPos = BlockPos.ZERO.relative(direction); var structure = Map.of(BlockPos.ZERO, base, headPos, head);
            assertEquals(new BlockMaterialResolver.Cost(block.asItem(), 1), resolver.resolve(base, structure, BlockPos.ZERO));
            assertEquals(0, resolver.resolve(head, structure, headPos).count());
            assertThrows(IllegalArgumentException.class, () -> resolver.resolve(head, Map.of(), headPos));
            for (var invalid : List.of(head.setValue(BlockStateProperties.FACING, direction.getOpposite()),
                    head.setValue(BlockStateProperties.PISTON_TYPE, type == PistonType.STICKY ? PistonType.DEFAULT : PistonType.STICKY),
                    head.setValue(BlockStateProperties.SHORT, true))) {
                var invalidStructure = Map.of(BlockPos.ZERO, base, headPos, invalid);
                assertThrows(IllegalArgumentException.class, () -> resolver.resolve(base, invalidStructure, BlockPos.ZERO));
                assertThrows(IllegalArgumentException.class, () -> resolver.resolve(invalid, invalidStructure, headPos));
            }
            var retracted = base.setValue(BlockStateProperties.EXTENDED, false);
            assertThrows(IllegalArgumentException.class, () -> resolver.resolve(head, Map.of(BlockPos.ZERO, retracted), headPos));
            assertEquals(new BlockMaterialResolver.Cost(block.asItem(), 1), resolver.resolve(retracted, Map.of(), BlockPos.ZERO));
        }
    }

    @Test void headlessExtendedPistonsChargeOneBaseInEveryDirection() {
        for (var direction : Direction.values()) for (var block : List.of(Blocks.PISTON, Blocks.STICKY_PISTON)) {
            var base = block.defaultBlockState().setValue(BlockStateProperties.FACING, direction).setValue(BlockStateProperties.EXTENDED, true);
            var headPos = BlockPos.ZERO.relative(direction);
            var expected = new BlockMaterialResolver.Cost(block.asItem(), 1);
            assertEquals(expected, resolver.resolve(base, Map.of(BlockPos.ZERO, base), BlockPos.ZERO));
            for (var air : List.of(Blocks.AIR, Blocks.CAVE_AIR, Blocks.VOID_AIR))
                assertEquals(expected, resolver.resolve(base, Map.of(BlockPos.ZERO, base, headPos, air.defaultBlockState()), BlockPos.ZERO));
            assertThrows(IllegalArgumentException.class, () -> resolver.resolve(base,
                    Map.of(BlockPos.ZERO, base, headPos, Blocks.STONE.defaultBlockState()), BlockPos.ZERO));
        }
    }

    @Test void movingPistonsAndUnknownNonItemStatesAreRejectedWithUsefulDetails() {
        var position = new BlockPos(2, 3, 4);
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(Blocks.MOVING_PISTON.defaultBlockState(), Map.of(), position)).getMessage().contains("stop moving"));
        var message = assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(Blocks.NETHER_PORTAL.defaultBlockState(), Map.of(), position)).getMessage();
        assertTrue(message.contains("minecraft:nether_portal")); assertTrue(message.contains(position.toShortString()));
    }

    /** Registry coverage catches newly added body/state blocks with no independent item. */
    @Test void allAllowedVanillaStatesHaveMaterialRules() throws Exception {
        Set<String> forbidden = new HashSet<>();
        try (var stream = getClass().getResourceAsStream("/data/prefablitematica/tags/block/forbidden_blocks.json")) {
            assertNotNull(stream);
            for (var entry : JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject().getAsJsonArray("values"))
                forbidden.add(entry.getAsString());
        }
        int checked = 0;
        for (var block : BuiltInRegistries.BLOCK) {
            var id = BuiltInRegistries.BLOCK.getKey(block);
            if (!id.getNamespace().equals("minecraft") || forbidden.contains(id.toString())) continue;
            for (var state : block.getStateDefinition().getPossibleStates()) {
                if (state.is(Blocks.PISTON_HEAD) && state.getValue(BlockStateProperties.SHORT)) continue; // In-motion shape, deliberately rejected above.
                var structure = pairedStructure(state);
                var costs = assertDoesNotThrow(() -> resolver.resolveAll(state, structure, BlockPos.ZERO), state::toString);
                for (var cost : costs) {
                    assertTrue(cost.count() >= 0, state::toString);
                    if (cost.count() > 0) assertNotEquals(Items.AIR, cost.item(), state::toString);
                }
                checked++;
            }
        }
        assertTrue(checked > 10000, "Coverage must include the real vanilla state registry");
        System.out.println("Material rules checked for " + checked + " allowed vanilla block states");
    }

    private static Map<BlockPos, BlockState> pairedStructure(BlockState state) {
        var structure = new HashMap<BlockPos, BlockState>(); structure.put(BlockPos.ZERO, state);
        if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof DoublePlantBlock) {
            boolean upper = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
            structure.put(upper ? BlockPos.ZERO.below() : BlockPos.ZERO.above(),
                    state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, upper ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER));
        } else if (state.getBlock() instanceof BedBlock) {
            boolean head = state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD;
            var facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            structure.put(BlockPos.ZERO.relative(head ? facing.getOpposite() : facing),
                    state.setValue(BlockStateProperties.BED_PART, head ? BedPart.FOOT : BedPart.HEAD));
        } else if (state.is(Blocks.PISTON_HEAD)) {
            var base = state.getValue(BlockStateProperties.PISTON_TYPE) == PistonType.STICKY ? Blocks.STICKY_PISTON : Blocks.PISTON;
            var facing = state.getValue(BlockStateProperties.FACING);
            structure.put(BlockPos.ZERO.relative(facing.getOpposite()),
                    base.defaultBlockState().setValue(BlockStateProperties.FACING, facing).setValue(BlockStateProperties.EXTENDED, true));
        } else if ((state.is(Blocks.PISTON) || state.is(Blocks.STICKY_PISTON)) && state.getValue(BlockStateProperties.EXTENDED)) {
            var facing = state.getValue(BlockStateProperties.FACING);
            structure.put(BlockPos.ZERO.relative(facing), Blocks.PISTON_HEAD.defaultBlockState().setValue(BlockStateProperties.FACING, facing)
                    .setValue(BlockStateProperties.PISTON_TYPE, state.is(Blocks.STICKY_PISTON) ? PistonType.STICKY : PistonType.DEFAULT));
        }
        return structure;
    }
}
