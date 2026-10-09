// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.material;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import java.util.*;

/** Explicit state-aware costs; unknown non-item blocks must never become free materials. */
public final class BlockMaterialResolver {
    public record Cost(Item item, int count) {}
    public List<Cost> resolveAll(BlockState state, Map<BlockPos, BlockState> structure, BlockPos pos) {
        if (state.getBlock() instanceof FlowerPotBlock pot && pot.getPotted() != Blocks.AIR)
            return List.of(new Cost(Items.FLOWER_POT, 1), new Cost(pot.getPotted().asItem(), 1));
        if (state.getBlock() instanceof CandleCakeBlock) {
            for (var color : DyeColor.values()) {
                var candle = (CandleBlock) Blocks.DYED_CANDLE.pick(color);
                if (CandleCakeBlock.byCandle(candle).getBlock() == state.getBlock()) return List.of(new Cost(Items.CAKE, 1), new Cost(candle.asItem(), 1));
            }
            return List.of(new Cost(Items.CAKE, 1), new Cost(Items.CANDLE, 1));
        }
        if (state.is(Blocks.POWDER_SNOW_CAULDRON)) return List.of(new Cost(Items.CAULDRON, 1), new Cost(Items.POWDER_SNOW_BUCKET, 1));
        return List.of(resolve(state, structure, pos));
    }
    public Cost resolve(BlockState state, Map<BlockPos, BlockState> structure, BlockPos pos) {
        Block block = state.getBlock();
        if (state.isAir() || block instanceof BaseFireBlock || block instanceof LiquidBlock || block instanceof BubbleColumnBlock)
            return new Cost(Items.AIR, 0); // Fluids are charged separately, including water inside bubble columns.
        if (state.is(Blocks.MOVING_PISTON))
            throw new IllegalArgumentException("Moving piston at " + pos.toShortString() + "; save the schematic after pistons stop moving");
        if (block instanceof PistonHeadBlock) {
            var facing = state.getValue(BlockStateProperties.FACING);
            BlockState base = structure.get(pos.relative(facing.getOpposite()));
            Block expected = state.getValue(BlockStateProperties.PISTON_TYPE) == PistonType.STICKY ? Blocks.STICKY_PISTON : Blocks.PISTON;
            if (state.getValue(BlockStateProperties.SHORT) || base == null || !base.is(expected)
                    || !base.getValue(BlockStateProperties.EXTENDED) || base.getValue(BlockStateProperties.FACING) != facing)
                throw new IllegalArgumentException("Unpaired or moving piston head at " + pos.toShortString());
            return new Cost(Items.AIR, 0); // The matching base pays for the entire piston.
        }
        if ((state.is(Blocks.PISTON) || state.is(Blocks.STICKY_PISTON)) && state.getValue(BlockStateProperties.EXTENDED)) {
            var facing = state.getValue(BlockStateProperties.FACING);
            BlockState head = structure.get(pos.relative(facing));
            PistonType expected = state.is(Blocks.STICKY_PISTON) ? PistonType.STICKY : PistonType.DEFAULT;
            // A static extended base can be headless or cropped at the schematic boundary.
            // Preserve that state and charge its item without creating a head outside the blueprint.
            if (head != null && !head.isAir() && (!head.is(Blocks.PISTON_HEAD) || head.getValue(BlockStateProperties.FACING) != facing
                    || head.getValue(BlockStateProperties.PISTON_TYPE) != expected || head.getValue(BlockStateProperties.SHORT)))
                throw new IllegalArgumentException("Incomplete extended piston at " + pos.toShortString());
        }
        if (block instanceof DoorBlock || block instanceof DoublePlantBlock && (!(block instanceof PitcherCropBlock) || state.getValue(BlockStateProperties.AGE_4) >= 3)) {
            if (state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                BlockState lower = structure.get(pos.below());
                if (lower == null || lower.getBlock() != block || lower.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.LOWER)
                    throw new IllegalArgumentException("Unpaired upper half at " + pos);
                return new Cost(Items.AIR, 0);
            }
            BlockState upper = structure.get(pos.above());
            if (upper == null || upper.getBlock() != block || upper.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.UPPER)
                throw new IllegalArgumentException("Unpaired lower half at " + pos);
        }
        if (block instanceof BedBlock) {
            var facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            boolean head = state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD;
            BlockState other = structure.get(pos.relative(head ? facing.getOpposite() : facing));
            if (other == null || other.getBlock() != block || other.getValue(BlockStateProperties.BED_PART) == state.getValue(BlockStateProperties.BED_PART)
                    || other.getValue(BlockStateProperties.HORIZONTAL_FACING) != facing)
                throw new IllegalArgumentException("Incomplete bed at " + pos);
            if (head) return new Cost(Items.AIR, 0);
        }
        Item item = block.asItem();
        if (block == Blocks.REDSTONE_WIRE) item = Items.REDSTONE;
        else if (block instanceof WallTorchBlock) item = block instanceof RedstoneWallTorchBlock ? Items.REDSTONE_TORCH
                : block == Blocks.SOUL_WALL_TORCH ? Items.SOUL_TORCH : Items.TORCH;
        else if (block == Blocks.WHEAT) item = Items.WHEAT_SEEDS;
        else if (block == Blocks.CARROTS) item = Items.CARROT;
        else if (block == Blocks.POTATOES) item = Items.POTATO;
        else if (block == Blocks.BEETROOTS) item = Items.BEETROOT_SEEDS;
        else if (block == Blocks.MELON_STEM || block == Blocks.ATTACHED_MELON_STEM) item = Items.MELON_SEEDS;
        else if (block == Blocks.PUMPKIN_STEM || block == Blocks.ATTACHED_PUMPKIN_STEM) item = Items.PUMPKIN_SEEDS;
        else if (block == Blocks.COCOA) item = Items.COCOA_BEANS;
        else if (block instanceof SweetBerryBushBlock) item = Items.SWEET_BERRIES;
        else if (block instanceof PitcherCropBlock) item = Items.PITCHER_POD;
        else if (block instanceof TorchflowerCropBlock) item = Items.TORCHFLOWER_SEEDS;
        else if (block == Blocks.TALL_SEAGRASS) item = Items.SEAGRASS;
        else if (block == Blocks.KELP_PLANT) item = Items.KELP;
        else if (block == Blocks.WEEPING_VINES_PLANT) item = Items.WEEPING_VINES;
        else if (block == Blocks.TWISTING_VINES_PLANT) item = Items.TWISTING_VINES;
        else if (block == Blocks.CAVE_VINES || block == Blocks.CAVE_VINES_PLANT) item = Items.GLOW_BERRIES;
        else if (block == Blocks.BAMBOO_SAPLING) item = Items.BAMBOO;
        else if (block == Blocks.FROSTED_ICE) item = Items.ICE;
        else if (block instanceof AbstractCauldronBlock) item = Items.CAULDRON;
        else if (block == Blocks.POWDER_SNOW) item = Items.POWDER_SNOW_BUCKET;
        else if (block == Blocks.TRIPWIRE) item = Items.STRING;
        else if (block instanceof WallSignBlock sign) item = sign.asItem();
        if (item == Items.AIR) throw new IllegalArgumentException("No safe material resolver for "
                + BuiltInRegistries.BLOCK.getKey(block) + " at " + pos.toShortString());
        int count = 1;
        if (block instanceof SlabBlock && state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) count = 2;
        else if (block instanceof SnowLayerBlock) count = state.getValue(BlockStateProperties.LAYERS);
        else if (block instanceof CandleBlock) count = state.getValue(BlockStateProperties.CANDLES);
        else if (block == Blocks.SEA_PICKLE) count = state.getValue(BlockStateProperties.PICKLES);
        else if (block == Blocks.TURTLE_EGG) count = state.getValue(BlockStateProperties.EGGS);
        else if (state.hasProperty(BlockStateProperties.FLOWER_AMOUNT)) count = state.getValue(BlockStateProperties.FLOWER_AMOUNT);
        else if (state.hasProperty(BlockStateProperties.SEGMENT_AMOUNT)) count = state.getValue(BlockStateProperties.SEGMENT_AMOUNT);
        return new Cost(item, count);
    }
}
