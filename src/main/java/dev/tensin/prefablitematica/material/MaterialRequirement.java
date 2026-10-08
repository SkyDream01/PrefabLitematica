// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.material;

public final class MaterialRequirement {
    public final String key;
    public final String representativeItem;
    public final String group;
    public int required;
    public int supplied;
    public MaterialRequirement(String key, String representativeItem, String group, int required) {
        this.key = key; this.representativeItem = representativeItem; this.group = group; this.required = required;
    }
    public int remaining() { return Math.max(0, required - supplied); }
    public int offer(int available) {
        if (available < 0) throw new IllegalArgumentException("Negative offer");
        int accepted = Math.min(available, remaining()); supplied += accepted; return accepted;
    }
}
