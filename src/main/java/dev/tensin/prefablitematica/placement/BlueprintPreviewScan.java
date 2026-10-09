// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.placement;

import dev.tensin.prefablitematica.PrefabLitematicaMod;
import dev.tensin.prefablitematica.blueprint.BlueprintData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Bounded, non-mutating scan of the entire paste volume, including empty schematic cells. */
public final class BlueprintPreviewScan {
    private final ServerPlayer player;
    public final BlockPos origin;
    public final BlueprintRotation rotation;
    public final int width, depth, volume;
    private final BitSet conflicts = new BitSet();
    private int index;
    private String firstProblem = "";

    public BlueprintPreviewScan(ServerPlayer player, BlueprintData data, BlockPos origin, BlueprintRotation rotation) {
        this.player = player; this.origin = origin.immutable(); this.rotation = rotation;
        width = rotation.ordinal() % 2 == 0 ? data.sizeX : data.sizeZ;
        depth = rotation.ordinal() % 2 == 0 ? data.sizeZ : data.sizeX;
        volume = width * data.sizeY * depth;
    }
    public boolean tick(int budget) {
        while (budget-- > 0 && index < volume) {
            int cell = index++;
            BlockPos pos = position(cell);
            String problem = PlacementValidator.problem(player, pos);
            if (problem == null && PrefabLitematicaMod.placements(player.level().getServer()).reserved(player.level(), pos)) problem = "Another placement reserves this area";
            if (problem != null) mark(cell, problem);
        }
        return complete();
    }
    private void mark(int cell, String problem) { conflicts.set(cell); if (firstProblem.isEmpty()) firstProblem = problem; }
    public BlockPos position(int cell) { return origin.offset(cell % width, cell / (width * depth), (cell / width) % depth); }
    public boolean complete() { return index == volume; }
    public boolean clear() { return complete() && conflicts.isEmpty(); }
    public BitSet conflicts() { return (BitSet) conflicts.clone(); }
    public String firstProblem() { return firstProblem; }
}
