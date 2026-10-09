// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.blueprint;

import com.google.gson.*;
import dev.tensin.prefablitematica.PrefabLitematicaMod;
import dev.tensin.prefablitematica.material.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

public final class BlueprintManager {
    private final Path directory;
    private final Map<UUID, BlueprintData> cache = new LinkedHashMap<>();
    public final MaterialEquivalenceRegistry equivalence = new MaterialEquivalenceRegistry();
    public BlueprintManager(MinecraftServer server) {
        directory = server.getWorldPath(LevelResource.ROOT).resolve("prefablitematica").resolve("blueprints");
        try { Files.createDirectories(directory); } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    public BlueprintData get(UUID id) {
        if (id == null) return null;
        if (cache.containsKey(id)) return cache.get(id).retired ? null : cache.get(id);
        Path structure = directory.resolve(id + ".bprint");
        if (!Files.exists(structure)) return null;
        try {
            if (Files.size(structure) > PrefabLitematicaMod.CONFIG.maxUploadBytes) throw new IOException("Saved file exceeds limit");
            BlueprintData data = BlueprintSerializer.decode(Files.readAllBytes(structure), id, PrefabLitematicaMod.CONFIG);
            JsonObject progress = JsonParser.parseString(Files.readString(directory.resolve(id + ".json"))).getAsJsonObject();
            if (progress.has("retired") && progress.get("retired").getAsBoolean()) return null;
            for (JsonElement entry : progress.getAsJsonArray("requirements")) {
                JsonObject row = entry.getAsJsonObject();
                MaterialRequirement r = new MaterialRequirement(row.get("key").getAsString(), row.get("item").getAsString(), row.get("group").getAsString(), row.get("required").getAsInt());
                r.supplied = row.get("supplied").getAsInt();
                if (r.required <= 0 || r.supplied < 0 || r.supplied > r.required) throw new IOException("Corrupt requirement");
                data.requirements.put(r.key, r);
            }
            // A reserved placement has already consumed its charge. A crash cannot restore it.
            if (progress.get("locked").getAsBoolean()) { data.reset(); data.locked = false; saveProgress(data); }
            evict(); cache.put(id, data); return data;
        } catch (Exception e) { PrefabLitematicaMod.LOGGER.error("Cannot read blueprint {}", id, e); return null; }
    }
    private void evict() {
        long count = cache.values().stream().mapToLong(d -> d.blocks.size()).sum();
        var it = cache.values().iterator();
        while (count > PrefabLitematicaMod.CONFIG.maxBlueprintBlocks * 2L && it.hasNext()) {
            BlueprintData data = it.next(); if (!data.locked) { count -= data.blocks.size(); it.remove(); }
        }
    }
    public void create(BlueprintData data) throws IOException {
        byte[] encoded = BlueprintSerializer.encode(data);
        if (encoded.length > PrefabLitematicaMod.CONFIG.maxUploadBytes) throw new IOException("Sanitized structure exceeds storage limit");
        atomicWrite(directory.resolve(data.id + ".bprint"), encoded);
        saveProgress(data); evict(); cache.put(data.id, data);
    }
    /** Refresh missing runtime metadata only; a paid blueprint cannot be exchanged for another structure. */
    public static void refreshLegacy(BlueprintData old, BlueprintData replacement) {
        if (!old.requiresReimport || replacement.requiresReimport || old.locked || old.retired || !old.id.equals(replacement.id)
                || old.sizeX != replacement.sizeX || old.sizeY != replacement.sizeY || old.sizeZ != replacement.sizeZ)
            throw new IllegalArgumentException("Legacy refresh requires the same building and blueprint");
        var before = new HashMap<BlockPos, BlockState>(); old.blocks.forEach(b -> before.put(b.relativePos(), b.state()));
        var after = new HashMap<BlockPos, BlockState>(); replacement.blocks.forEach(b -> after.put(b.relativePos(), b.state()));
        if (!before.equals(after) || !old.requirements.keySet().equals(replacement.requirements.keySet())
                || replacement.requirements.values().stream().anyMatch(r -> old.requirements.get(r.key).required != r.required))
            throw new IllegalArgumentException("Reimport the exact original building to preserve its charge");
        replacement.requirements.values().forEach(r -> r.supplied = old.requirements.get(r.key).supplied);
    }
    public void saveProgress(BlueprintData data) throws IOException {
        JsonObject object = new JsonObject(); object.addProperty("version", 1); object.addProperty("locked", data.locked); object.addProperty("retired", data.retired);
        JsonArray rows = new JsonArray();
        data.requirements.values().forEach(r -> {
            JsonObject row = new JsonObject(); row.addProperty("key", r.key); row.addProperty("item", r.representativeItem);
            row.addProperty("group", r.group); row.addProperty("required", r.required); row.addProperty("supplied", r.supplied); rows.add(row);
        });
        object.add("requirements", rows); atomicWrite(directory.resolve(data.id + ".json"), object.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public static void atomicWrite(Path target, byte[] bytes) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true);
        }
        try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
    }
    /** Analysis is budgeted by server ticks; tag lookups are cached by item during one import. */
    public final class Analysis {
        public final BlueprintData data;
        private final Map<BlockPos, BlockState> states = new HashMap<>();
        private final Map<Item, MaterialEquivalenceRegistry.Match> matches = new HashMap<>();
        private final BlockMaterialResolver blocks = new BlockMaterialResolver();
        private final FluidMaterialResolver fluids = new FluidMaterialResolver();
        private int index;
        private boolean mapped;
        public Analysis(BlueprintData data) { this.data = data; }
        public boolean tick(int budget) {
            while (budget-- > 0 && index < data.blocks.size()) {
                BlueprintBlock b = data.blocks.get(index++);
                if (!mapped) states.put(b.relativePos(), b.state());
                else {
                    fluids.accept(b.state());
                    for (var cost : blocks.resolveAll(b.state(), states, b.relativePos())) {
                    if (cost.count() > 0) {
                        if (cost.item() == net.minecraft.world.item.Items.AIR) throw new IllegalArgumentException("Unsupported composite material");
                        var match = matches.computeIfAbsent(cost.item(), item -> equivalence.resolve(item, PrefabLitematicaMod.CONFIG.allowMaterialSubstitution));
                        data.requirements.computeIfAbsent(match.key(), ignored -> new MaterialRequirement(match.key(), match.item(), match.group(), 0)).required += cost.count();
                    }
                    }
                }
            }
            if (index == data.blocks.size()) {
                if (!mapped) { mapped = true; index = 0; }
                else { fluids.finish(data.requirements); return true; }
            }
            return false;
        }
    }
}
