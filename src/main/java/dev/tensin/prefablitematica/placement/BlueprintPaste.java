// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.placement;

import dev.tensin.prefablitematica.blueprint.BlueprintBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import java.util.List;

/** Dedicated-server counterpart of Litematica's per-cell direct paste; restore NBT immediately. */
public final class BlueprintPaste {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;
    private BlueprintPaste() {}
    public static void paste(ServerLevel world, List<BlueprintBlock> blocks) {
        for (var block : blocks) {
            var pos = block.relativePos();
            if (!world.hasChunkAt(pos)) throw new IllegalStateException("Chunk unloaded during placement");
            if (world.getBlockEntity(pos) != null) set(world, new BlueprintBlock(pos, Blocks.BARRIER.defaultBlockState(), null));
            set(world, block);
            if (block.blockEntity() != null) {
                var entity = world.getBlockEntity(pos);
                if (entity == null) throw new IllegalStateException("Missing block entity during paste at " + pos);
                entity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, world.registryAccess(), block.blockEntity().copy()));
                // Mark and sync without setChanged(), which would notify comparators.
                world.blockEntityChanged(pos);
                world.sendBlockUpdated(pos, block.state(), block.state(), Block.UPDATE_CLIENTS);
            }
        }
    }
    private static void set(ServerLevel world, BlueprintBlock block) {
        if (!world.setBlock(block.relativePos(), block.state(), FLAGS) && world.getBlockState(block.relativePos()) != block.state())
            throw new IllegalStateException("World refused block placement");
    }
}
