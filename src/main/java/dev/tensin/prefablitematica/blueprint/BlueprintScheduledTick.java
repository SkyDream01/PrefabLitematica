// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.blueprint;

import dev.tensin.prefablitematica.placement.BlueprintRotation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;

/** Preserve Litematica's saved trigger time, priority and sub-tick ordering verbatim. */
public record BlueprintScheduledTick(BlockPos position, boolean fluid, Identifier type, long trigger, int priority, long order) {
    public void schedule(ServerLevel world, BlockPos origin, BlueprintRotation rotation, int sizeX, int sizeZ) {
        BlockPos target = origin.offset(rotation.apply(position, sizeX, sizeZ));
        if (fluid) {
            var value = BuiltInRegistries.FLUID.getValue(type);
            if (world.getFluidState(target).getType() == value)
                world.getFluidTicks().schedule(new ScheduledTick<>(value, target, trigger, TickPriority.byValue(priority), order));
        } else {
            var value = BuiltInRegistries.BLOCK.getValue(type);
            if (world.getBlockState(target).getBlock() == value)
                world.getBlockTicks().schedule(new ScheduledTick<>(value, target, trigger, TickPriority.byValue(priority), order));
        }
    }
}
