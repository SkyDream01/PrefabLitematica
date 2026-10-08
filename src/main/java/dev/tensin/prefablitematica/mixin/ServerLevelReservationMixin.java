// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.mixin;

import dev.tensin.prefablitematica.PrefabLitematicaMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelReservationMixin {
    @Inject(method = "tickBlock", at = @At("HEAD"), cancellable = true)
    private void blueprint$pauseBlock(BlockPos pos, Block block, CallbackInfo callback) {
        if (reserved(pos)) { ((ServerLevel) (Object) this).scheduleTick(pos, block, 2); callback.cancel(); }
    }
    @Inject(method = "tickFluid", at = @At("HEAD"), cancellable = true)
    private void blueprint$pauseFluid(BlockPos pos, Fluid fluid, CallbackInfo callback) {
        if (reserved(pos)) { ((ServerLevel) (Object) this).scheduleTick(pos, fluid, 2); callback.cancel(); }
    }
    private boolean reserved(BlockPos pos) { return PrefabLitematicaMod.activePlacements() != null && PrefabLitematicaMod.activePlacements().reserved((ServerLevel) (Object) this, pos); }
}
