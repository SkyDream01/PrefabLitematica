// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.client;

import net.fabricmc.fabric.api.client.rendering.v1.level.*;
import net.minecraft.gizmos.*;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.phys.*;
import java.util.*;

/** Red obstruction markers complement Litematica, including occupied empty cells and protected terrain. */
public final class BlueprintProjectionRenderer {
    private record Frame(AABB bounds, List<AABB> ghosts, List<AABB> conflicts, Vec3 direction) {}
    private static volatile Frame frame;
    private static List<AABB> conflicts = List.of();
    private static boolean dirty = true;
    private static final GizmoStyle RED = GizmoStyle.strokeAndFill(0xFFFF3030, 2.5f, 0x66FF2020);
    private static final GizmoStyle GHOST = GizmoStyle.strokeAndFill(0x883ADBE8, 1, 0x333ADBE8);
    private BlueprintProjectionRenderer() {}
    public static void invalidate() { dirty = true; }
    public static void register() {
        LevelExtractionEvents.END_EXTRACTION.register(context -> {
            var preview = BlueprintProjectionClient.INSTANCE;
            if (!preview.active() || !preview.shown()) { frame = null; return; }
            int width = preview.width(), height = preview.height(), depth = preview.depth(); var origin = preview.origin();
            if (dirty) {
                var boxes = new ArrayList<AABB>(); var cells = preview.conflicts();
                // Merge adjacent cells along X to keep large obstructed regions inexpensive to draw.
                for (int cell = cells.nextSetBit(0); cell >= 0;) {
                    int end = Math.min(cells.nextClearBit(cell), (cell / width + 1) * width);
                    int x = cell % width, y = cell / (width * depth), z = (cell / width) % depth;
                    boxes.add(new AABB(origin.getX() + x, origin.getY() + y, origin.getZ() + z, origin.getX() + x + end - cell, origin.getY() + y + 1, origin.getZ() + z + 1).inflate(0.005));
                    cell = cells.nextSetBit(end);
                }
                conflicts = List.copyOf(boxes); dirty = false;
            }
            Vec3 camera = context.camera().position();
            var ghosts = new ArrayList<AABB>(); var data = preview.data();
            if (!preview.litematicaVisible() && data != null) {
                for (var block : data.blocks) {
                    if (block.state().isAir()) continue;
                    var pos = origin.offset(preview.rotation().apply(block.relativePos(), data.sizeX, data.sizeZ));
                    if (Vec3.atCenterOf(pos).distanceToSqr(camera) > 128 * 128) continue;
                    var shape = block.state().rotate(preview.rotation().vanilla).getShape(EmptyBlockGetter.INSTANCE, pos);
                    if (shape.isEmpty()) ghosts.add(new AABB(pos));
                    else for (var box : shape.toAabbs()) ghosts.add(box.move(pos.getX(), pos.getY(), pos.getZ()));
                    if (ghosts.size() >= 8192) break;
                }
            }
            Vec3 direction = switch (preview.rotation()) {
                case NONE -> new Vec3(0, 0, 3); case CW_90 -> new Vec3(-3, 0, 0);
                case CW_180 -> new Vec3(0, 0, -3); case CW_270 -> new Vec3(3, 0, 0);
            };
            frame = new Frame(new AABB(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + width, origin.getY() + height, origin.getZ() + depth), List.copyOf(ghosts), conflicts, direction);
        });
        LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
            Frame current = frame; if (current == null) return;
            try (var ignored = context.levelRenderer().collectPerFrameRenderThreadGizmos()) {
                Gizmos.cuboid(current.bounds, GizmoStyle.stroke(0xFF44DDEE, 2)).setAlwaysOnTop();
                for (var box : current.ghosts) Gizmos.cuboid(box, GHOST);
                for (var box : current.conflicts) Gizmos.cuboid(box, RED).setAlwaysOnTop();
                var center = new Vec3((current.bounds.minX + current.bounds.maxX) / 2, current.bounds.minY + 0.1, (current.bounds.minZ + current.bounds.maxZ) / 2);
                Gizmos.arrow(center, center.add(current.direction), 0xFF55FFAA).setAlwaysOnTop();
            }
        });
    }
}
