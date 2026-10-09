// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.blueprint;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Set;

/** Blocks shown in projections but preserved as-is when a blueprint is charged and placed. */
public final class BlueprintBlockPolicy {
    private static final Set<Block> PRESERVED = Set.of(
            Blocks.BEDROCK,
            Blocks.END_PORTAL_FRAME,
            Blocks.END_PORTAL,
            Blocks.END_GATEWAY,
            Blocks.BUDDING_AMETHYST,
            Blocks.TRIAL_SPAWNER,
            Blocks.VAULT,
            Blocks.SPAWNER
    );

    private BlueprintBlockPolicy() {}

    public static boolean isPreserved(BlockState state) {
        return PRESERVED.contains(state.getBlock());
    }
}
