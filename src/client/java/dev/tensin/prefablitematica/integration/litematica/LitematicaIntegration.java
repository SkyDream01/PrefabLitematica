// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.integration.litematica;

import dev.tensin.prefablitematica.blueprint.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.ticks.ScheduledTick;
import dev.tensin.prefablitematica.security.BlueprintNbtSanitizer;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;

/** Client-only optional adapter verified against Litematica 26.3 / 0.29.1 public methods. */
public final class LitematicaIntegration {
    private static Object call(Object target, String name) throws ReflectiveOperationException { return target.getClass().getMethod(name).invoke(target); }
    private static Object named(Object target, String method, String name) throws ReflectiveOperationException { return target.getClass().getMethod(method, String.class).invoke(target, name); }
    public static boolean available() { return FabricLoader.getInstance().isModLoaded("litematica"); }
    public enum SourceKind { PLACEMENT, LOADED_SCHEMATIC, FILE }
    public record Source(String name, String detail, SourceKind kind, Object placement, Object schematic, Path file) {
        public String translationKey() { return "gui.prefablitematica.source." + kind.name().toLowerCase(Locale.ROOT); }
    }
    public static List<Source> memorySources() throws Exception {
        if (!available()) throw new IllegalArgumentException("Install Litematica 26.3 and MaLiLib on the client");
        var result = new ArrayList<Source>(); Set<Object> represented = Collections.newSetFromMap(new IdentityHashMap<>());
        Object placements = Class.forName("fi.dy.masa.litematica.data.DataManager").getMethod("getSchematicPlacementManager").invoke(null);
        for (Object placement : new ArrayList<>((Collection<?>) call(placements, "getAllSchematicsPlacements"))) {
            Object schematic = call(placement, "getSchematic"); represented.add(schematic);
            result.add(new Source((String) call(placement, "getName"), String.valueOf(call(placement, "getOrigin")), SourceKind.PLACEMENT, placement, null, null));
        }
        Object holder = Class.forName("fi.dy.masa.litematica.data.SchematicHolder").getMethod("getInstance").invoke(null);
        for (Object schematic : new ArrayList<>((Collection<?>) call(holder, "getAllSchematics"))) if (!represented.contains(schematic)) {
            Path path = (Path) call(schematic, "getFile");
            result.add(new Source(schematicName(schematic), path == null ? "" : path.toString(), SourceKind.LOADED_SCHEMATIC, null, schematic, null));
        }
        return result;
    }
    public static Path schematicDirectory() throws Exception {
        return ((Path) Class.forName("fi.dy.masa.litematica.data.DataManager").getMethod("getSchematicsBaseDirectory").invoke(null)).toAbsolutePath().normalize();
    }
    /** Directory enumeration runs on a worker; no file is loaded into Litematica or its world here. */
    public static List<Source> fileSources(Path directory) throws java.io.IOException {
        if (!Files.isDirectory(directory)) return List.of();
        try (var paths = Files.find(directory, 8, (path, attributes) -> attributes.isRegularFile() && supportedFile(path))) {
            return paths.limit(4096).sorted().map(path -> new Source(path.getFileName().toString(), directory.relativize(path).toString(), SourceKind.FILE, null, null, path)).toList();
        }
    }
    private static boolean supportedFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".litematic") || name.endsWith(".schematic") || name.endsWith(".schem") || name.endsWith(".nbt");
    }
    private static String schematicName(Object schematic) throws Exception { return ((String) call(call(schematic, "getMetadata"), "getName")).strip(); }
    private static Source selectedSource() throws Exception {
        if (!available()) throw new IllegalArgumentException("Install Litematica 26.3 and MaLiLib on the client");
        Object placements = Class.forName("fi.dy.masa.litematica.data.DataManager").getMethod("getSchematicPlacementManager").invoke(null);
        Object selected = call(placements, "getSelectedSchematicPlacement");
        if (selected == null) throw new IllegalArgumentException("Select a Litematica placement first");
        return new Source((String) call(selected, "getName"), "", SourceKind.PLACEMENT, selected, null, null);
    }
    public static final class Capture {
        private record Region(Object container, Method get, Map<BlockPos, ?> nbt, Map<BlockPos, ScheduledTick<Block>> blockTicks,
                              Map<BlockPos, ScheduledTick<net.minecraft.world.level.material.Fluid>> fluidTicks, int x, int y, int z, BlockPos minimum,
                              BlockPos offset, Mirror mainMirror, Mirror subMirror, Rotation mainRotation, Rotation subRotation, Rotation combined, Mirror stateSubMirror) {
            BlockPos transform(BlockPos local) { return transformPos(transformPos(local, mainMirror, mainRotation), subMirror, subRotation).offset(offset); }
        }
        private final List<Region> regions = new ArrayList<>();
        private final LinkedHashMap<BlockPos, BlueprintBlock> blocks = new LinkedHashMap<>();
        private final LinkedHashMap<String, BlueprintScheduledTick> ticks = new LinkedHashMap<>();
        private final String name;
        private final BlockPos minimum;
        private final int sizeX, sizeY, sizeZ;
        private int regionIndex, index;
        private final Method toVanilla;
        public Capture() throws Exception { this(selectedSource()); }
        @SuppressWarnings("unchecked") public Capture(Source source) throws Exception {
            if (!available()) throw new IllegalArgumentException("Install Litematica 26.3 and MaLiLib on the client");
            Object selected = source.placement(); Object schematic = source.schematic();
            if (selected != null) schematic = call(selected, "getSchematic");
            else {
                if (schematic == null) {
                    Path file = source.file();
                    if (file == null || !Files.isRegularFile(file) || Files.size(file) > 33554432) throw new IllegalArgumentException("Invalid or oversized schematic file");
                    schematic = Class.forName("fi.dy.masa.litematica.schematic.LitematicaSchematic").getMethod("createFromFile", Path.class, String.class).invoke(null, file.getParent(), file.getFileName().toString());
                }
                if (schematic == null) throw new IllegalArgumentException("Cannot read schematic file");
                // This placement is never registered, selected, enabled in-world or added to the holder.
                selected = Class.forName("fi.dy.masa.litematica.schematic.placement.SchematicPlacement").getMethod("createTemporary", schematic.getClass(), BlockPos.class).invoke(null, schematic, BlockPos.ZERO);
            }
            name = source.kind() == SourceKind.PLACEMENT ? ((String) call(selected, "getName")).strip() : schematicName(schematic);
            Mirror mainMirror = (Mirror) call(selected, "getMirror"); Rotation mainRotation = (Rotation) call(selected, "getRotation");
            Map<String, ?> enabled = (Map<String, ?>) call(selected, "getEnabledRelativeSubRegionPlacements");
            if (enabled.isEmpty()) {
                var all = new LinkedHashMap<String, Object>();
                for (Object sub : (Collection<?>) call(selected, "getAllSubRegionsPlacements")) all.put((String) call(sub, "getName"), sub);
                enabled = all;
            }
            if (enabled.isEmpty()) throw new IllegalArgumentException("Projection has no regions");
            long scanned = 0; int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (var entry : enabled.entrySet()) {
                Object sub = entry.getValue(); String regionName = entry.getKey();
                BlockPos signed = (BlockPos) named(schematic, "getAreaSize", regionName); BlockPos position = (BlockPos) call(sub, "getPos");
                int x = Math.abs(signed.getX()), y = Math.abs(signed.getY()), z = Math.abs(signed.getZ());
                scanned += (long) x * y * z; if (x == 0 || y == 0 || z == 0 || scanned > 500000) throw new IllegalArgumentException("Projection exceeds 500000 scanned cells");
                Object container = named(schematic, "getSubRegionContainer", regionName); if (container == null) throw new IllegalArgumentException("Missing schematic region container");
                Map<BlockPos, ?> nbt = (Map<BlockPos, ?>) named(schematic, "getBlockEntityMapForRegion", regionName);
                var blockTicks = (Map<BlockPos, ScheduledTick<Block>>) named(schematic, "getScheduledBlockTicksForRegion", regionName);
                var fluidTicks = (Map<BlockPos, ScheduledTick<net.minecraft.world.level.material.Fluid>>) named(schematic, "getScheduledFluidTicksForRegion", regionName);
                Mirror subMirror = (Mirror) call(sub, "getMirror"); Rotation subRotation = (Rotation) call(sub, "getRotation");
                Mirror stateSub = subMirror;
                if (mainRotation == Rotation.CLOCKWISE_90 || mainRotation == Rotation.COUNTERCLOCKWISE_90)
                    stateSub = subMirror == Mirror.FRONT_BACK ? Mirror.LEFT_RIGHT : subMirror == Mirror.LEFT_RIGHT ? Mirror.FRONT_BACK : Mirror.NONE;
                BlockPos localMin = new BlockPos(signed.getX() < 0 ? 1 - x : 0, signed.getY() < 0 ? 1 - y : 0, signed.getZ() < 0 ? 1 - z : 0);
                Region region = new Region(container, container.getClass().getMethod("get", int.class, int.class, int.class), nbt == null ? Map.of() : nbt,
                        blockTicks == null ? Map.of() : blockTicks, fluidTicks == null ? Map.of() : fluidTicks,
                        x, y, z, localMin, transformPos(position, mainMirror, mainRotation), mainMirror, subMirror, mainRotation, subRotation, mainRotation.getRotated(subRotation), stateSub);
                regions.add(region);
                for (int cx : new int[]{0, x - 1}) for (int cy : new int[]{0, y - 1}) for (int cz : new int[]{0, z - 1}) {
                    BlockPos p = region.transform(localMin.offset(cx, cy, cz));
                    minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
                    maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
                }
            }
            minimum = new BlockPos(minX, minY, minZ); sizeX = maxX - minX + 1; sizeY = maxY - minY + 1; sizeZ = maxZ - minZ + 1;
            if (sizeX > 512 || sizeY > 512 || sizeZ > 512 || (long) sizeX * sizeY * sizeZ > 500000) throw new IllegalArgumentException("Projection bounding volume exceeds import limits");
            toVanilla = Class.forName("fi.dy.masa.malilib.util.data.tag.converter.DataConverterNbt").getMethod("toVanillaCompound", Class.forName("fi.dy.masa.malilib.util.data.tag.CompoundData"));
        }
        public boolean tick(int budget) throws Exception {
            while (budget-- > 0 && regionIndex < regions.size()) {
                Region r = regions.get(regionIndex); int x = index % r.x, z = (index / r.x) % r.z, y = index / (r.x * r.z);
                BlockState state = (BlockState) r.get.invoke(r.container, x, y, z);
                if (!state.isAir() && state.getBlock() != Blocks.STRUCTURE_VOID) {
                    BlockPos pos = r.transform(r.minimum.offset(x, y, z)).subtract(minimum);
                    state = state.mirror(r.mainMirror).mirror(r.stateSubMirror).rotate(r.combined);
                    Object rawNbt = r.nbt.get(new BlockPos(x, y, z));
                    CompoundTag nbt = rawNbt == null ? null : BlueprintNbtSanitizer.prepareForExport((CompoundTag) toVanilla.invoke(null, rawNbt), state);
                    BlueprintBlock block = new BlueprintBlock(pos, state, nbt);
                    BlueprintBlock previous = blocks.putIfAbsent(pos, block);
                    if (previous != null && !previous.equals(block)) throw new IllegalArgumentException("Conflicting overlapping subregions");
                    var local = new BlockPos(x, y, z); var blockTick = r.blockTicks.get(local); var fluidTick = r.fluidTicks.get(local);
                    if (blockTick != null && blockTick.type() == state.getBlock()) addTick(new BlueprintScheduledTick(pos, false,
                            net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(blockTick.type()), blockTick.triggerTick(), blockTick.priority().getValue(), blockTick.subTickOrder()));
                    if (fluidTick != null && fluidTick.type() == state.getFluidState().getType()) addTick(new BlueprintScheduledTick(pos, true,
                            net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluidTick.type()), fluidTick.triggerTick(), fluidTick.priority().getValue(), fluidTick.subTickOrder()));
                }
                if (++index >= r.x * r.y * r.z) { regionIndex++; index = 0; }
            }
            return regionIndex == regions.size();
        }
        public BlueprintData finish() {
            if (blocks.isEmpty()) throw new IllegalArgumentException("Projection is empty");
            return new BlueprintData(UUID.randomUUID(), name, sizeX, sizeY, sizeZ, new ArrayList<>(blocks.values()), new ArrayList<>(ticks.values()), new LinkedHashMap<>());
        }
        private void addTick(BlueprintScheduledTick tick) {
            var previous = ticks.putIfAbsent(tick.fluid() + ":" + tick.position().toShortString(), tick);
            if (previous != null && !previous.equals(tick)) throw new IllegalArgumentException("Conflicting scheduled ticks in overlapping subregions");
        }
    }
    public static BlockPos transformPos(BlockPos pos, Mirror mirror, Rotation rotation) {
        int x = pos.getX(), z = pos.getZ();
        if (mirror == Mirror.LEFT_RIGHT) z = -z; else if (mirror == Mirror.FRONT_BACK) x = -x;
        return switch (rotation) {
            case NONE -> new BlockPos(x, pos.getY(), z);
            case CLOCKWISE_90 -> new BlockPos(-z, pos.getY(), x);
            case CLOCKWISE_180 -> new BlockPos(-x, pos.getY(), -z);
            case COUNTERCLOCKWISE_90 -> new BlockPos(z, pos.getY(), -x);
        };
    }
}
