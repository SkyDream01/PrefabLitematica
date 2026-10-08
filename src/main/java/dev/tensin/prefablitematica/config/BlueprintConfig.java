// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.config;

import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;

public final class BlueprintConfig {
    public int maxBlueprintBlocks = 500000;
    public int blocksPlacedPerTick = 4096;
    public String placementMode = "SAFE";
    public boolean consumeBlueprintAfterPlacement = false;
    public boolean copyContainerContents = false;
    public boolean allowMaterialSubstitution = true;
    public boolean creativeBatteryEnabled = true;
    public int maxDimension = 512;
    public int maxUploadBytes = 32 * 1024 * 1024;
    public int maxExpandedBytes = 96 * 1024 * 1024;
    public int maxBlockEntityBytes = 16384;
    public int maxConcurrentTasks = 2;
    public boolean allowSurvivalImports = true;
    public void validate() {
        if (maxBlueprintBlocks < 1 || maxBlueprintBlocks > 500000 || blocksPlacedPerTick < 1 || blocksPlacedPerTick > 16384
                || maxDimension < 1 || maxDimension > 2048 || maxUploadBytes < 1024 || maxUploadBytes > 33554432
                || maxExpandedBytes < 1024 || maxExpandedBytes > 100663296 || maxBlockEntityBytes < 1 || maxBlockEntityBytes > 16384
                || maxConcurrentTasks < 1 || maxConcurrentTasks > 8 || !(placementMode.equals("SAFE") || placementMode.equals("REPLACE")))
            throw new IllegalArgumentException("Invalid blueprint config bounds");
        if (copyContainerContents) throw new IllegalArgumentException("copyContainerContents=true is intentionally unsupported: it duplicates items");
    }
    public static BlueprintConfig load() {
        try {
            Path path = FabricLoader.getInstance().getConfigDir().resolve("prefablitematica.json");
            var gson = new GsonBuilder().setPrettyPrinting().create();
            BlueprintConfig config = Files.exists(path) ? gson.fromJson(Files.readString(path), BlueprintConfig.class) : new BlueprintConfig();
            config.validate();
            if (!Files.exists(path)) Files.writeString(path, gson.toJson(config));
            return config;
        } catch (Exception e) { throw new IllegalStateException("Cannot load config/prefablitematica.json", e); }
    }
}
