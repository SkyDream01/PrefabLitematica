// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.material;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** Order is explicit: specialized shape groups precede broader groups. */
public enum MaterialGroup {
    WOODEN_PLANKS, LOGS, STRIPPED_LOGS, WOOD, STRIPPED_WOOD,
    WOODEN_SLABS, WOODEN_STAIRS, WOODEN_DOORS, WOODEN_TRAPDOORS,
    WOODEN_FENCES, WOODEN_FENCE_GATES, WOODEN_BUTTONS, WOODEN_PRESSURE_PLATES,
    SANDSTONE_BLOCKS, SANDSTONE_SLABS, SANDSTONE_STAIRS,
    STONE_BLOCKS, STONE_SLABS, STONE_STAIRS, STONE_WALLS,
    SAND, COLORED_SAND, WOOL, WOOL_SLABS, WOOL_STAIRS, CARPETS, CONCRETE, CONCRETE_SLABS, CONCRETE_STAIRS, CONCRETE_POWDER,
    STAINED_GLASS, STAINED_GLASS_PANES, TERRACOTTA, GLAZED_TERRACOTTA,
    CANDLES, BEDS, BANNERS, LEAVES, SAPLINGS, WOODEN_SIGNS, WOODEN_HANGING_SIGNS,
    COPPER_BLOCKS, COPPER_STAIRS, COPPER_SLABS, COPPER_GRATES;
    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
    public TagKey<Item> tag() { return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("prefablitematica", "materials/" + id())); }
}
