// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.mixin;

import dev.tensin.prefablitematica.PrefabLitematicaMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

@Mixin(ServerExplosion.class)
public abstract class ServerExplosionReservationMixin {
    @Inject(method = "interactWithBlocks", at = @At("HEAD"))
    private void blueprint$excludeReservedBlocks(List<BlockPos> positions, CallbackInfo callback) {
        var manager = PrefabLitematicaMod.activePlacements();
        if (manager != null) positions.removeIf(pos -> manager.reserved(((ServerExplosion) (Object) this).level(), pos));
    }
}
