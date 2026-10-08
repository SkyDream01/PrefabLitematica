// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.placement;

import dev.tensin.prefablitematica.PrefabLitematicaMod;
import net.fabricmc.fabric.api.event.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

public final class PlacementValidator {
    /** Claim/protection mods can veto each cell, including cells in the empty bounding volume. */
    public interface Permission { boolean allow(ServerPlayer player, BlockPos position); }
    public static final Event<Permission> ALLOW = EventFactory.createArrayBacked(Permission.class,
            listeners -> (player, pos) -> { for (Permission listener : listeners) if (!listener.allow(player, pos)) return false; return true; });
    private PlacementValidator() {}
    public static void validate(ServerPlayer player, BlockPos pos) {
        var world = player.level();
        if (!player.mayBuild() || player.isSpectator()) throw new IllegalArgumentException("Building is not permitted");
        if (pos.getY() < world.getMinY() || pos.getY() > world.getMaxY() || !world.getWorldBorder().isWithinBounds(pos))
            throw new IllegalArgumentException("Outside world bounds: " + pos.toShortString());
        if (!world.hasChunkAt(pos)) throw new IllegalArgumentException("Load the entire building area first");
        if (!world.mayInteract(player, pos) || !ALLOW.invoker().allow(player, pos)) throw new IllegalArgumentException("Protected position: " + pos.toShortString());
        var existing = world.getBlockState(pos);
        if (PrefabLitematicaMod.CONFIG.placementMode.equals("SAFE") && !existing.isAir() && !existing.canBeReplaced())
            throw new IllegalArgumentException("Occupied position: " + pos.toShortString());
        if (existing.getDestroySpeed(world, pos) < 0 && !existing.isAir()) throw new IllegalArgumentException("Unbreakable position: " + pos.toShortString());
    }
}
