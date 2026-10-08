// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.blueprint;

import dev.tensin.prefablitematica.config.BlueprintConfig;
import dev.tensin.prefablitematica.security.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.Property;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/** Wire and disk format contains structure only: no UUID, charge, material group or entities from clients. */
public final class BlueprintSerializer {
    public static final int MAGIC = 0x42505231;
    public static final TagKey<Block> FORBIDDEN_BLOCKS = TagKey.create(Registries.BLOCK, Identifier.parse("prefablitematica:forbidden_blocks"));
    private BlueprintSerializer() {}
    public static byte[] encode(BlueprintData data) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeInt(MAGIC); writeString(out, data.name);
            out.writeInt(data.sizeX); out.writeInt(data.sizeY); out.writeInt(data.sizeZ);
            var palette = new LinkedHashMap<BlockState, Integer>();
            data.blocks.forEach(b -> palette.computeIfAbsent(b.state(), ignored -> palette.size()));
            out.writeInt(palette.size());
            for (BlockState state : palette.keySet()) {
                writeString(out, BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
                out.writeInt(state.getProperties().size());
                for (var property : state.getProperties()) { writeString(out, property.getName()); writeString(out, valueName(property, state.getValue(property))); }
            }
            out.writeInt(data.blocks.size());
            for (BlueprintBlock b : data.blocks) {
                out.writeInt(b.relativePos().getX()); out.writeInt(b.relativePos().getY()); out.writeInt(b.relativePos().getZ());
                out.writeInt(palette.get(b.state()));
                writeString(out, BuiltInRegistries.FLUID.getKey(b.fluid().getType()).toString());
                writeString(out, b.blockEntity() == null ? "" : b.blockEntity().toString());
            }
        }
        return bytes.toByteArray();
    }
    @SuppressWarnings({"rawtypes", "unchecked"}) private static String valueName(Property property, Comparable value) { return property.getName(value); }
    public static BlueprintData decode(byte[] bytes, UUID id, BlueprintConfig config) throws IOException {
        byte[] expanded = BoundedStreams.expand(bytes, config.maxUploadBytes, config.maxExpandedBytes);
        try (var in = new DataInputStream(new ByteArrayInputStream(expanded))) {
            require(in.readInt() == MAGIC, "Unsupported blueprint format");
            String name = readString(in, 256).strip(); require(!name.isEmpty() && name.length() <= 80 && name.chars().noneMatch(c -> c < 32 || c == 127), "Invalid name");
            int x = in.readInt(), y = in.readInt(), z = in.readInt();
            require(x > 0 && y > 0 && z > 0 && x <= config.maxDimension && y <= config.maxDimension && z <= config.maxDimension, "Invalid dimensions");
            require((long) x * y * z <= config.maxBlueprintBlocks, "Blueprint bounding volume exceeds limit");
            int paletteSize = in.readInt(); require(paletteSize > 0 && paletteSize <= Math.min(config.maxBlueprintBlocks, 65536), "Invalid palette size");
            List<BlockState> palette = new ArrayList<>();
            for (int i = 0; i < paletteSize; i++) {
                Identifier blockId = Identifier.tryParse(readString(in, 256));
                require(blockId != null && BuiltInRegistries.BLOCK.containsKey(blockId), "Unknown block ID");
                BlockState state = BuiltInRegistries.BLOCK.getValue(blockId).defaultBlockState();
                require(!state.is(FORBIDDEN_BLOCKS), "Forbidden technical block: " + blockId);
                int properties = in.readInt(); require(properties == state.getProperties().size(), "Incomplete state properties");
                Set<String> seenProperties = new HashSet<>();
                for (int p = 0; p < properties; p++) {
                    String key = readString(in, 64), value = readString(in, 64);
                    Property<?> property = state.getBlock().getStateDefinition().getProperty(key);
                    require(property != null && seenProperties.add(key), "Unknown or duplicate property");
                    state = withProperty(state, property, value);
                }
                palette.add(state);
            }
            int count = in.readInt(); require(count > 0 && count <= config.maxBlueprintBlocks && count <= (long) x * y * z, "Invalid block count");
            List<BlueprintBlock> blocks = new ArrayList<>(count); Set<BlockPos> coordinates = new HashSet<>();
            for (int i = 0; i < count; i++) {
                int bx = in.readInt(), by = in.readInt(), bz = in.readInt(), index = in.readInt();
                require(bx >= 0 && bx < x && by >= 0 && by < y && bz >= 0 && bz < z && index >= 0 && index < paletteSize, "Invalid coordinate or palette index");
                BlockPos pos = new BlockPos(bx, by, bz); require(coordinates.add(pos), "Duplicate coordinate");
                BlockState state = palette.get(index);
                String fluid = readString(in, 256);
                require(fluid.equals(BuiltInRegistries.FLUID.getKey(state.getFluidState().getType()).toString()), "Fluid contradicts block state");
                String snbt = readString(in, config.maxBlockEntityBytes); CompoundTag nbt = null;
                if (!snbt.isEmpty()) {
                    try { nbt = BlueprintNbtSanitizer.sanitize(TagParser.parseCompoundFully(snbt), state, config.maxBlockEntityBytes); }
                    catch (Exception e) { throw new IOException("Invalid block entity at " + pos.toShortString() + " (" + BuiltInRegistries.BLOCK.getKey(state.getBlock()) + "): " + e.getMessage(), e); }
                }
                blocks.add(new BlueprintBlock(pos, state, nbt));
            }
            require(in.available() == 0, "Trailing structure data");
            return new BlueprintData(id, name, x, y, z, blocks, new LinkedHashMap<>());
        } catch (Exception e) { if (e instanceof IOException io) throw io; throw new IOException("Invalid prefablitematica: " + e.getMessage(), e); }
    }
    private static <T extends Comparable<T>> BlockState withProperty(BlockState state, Property<T> property, String value) throws IOException {
        T parsed = property.getValue(value).orElseThrow(() -> new IOException("Invalid property value")); return state.setValue(property, parsed);
    }
    public static void writeString(DataOutput out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8); out.writeInt(bytes.length); out.write(bytes);
    }
    public static String readString(DataInput in, int maxBytes) throws IOException {
        int length = in.readInt(); require(length >= 0 && length <= maxBytes, "String exceeds limit");
        byte[] bytes = new byte[length]; in.readFully(bytes);
        return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
    }
    private static void require(boolean condition, String message) throws IOException { if (!condition) throw new IOException(message); }
}
