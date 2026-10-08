// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.mixin;

import dev.tensin.prefablitematica.PrefabLitematicaMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public abstract class BlockDropsReservationMixin {
    @Inject(method = "popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
    private static void blueprint$preventReservedDrops(Level world, BlockPos pos, ItemStack stack, CallbackInfo callback) {
        if (PrefabLitematicaMod.activePlacements() != null && PrefabLitematicaMod.activePlacements().reserved(world, pos)) callback.cancel();
    }
}
