// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.integration.litematica;

import dev.tensin.prefablitematica.blueprint.BlueprintData;
import dev.tensin.prefablitematica.placement.BlueprintRotation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import java.lang.reflect.Method;
import java.util.Collection;

/** Optional, transient Litematica placement; never writes a schematic file or changes global render settings. */
public final class LitematicaProjection implements AutoCloseable {
    public record Pose(BlockPos origin, BlueprintRotation rotation) {}
    private final BlueprintData data;
    private final Object manager, schematic, container, previousSelection;
    private final Class<?> placementType;
    private final Method set;
    private Object placement;
    private int index;
    private boolean closed;
    public LitematicaProjection(BlueprintData data) throws Exception {
        this.data = data;
        Class<?> areaType = Class.forName("fi.dy.masa.litematica.selection.AreaSelection");
        Class<?> boxType = Class.forName("fi.dy.masa.litematica.selection.Box");
        Class<?> schematicType = Class.forName("fi.dy.masa.litematica.schematic.LitematicaSchematic");
        placementType = Class.forName("fi.dy.masa.litematica.schematic.placement.SchematicPlacement");
        Object area = areaType.getConstructor().newInstance();
        areaType.getMethod("setName", String.class).invoke(area, data.name);
        Object box = boxType.getConstructor(BlockPos.class, BlockPos.class, String.class).newInstance(BlockPos.ZERO, new BlockPos(data.sizeX - 1, data.sizeY - 1, data.sizeZ - 1), "blueprint");
        areaType.getMethod("addSubRegionBox", boxType, boolean.class).invoke(area, box, true);
        schematic = schematicType.getMethod("createEmptySchematic", areaType, String.class).invoke(null, area, "PrefabLitematica");
        container = schematicType.getMethod("getSubRegionContainer", String.class).invoke(schematic, "blueprint");
        set = container.getClass().getMethod("set", int.class, int.class, int.class, net.minecraft.world.level.block.state.BlockState.class);
        manager = Class.forName("fi.dy.masa.litematica.data.DataManager").getMethod("getSchematicPlacementManager").invoke(null);
        previousSelection = manager.getClass().getMethod("getSelectedSchematicPlacement").invoke(manager);
    }
    public boolean tick(int budget, BlockPos origin, BlueprintRotation rotation) throws Exception {
        if (closed) return false;
        while (budget-- > 0 && index < data.blocks.size()) {
            var block = data.blocks.get(index++); var pos = block.relativePos();
            set.invoke(container, pos.getX(), pos.getY(), pos.getZ(), block.state());
        }
        if (index == data.blocks.size() && placement == null) {
            placement = placementType.getMethod("createFor", schematic.getClass(), BlockPos.class, String.class, boolean.class, boolean.class)
                    .invoke(null, schematic, origin, "[Blueprint] " + data.name, true, true);
            placementType.getMethod("setShouldBeSaved", boolean.class).invoke(placement, false);
            move(origin, rotation);
            manager.getClass().getMethod("addSchematicPlacement", placementType, boolean.class).invoke(manager, placement, false);
        }
        return placement != null;
    }
    /** Litematica rotates around its corner; blueprint coordinates always refer to the minimum corner. */
    public static BlockPos cornerOffset(BlueprintData data, BlueprintRotation rotation) {
        return switch (rotation) {
            case NONE -> BlockPos.ZERO;
            case CW_90 -> new BlockPos(data.sizeZ - 1, 0, 0);
            case CW_180 -> new BlockPos(data.sizeX - 1, 0, data.sizeZ - 1);
            case CW_270 -> new BlockPos(0, 0, data.sizeX - 1);
        };
    }
    public void move(BlockPos origin, BlueprintRotation rotation) throws Exception {
        if (placement == null) return;
        if ((boolean) placementType.getMethod("isLocked").invoke(placement)) throw new IllegalArgumentException("Temporary blueprint projection is locked");
        placementType.getMethod("setRotation", Rotation.class, Class.forName("fi.dy.masa.malilib.gui.interfaces.IMessageConsumer")).invoke(placement, rotation.vanilla, null);
        placementType.getMethod("setOrigin", BlockPos.class, Class.forName("fi.dy.masa.malilib.interfaces.IStringConsumer")).invoke(placement, origin.offset(cornerOffset(data, rotation)), null);
    }
    public Pose pose() throws Exception {
        if (placement == null) return null;
        // Native edits beyond the blueprint's supported pose fall back to its authoritative geometry.
        if (placementType.getMethod("getMirror").invoke(placement) != Mirror.NONE || (boolean) placementType.getMethod("isRegionPlacementModified").invoke(placement))
            throw new IllegalArgumentException("Blueprint projections support whole-building position and rotation only");
        Rotation vanilla = (Rotation) placementType.getMethod("getRotation").invoke(placement);
        BlueprintRotation rotation = java.util.Arrays.stream(BlueprintRotation.values()).filter(r -> r.vanilla == vanilla).findFirst().orElseThrow();
        BlockPos corner = (BlockPos) placementType.getMethod("getOrigin").invoke(placement);
        return new Pose(corner.subtract(cornerOffset(data, rotation)), rotation);
    }
    public boolean visible() throws Exception {
        if (placement == null || !(boolean) placementType.getMethod("isEnabled").invoke(placement) || !(boolean) placementType.getMethod("isRenderingEnabled").invoke(placement)) return false;
        Class<?> visuals = Class.forName("fi.dy.masa.litematica.config.Configs$Visuals");
        for (String setting : new String[]{"ENABLE_RENDERING", "ENABLE_SCHEMATIC_RENDERING", "ENABLE_SCHEMATIC_BLOCKS"}) {
            Object config = visuals.getField(setting).get(null);
            if (!(boolean) config.getClass().getMethod("getBooleanValue").invoke(config)) return false;
        }
        return true;
    }
    public Object placement() { return placement; }
    @Override public void close() throws Exception {
        if (closed) return;
        closed = true;
        if (placement != null) {
            boolean selected = manager.getClass().getMethod("getSelectedSchematicPlacement").invoke(manager) == placement;
            manager.getClass().getMethod("removeSchematicPlacement", placementType).invoke(manager, placement);
            if (selected) {
                var all = (Collection<?>) manager.getClass().getMethod("getAllSchematicsPlacements").invoke(manager);
                manager.getClass().getMethod("setSelectedSchematicPlacement", placementType).invoke(manager, all.contains(previousSelection) ? previousSelection : null);
            }
            placement = null;
        }
    }
}
