// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.blueprint;

import dev.tensin.prefablitematica.material.MaterialRequirement;
import java.util.*;

public final class BlueprintData {
    public final UUID id;
    public final String name;
    public final int sizeX, sizeY, sizeZ;
    public final List<BlueprintBlock> blocks;
    public final LinkedHashMap<String, MaterialRequirement> requirements;
    public boolean locked;
    public boolean retired;
    public BlueprintData(UUID id, String name, int x, int y, int z, List<BlueprintBlock> blocks, LinkedHashMap<String, MaterialRequirement> requirements) {
        this.id = id; this.name = name; sizeX = x; sizeY = y; sizeZ = z;
        this.blocks = List.copyOf(blocks); this.requirements = requirements;
    }
    public boolean fullyCharged() { return requirements.values().stream().allMatch(r -> r.remaining() == 0); }
    /** Stable order within each group keeps the list predictable as materials are supplied. */
    public List<MaterialRequirement> requirementsForInput() {
        return requirements.values().stream().sorted(Comparator.comparing(r -> r.remaining() == 0)).toList();
    }
    public double charge() {
        long total = requirements.values().stream().mapToLong(r -> r.required).sum();
        long paid = requirements.values().stream().mapToLong(r -> r.supplied).sum();
        return total == 0 ? 1 : (double) paid / total;
    }
    public void fill() { requirements.values().forEach(r -> r.supplied = r.required); }
    public void reset() { requirements.values().forEach(r -> r.supplied = 0); }
}
