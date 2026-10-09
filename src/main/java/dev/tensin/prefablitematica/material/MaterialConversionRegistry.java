// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.material;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.BlockTransformers;
import net.minecraft.world.level.block.*;
import java.util.*;

/** Alternative inputs pay for the requested material and every required tool use together. */
public final class MaterialConversionRegistry {
    public enum Tool {
        NONE, SHOVEL, HOE, AXE, SHEARS;

        public boolean accepts(ItemStack stack) {
            if (stack.isEmpty() || !stack.isDamageableItem()) return false;
            if (this == SHEARS) return stack.is(Items.SHEARS);
            var transformer = stack.get(DataComponents.BLOCK_TRANSFORMER);
            return transformer != null && switch (this) {
                case SHOVEL -> transformer.is(BlockTransformers.SHOVEL);
                case HOE -> transformer.is(BlockTransformers.HOE);
                case AXE -> transformer.is(BlockTransformers.AXE);
                default -> false;
            };
        }
    }

    public record Recipe(Item source, Item target, Tool tool, int durability, String hint) {}

    private static final class Rules {
        private static final List<Recipe> ALL = createRules();
    }

    public static List<Recipe> recipes() { return Rules.ALL; }

    public static List<String> hints(MaterialRequirement requirement, MaterialEquivalenceRegistry equivalence) {
        return recipes().stream().filter(r -> equivalence.accepts(requirement, new ItemStack(r.target())))
                .map(Recipe::hint).distinct().toList();
    }

    /** Deliberately bypass enchantment randomness: one tool use costs exactly one durability. */
    public static int capacity(ItemStack stack, Tool tool, int durability) {
        if (!tool.accepts(stack) || durability <= 0) return 0;
        return Math.max(0, stack.getMaxDamage() - stack.getDamageValue()) / durability;
    }

    public static void damage(ItemStack stack, int amount) {
        int damaged = stack.getDamageValue() + amount;
        if (damaged >= stack.getMaxDamage()) stack.shrink(1);
        else stack.setDamageValue(damaged);
    }

    private static List<Recipe> createRules() {
        var rules = new ArrayList<Recipe>();
        add(rules, Blocks.DIRT, Blocks.GRASS_BLOCK, Tool.NONE, 0, "grass");
        for (var source : List.of(Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT,
                Blocks.ROOTED_DIRT, Blocks.PODZOL, Blocks.MYCELIUM))
            add(rules, source, Blocks.DIRT_PATH, Tool.SHOVEL, 1, "path");
        for (var source : List.of(Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.DIRT_PATH))
            add(rules, source, Blocks.FARMLAND, Tool.HOE, 1, "farmland");
        for (var source : List.of(Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT)) {
            add(rules, source, Blocks.DIRT, Tool.HOE, 1, "till_dirt");
            add(rules, source, Blocks.FARMLAND, Tool.HOE, 2, "farmland_twice");
        }
        add(rules, Blocks.PUMPKIN, Blocks.CARVED_PUMPKIN, Tool.SHEARS, 1, "carve");
        // Discover every vanilla stripped log/wood variant, including newer tree species.
        for (var block : BuiltInRegistries.BLOCK) {
            var id = BuiltInRegistries.BLOCK.getKey(block);
            if (!id.getNamespace().equals("minecraft") || !id.getPath().startsWith("stripped_")) continue;
            var sourceId = Identifier.withDefaultNamespace(id.getPath().substring("stripped_".length()));
            if (BuiltInRegistries.BLOCK.containsKey(sourceId))
                add(rules, BuiltInRegistries.BLOCK.getValue(sourceId), block, Tool.AXE, 1, "strip");
        }
        // Each scrape and wax removal is a separate use, even when combined in one charge.
        WeatheringCopper.PREVIOUS_BY_BLOCK.get().forEach((source, target) -> {
            int uses = 1;
            for (Block next = target; next != null; next = WeatheringCopper.PREVIOUS_BY_BLOCK.get().get(next))
                add(rules, source, next, Tool.AXE, uses++, "scrape");
        });
        HoneycombItem.WAX_OFF_BY_BLOCK.get().forEach((source, target) -> {
            add(rules, source, target, Tool.AXE, 1, "wax_off");
            int uses = 2;
            for (Block next = WeatheringCopper.PREVIOUS_BY_BLOCK.get().get(target); next != null;
                    next = WeatheringCopper.PREVIOUS_BY_BLOCK.get().get(next))
                add(rules, source, next, Tool.AXE, uses++, "wax_scrape");
        });
        return List.copyOf(rules);
    }

    private static void add(List<Recipe> rules, Block source, Block target, Tool tool, int durability, String hint) {
        if (source.asItem() != Items.AIR && target.asItem() != Items.AIR)
            rules.add(new Recipe(source.asItem(), target.asItem(), tool, durability, hint));
    }
}
