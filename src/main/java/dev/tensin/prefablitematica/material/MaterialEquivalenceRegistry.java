// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.material;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;

/** Membership is resolved against server data pack tags, never client classifications. */
public final class MaterialEquivalenceRegistry {
    public record Match(String key, String item, String group) {}
    public Match resolve(Item item, boolean substitution) {
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        if (substitution) for (MaterialGroup group : MaterialGroup.values()) {
            if (new ItemStack(item).is(group.tag())) return new Match("group:" + group.id(), itemId, group.id());
        }
        return new Match("item:" + itemId, itemId, "exact");
    }
    public boolean accepts(MaterialRequirement requirement, ItemStack stack) {
        if (requirement.key.startsWith("group:")) {
            for (MaterialGroup group : MaterialGroup.values())
                if (group.id().equals(requirement.group)) return stack.is(group.tag());
            return false;
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(requirement.representativeItem);
    }
}
