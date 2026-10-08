// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.mixin;

import dev.tensin.prefablitematica.PrefabLitematicaMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelReservationMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), cancellable = true)
    private void blueprint$reserve(BlockPos pos, BlockState state, int flags, int recursion, CallbackInfoReturnable<Boolean> callback) {
        if ((Object) this instanceof ServerLevel world && PrefabLitematicaMod.activePlacements() != null && PrefabLitematicaMod.activePlacements().reserved(world, pos)) callback.setReturnValue(false);
    }
    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z", at = @At("HEAD"), cancellable = true)
    private void blueprint$reserveDestruction(BlockPos pos, boolean drops, net.minecraft.world.entity.Entity breaker, int recursion, CallbackInfoReturnable<Boolean> callback) {
        if ((Object) this instanceof ServerLevel world && PrefabLitematicaMod.activePlacements() != null && PrefabLitematicaMod.activePlacements().reserved(world, pos)) callback.setReturnValue(false);
    }
}
