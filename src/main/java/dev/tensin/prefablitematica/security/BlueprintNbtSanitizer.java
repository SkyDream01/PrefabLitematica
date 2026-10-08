// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.security;

import net.minecraft.nbt.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.*;
import java.util.*;

/** Allow-list rather than trusting an ever-growing blacklist of item-bearing components. */
public final class BlueprintNbtSanitizer {
    private static final Set<String> COSMETIC = Set.of("front_text", "back_text", "is_waxed", "patterns", "Patterns", "Base");
    private static final Set<String> FORBIDDEN = Set.of("items", "item", "loottable", "loottableseed", "components", "command",
            "clickevent", "click_event", "hoverevent", "hover_event", "insertion", "entitydata", "entity_data", "block_entity_data",
            "recorditem", "book", "bees", "spawn_data", "spawndata", "spawnpotentials", "recipesused");
    private BlueprintNbtSanitizer() {}
    /** Cosmetic export only: ignore stale tile-map entries and strip inventories before transmission. */
    public static CompoundTag prepareForExport(CompoundTag input, BlockState state) {
        if (!state.hasBlockEntity()) return null;
        CompoundTag output = new CompoundTag();
        if (state.getBlock() instanceof SignBlock || state.getBlock() instanceof AbstractBannerBlock)
            for (String key : input.keySet()) if (COSMETIC.contains(key)) output.put(key, input.get(key).copy());
        // The block entity type is determined by the block state, not old schematic metadata.
        return sanitize(output, state, 16384);
    }
    public static CompoundTag sanitize(CompoundTag input, BlockState state, int maxBytes) {
        if (input.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxBytes)
            throw new IllegalArgumentException("Block entity NBT too large");
        checkDepth(input, 0);
        if (!state.hasBlockEntity()) throw new IllegalArgumentException("NBT supplied for a non block entity");
        if (input.contains("id")) {
            String provided = input.getStringOr("id", "");
            var id = net.minecraft.resources.Identifier.tryParse(provided);
            if (id == null || !net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(id))
                throw new IllegalArgumentException("Unknown BlockEntity ID");
            if (!(state.getBlock() instanceof EntityBlock entityBlock)) throw new IllegalArgumentException("Invalid BlockEntity block");
            var entity = entityBlock.newBlockEntity(net.minecraft.core.BlockPos.ZERO, state);
            if (entity == null || !id.equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType())))
                throw new IllegalArgumentException("BlockEntity ID contradicts block state");
        }
        CompoundTag output = new CompoundTag();
        boolean cosmetic = state.getBlock() instanceof SignBlock || state.getBlock() instanceof AbstractBannerBlock;
        if (cosmetic) for (String key : input.keySet()) {
            if (COSMETIC.contains(key)) output.put(key, scrub(input.get(key), 0));
        }
        return output;
    }
    public static void checkDepth(Tag tag, int depth) {
        if (depth > 16) throw new IllegalArgumentException("NBT nested too deeply");
        if (tag instanceof CompoundTag compound) {
            if (compound.size() > 128) throw new IllegalArgumentException("Too many NBT keys");
            compound.keySet().forEach(key -> checkDepth(compound.get(key), depth + 1));
        } else if (tag instanceof ListTag list) {
            if (list.size() > 256) throw new IllegalArgumentException("NBT list too long");
            list.forEach(value -> checkDepth(value, depth + 1));
        } else if (tag instanceof StringTag str && str.value().length() > 4096)
            throw new IllegalArgumentException("NBT string too long");
    }
    private static Tag scrub(Tag tag, int depth) {
        if (tag instanceof CompoundTag compound) {
            CompoundTag clean = new CompoundTag();
            for (String key : compound.keySet()) if (!FORBIDDEN.contains(key.toLowerCase(Locale.ROOT))) clean.put(key, scrub(compound.get(key), depth + 1));
            return clean;
        }
        if (tag instanceof ListTag list) {
            ListTag clean = new ListTag(); list.forEach(value -> clean.add(scrub(value, depth + 1))); return clean;
        }
        // Legacy signs may embed components in JSON strings. Preserve literal display text only.
        if (tag instanceof StringTag str && (str.value().contains("clickEvent") || str.value().contains("click_event")))
            return StringTag.valueOf("");
        return tag.copy();
    }
}
