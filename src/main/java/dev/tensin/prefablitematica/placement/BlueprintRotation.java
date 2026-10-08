// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;

public enum BlueprintRotation {
    NONE(Rotation.NONE), CW_90(Rotation.CLOCKWISE_90), CW_180(Rotation.CLOCKWISE_180), CW_270(Rotation.COUNTERCLOCKWISE_90);
    public final Rotation vanilla;
    BlueprintRotation(Rotation rotation) { vanilla = rotation; }
    public BlockPos apply(BlockPos p, int sizeX, int sizeZ) {
        return switch (this) {
            case NONE -> p;
            case CW_90 -> new BlockPos(sizeZ - 1 - p.getZ(), p.getY(), p.getX());
            case CW_180 -> new BlockPos(sizeX - 1 - p.getX(), p.getY(), sizeZ - 1 - p.getZ());
            case CW_270 -> new BlockPos(p.getZ(), p.getY(), sizeX - 1 - p.getX());
        };
    }
}
