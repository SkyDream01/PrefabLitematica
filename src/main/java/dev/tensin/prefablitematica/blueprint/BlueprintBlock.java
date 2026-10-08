// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public record BlueprintBlock(BlockPos relativePos, BlockState state, CompoundTag blockEntity) {
    public FluidState fluid() { return state.getFluidState(); }
}
