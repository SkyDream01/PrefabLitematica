// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.placement;

import dev.tensin.prefablitematica.PrefabLitematicaMod;
import dev.tensin.prefablitematica.blueprint.*;
import dev.tensin.prefablitematica.item.BlueprintItem;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import java.util.*;

/** Reserves a volume, debits durable charge, then pastes exact states without placement updates. */
public final class BlueprintPlacementTask {
    public final ServerLevel world;
    public final BlueprintData data;
    private final BlueprintPlacementManager manager;
    private final ServerPlayer player;
    private final ItemStack item;
    private final BlockPos origin, maximum;
    private final BlueprintRotation rotation;
    private final int width, depth;
    private final Map<ChunkPos, List<BlueprintBlock>> blocksByChunk = new LinkedHashMap<>();
    private final Set<BlockPos> preservedPositions = new HashSet<>();
    private List<ChunkPos> chunks;
    private LitematicaPaste nativePaste;
    private final Set<net.minecraft.world.level.ChunkPos> pinned = new HashSet<>();
    private int phase, index;
    private boolean chargedConsumed;
    public BlueprintPlacementTask(BlueprintPlacementManager manager, ServerPlayer player, ItemStack item, BlueprintData data, BlockPos origin, BlueprintRotation rotation) {
        this.manager = manager; this.player = player; this.item = item; this.data = data; this.origin = origin; this.rotation = rotation;
        world = player.level(); width = rotation.ordinal() % 2 == 0 ? data.sizeX : data.sizeZ; depth = rotation.ordinal() % 2 == 0 ? data.sizeZ : data.sizeX;
        maximum = origin.offset(width - 1, data.sizeY - 1, depth - 1);
        for (BlueprintBlock block : data.blocks) {
            if (BlueprintBlockPolicy.isPreserved(block.state()))
                preservedPositions.add(origin.offset(rotation.apply(block.relativePos(), data.sizeX, data.sizeZ)));
        }
    }
    private BlockPos target(BlueprintBlock b) { return origin.offset(rotation.apply(b.relativePos(), data.sizeX, data.sizeZ)); }
    public boolean contains(BlockPos p) { return p.getX() >= origin.getX() && p.getX() <= maximum.getX() && p.getY() >= origin.getY() && p.getY() <= maximum.getY() && p.getZ() >= origin.getZ() && p.getZ() <= maximum.getZ(); }
    public boolean intersects(BlueprintPlacementTask other) {
        return world == other.world && origin.getX() <= other.maximum.getX() && maximum.getX() >= other.origin.getX()
                && origin.getY() <= other.maximum.getY() && maximum.getY() >= other.origin.getY()
                && origin.getZ() <= other.maximum.getZ() && maximum.getZ() >= other.origin.getZ();
    }
    public boolean tick(int budget) throws Exception {
        if (!chargedConsumed && (!player.isAlive() || player.hasDisconnected())) throw new IllegalArgumentException("Player is unavailable");
        while (budget-- > 0) {
            if (phase == 0) {
                int volume = width * data.sizeY * depth;
                if (index < volume) {
                    int x = index % width, z = (index / width) % depth, y = index / (width * depth); index++;
                    BlockPos pos = origin.offset(x, y, z);
                    if (!preservedPositions.contains(pos)) PlacementValidator.validate(player, pos);
                    var chunk = new net.minecraft.world.level.ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);
                    if (pinned.add(chunk)) world.getChunkSource().addTicketWithRadius(PrefabLitematicaMod.PLACEMENT_TICKET, chunk, 0);
                    continue;
                }
                phase++; index = 0;
            }
            if (phase == 1) {
                if (index == 0 && nativePaste == null && LitematicaPaste.available())
                    nativePaste = new LitematicaPaste(world, origin, width, data.sizeY, depth, data.name);
                if (index < data.blocks.size()) {
                    var b = data.blocks.get(index++); var transformed = new BlueprintBlock(target(b), b.state().rotate(rotation.vanilla), b.blockEntity());
                    if (BlueprintBlockPolicy.isPreserved(transformed.state())) continue;
                    blocksByChunk.computeIfAbsent(new ChunkPos(transformed.relativePos().getX() >> 4, transformed.relativePos().getZ() >> 4), ignored -> new ArrayList<>()).add(transformed);
                    if (nativePaste != null) nativePaste.add(transformed);
                    continue;
                }
                chunks = new ArrayList<>(blocksByChunk.keySet());
                blocksByChunk.values().forEach(blocks -> blocks.sort(Comparator.comparingInt((BlueprintBlock b) -> b.relativePos().getY())
                        .thenComparingInt(b -> b.relativePos().getZ()).thenComparingInt(b -> b.relativePos().getX())));
                phase++; index = 0;
            }
            if (phase == 2) {
                // Before changing the world, persist the debit. Charge never comes back after an interrupted placement.
                data.reset(); PrefabLitematicaMod.manager(manager.server).saveProgress(data); chargedConsumed = true;
                phase++; index = 0;
                player.sendSystemMessage(Component.translatable("message.prefablitematica.placing"), false);
            }
            if (phase == 3) {
                if (index < chunks.size()) {
                    var chunk = chunks.get(index++); var blocks = blocksByChunk.get(chunk);
                    // Litematica pastes a whole chunk, with each block's NBT restored in the same call.
                    // The configured budget is a target: never split an atomic chunk across ticks.
                    if (nativePaste != null) {
                        Exception[] failure = new Exception[1];
                        manager.write(() -> { try { nativePaste.paste(chunk); } catch (Exception e) { failure[0] = e; } });
                        if (failure[0] != null) throw failure[0];
                    } else manager.write(() -> BlueprintPaste.paste(world, blocks));
                    budget -= blocks.size() - 1;
                    continue;
                }
                phase++; index = 0;
            }
            if (phase == 4) {
                if (index < data.scheduledTicks.size()) {
                    var tick = data.scheduledTicks.get(index++);
                    if (!preservedPositions.contains(origin.offset(rotation.apply(tick.position(), data.sizeX, data.sizeZ))))
                        tick.schedule(world, origin, rotation, data.sizeX, data.sizeZ);
                    continue;
                }
                if (PrefabLitematicaMod.CONFIG.consumeBlueprintAfterPlacement) {
                    data.retired = true; PrefabLitematicaMod.manager(manager.server).saveProgress(data);
                    if (BlueprintItem.id(item) != null && BlueprintItem.id(item).equals(data.id)) item.shrink(1);
                }
                player.sendSystemMessage(Component.translatable("message.prefablitematica.complete"), false); return true;
            }
        }
        return false;
    }
    public void fail(String message) { player.sendSystemMessage(Component.literal("Blueprint placement stopped: " + message), false); }
    public void close() {
        data.locked = false;
        if (chargedConsumed) try { world.getChunkSource().save(true); PrefabLitematicaMod.manager(manager.server).saveProgress(data); }
        catch (Exception e) { PrefabLitematicaMod.LOGGER.error("Failed final placement save", e); }
        pinned.forEach(chunk -> world.getChunkSource().removeTicketWithRadius(PrefabLitematicaMod.PLACEMENT_TICKET, chunk, 0));
    }
}
