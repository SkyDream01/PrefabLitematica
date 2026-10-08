// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.material;

import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import java.util.LinkedHashMap;

public final class FluidMaterialResolver {
    private boolean water;
    private int lava;
    public void accept(net.minecraft.world.level.block.state.BlockState state) {
        accept(state.getFluidState());
        if (state.is(net.minecraft.world.level.block.Blocks.WATER_CAULDRON)) water = true;
        if (state.is(net.minecraft.world.level.block.Blocks.LAVA_CAULDRON)) lava++;
    }
    public void accept(FluidState fluid) {
        if (fluid.getType().isSame(Fluids.WATER)) water = true;
        if (fluid.getType().isSame(Fluids.LAVA) && fluid.isSource()) lava++;
        if (!fluid.isEmpty() && !fluid.getType().isSame(Fluids.WATER) && !fluid.getType().isSame(Fluids.LAVA))
            throw new IllegalArgumentException("Unsupported modded fluid; define a resolver before importing");
    }
    public void finish(LinkedHashMap<String, MaterialRequirement> requirements) {
        if (water) requirements.put("item:minecraft:water_bucket", new MaterialRequirement("item:minecraft:water_bucket", "minecraft:water_bucket", "water", 2));
        if (lava > 0) requirements.put("item:minecraft:lava_bucket", new MaterialRequirement("item:minecraft:lava_bucket", "minecraft:lava_bucket", "lava", lava));
    }
}
