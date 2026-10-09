// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.placement;

import dev.tensin.prefablitematica.blueprint.BlueprintBlock;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.lang.reflect.Method;
import java.util.Map;

/** Calls Litematica's actual direct-paste implementation on an integrated server. */
public final class LitematicaPaste {
    private static final int CLEAR_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;
    private final ServerLevel world;
    private final BlockPos origin;
    private final Object container, placement, replace, layers;
    private final Map<BlockPos, Object> entities;
    private final Map<ChunkPos, List<BlockPos>> clearPositions = new HashMap<>();
    private final Method set, convert, paste;

    public static boolean available() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT && FabricLoader.getInstance().isModLoaded("litematica");
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public LitematicaPaste(ServerLevel world, BlockPos origin, int width, int height, int depth, String name) throws ReflectiveOperationException {
        this.world = world; this.origin = origin;
        Class<?> areaType = Class.forName("fi.dy.masa.litematica.selection.AreaSelection");
        Class<?> boxType = Class.forName("fi.dy.masa.litematica.selection.Box");
        Class<?> schematicType = Class.forName("fi.dy.masa.litematica.schematic.LitematicaSchematic");
        Class<?> placementType = Class.forName("fi.dy.masa.litematica.schematic.placement.SchematicPlacement");
        Object area = areaType.getConstructor().newInstance();
        areaType.getMethod("setName", String.class).invoke(area, name);
        Object box = boxType.getConstructor(BlockPos.class, BlockPos.class, String.class).newInstance(BlockPos.ZERO, new BlockPos(width - 1, height - 1, depth - 1), "blueprint");
        areaType.getMethod("addSubRegionBox", boxType, boolean.class).invoke(area, box, true);
        Object schematic = schematicType.getMethod("createEmptySchematic", areaType, String.class).invoke(null, area, "PrefabLitematica");
        container = schematicType.getMethod("getSubRegionContainer", String.class).invoke(schematic, "blueprint");
        entities = (Map<BlockPos, Object>) schematicType.getMethod("getBlockEntityMapForRegion", String.class).invoke(schematic, "blueprint");
        set = container.getClass().getMethod("set", int.class, int.class, int.class, BlockState.class);
        convert = Class.forName("fi.dy.masa.malilib.util.data.tag.converter.DataConverterNbt").getMethod("fromVanillaCompound", CompoundTag.class);
        placement = placementType.getMethod("createTemporary", schematicType, BlockPos.class).invoke(null, schematic, origin);
        Class<? extends Enum> replaceType = (Class<? extends Enum>) Class.forName("fi.dy.masa.litematica.util.ReplaceBehavior");
        Class<? extends Enum> layerType = (Class<? extends Enum>) Class.forName("fi.dy.masa.litematica.util.PasteLayerBehavior");
        replace = Enum.valueOf(replaceType, "WITH_NON_AIR"); layers = Enum.valueOf(layerType, "ALL");
        paste = Class.forName("fi.dy.masa.litematica.util.SchematicPlacingUtils").getMethod("placeToWorldWithinChunk", Level.class, ChunkPos.class, placementType, replaceType, layerType, boolean.class);
    }
    public void add(BlueprintBlock block) throws ReflectiveOperationException {
        if (block.state().isAir()) {
            BlockPos clearPos = block.relativePos().immutable();
            ChunkPos chunk = new ChunkPos(clearPos.getX() >> 4, clearPos.getZ() >> 4);
            clearPositions.computeIfAbsent(chunk, ignored -> new ArrayList<>()).add(clearPos);
            return;
        }
        BlockPos pos = block.relativePos().subtract(origin);
        set.invoke(container, pos.getX(), pos.getY(), pos.getZ(), block.state());
        if (block.blockEntity() != null) entities.put(pos, convert.invoke(null, block.blockEntity().copy()));
    }
    public void paste(ChunkPos chunk) throws ReflectiveOperationException {
        if (!(boolean) paste.invoke(null, world, chunk, placement, replace, layers, false))
            throw new IllegalStateException("Litematica refused blueprint chunk " + chunk);
        for (BlockPos pos : clearPositions.getOrDefault(chunk, List.of())) {
            if (!world.setBlock(pos, Blocks.AIR.defaultBlockState(), CLEAR_FLAGS) && !world.getBlockState(pos).isAir())
                throw new IllegalStateException("World refused to clear blueprint position " + pos);
        }
    }
}
