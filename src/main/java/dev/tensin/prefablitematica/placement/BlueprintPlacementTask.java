// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.placement;

import dev.tensin.prefablitematica.PrefabLitematicaMod;
import dev.tensin.prefablitematica.blueprint.*;
import dev.tensin.prefablitematica.item.BlueprintItem;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.TagValueInput;
import java.lang.reflect.Proxy;
import java.util.*;

/** Reserves a volume, completes preflight, consumes durable charge, then performs bounded phases. */
public final class BlueprintPlacementTask {
    public final ServerLevel world;
    public final BlueprintData data;
    private final BlueprintPlacementManager manager;
    private final ServerPlayer player;
    private final ItemStack item;
    private final BlockPos origin, maximum;
    private final BlueprintRotation rotation;
    private final int width, depth;
    private final List<BlueprintBlock> ordered = new ArrayList<>();
    private final Map<BlockPos, BlockState> future = new HashMap<>();
    private final Set<net.minecraft.world.level.ChunkPos> pinned = new HashSet<>();
    private int phase, index;
    private boolean chargedConsumed;
    private int fireWarnings;
    public BlueprintPlacementTask(BlueprintPlacementManager manager, ServerPlayer player, ItemStack item, BlueprintData data, BlockPos origin, BlueprintRotation rotation) {
        this.manager = manager; this.player = player; this.item = item; this.data = data; this.origin = origin; this.rotation = rotation;
        world = player.level(); width = rotation.ordinal() % 2 == 0 ? data.sizeX : data.sizeZ; depth = rotation.ordinal() % 2 == 0 ? data.sizeZ : data.sizeX;
        maximum = origin.offset(width - 1, data.sizeY - 1, depth - 1);
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
                    PlacementValidator.validate(player, pos);
                    var chunk = new net.minecraft.world.level.ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);
                    if (pinned.add(chunk)) world.getChunkSource().addTicketWithRadius(PrefabLitematicaMod.PLACEMENT_TICKET, chunk, 0);
                    continue;
                }
                phase++; index = 0;
            }
            if (phase == 1) {
                if (index < data.blocks.size()) {
                    var b = data.blocks.get(index++); var transformed = new BlueprintBlock(target(b), b.state().rotate(rotation.vanilla), b.blockEntity());
                    ordered.add(transformed); future.put(transformed.relativePos(), transformed.state()); continue;
                }
                ordered.sort(Comparator.comparingInt((BlueprintBlock b) -> stage(b.state())).thenComparingInt(b -> b.relativePos().getY()));
                phase++; index = 0;
            }
            if (phase == 2) {
                LevelReader overlay = (LevelReader) Proxy.newProxyInstance(LevelReader.class.getClassLoader(), new Class<?>[]{LevelReader.class}, (proxy, method, args) -> {
                    if (args != null && args.length == 1 && args[0] instanceof BlockPos pos) {
                        if (method.getName().equals("getBlockState")) return future.getOrDefault(pos, world.getBlockState(pos));
                        if (method.getName().equals("getFluidState")) return future.getOrDefault(pos, world.getBlockState(pos)).getFluidState();
                    }
                    return method.invoke(world, args);
                });
                if (index < ordered.size()) {
                    var b = ordered.get(index++);
                    if (!(b.state().getBlock() instanceof BaseFireBlock) && !b.state().canSurvive(overlay, b.relativePos()))
                        throw new IllegalArgumentException("Missing support: " + b.relativePos().toShortString());
                    continue;
                }
                // Before changing the world, persist the debit. Charge never comes back after an interrupted placement.
                data.reset(); PrefabLitematicaMod.manager(manager.server).saveProgress(data); chargedConsumed = true;
                phase++; index = 0;
                player.sendSystemMessage(Component.translatable("message.prefablitematica.placing"), false);
            }
            if (phase == 3) {
                if (index < ordered.size()) {
                    var b = ordered.get(index++);
                    if (b.state().getBlock() instanceof BaseFireBlock || standaloneFluid(b.state())) continue;
                    BlockState state = b.state(); if (state.hasProperty(BlockStateProperties.WATERLOGGED)) state = state.setValue(BlockStateProperties.WATERLOGGED, false);
                    set(b.relativePos(), state); continue;
                }
                phase++; index = 0;
            }
            if (phase == 4) {
                if (index < ordered.size()) {
                    var b = ordered.get(index++);
                    if (b.blockEntity() != null) {
                        var be = world.getBlockEntity(b.relativePos());
                        if (be != null) {
                            be.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, world.registryAccess(), b.blockEntity().copy()));
                            if (be instanceof Container container) container.clearContent(); be.setChanged();
                        }
                    }
                    continue;
                }
                phase++; index = 0;
            }
            if (phase == 5) {
                if (index < ordered.size()) {
                    var b = ordered.get(index++);
                    if (b.state().hasProperty(BlockStateProperties.WATERLOGGED) && b.state().getValue(BlockStateProperties.WATERLOGGED)) set(b.relativePos(), b.state());
                    continue;
                }
                phase++; index = 0;
            }
            if (phase == 6 || phase == 7) {
                if (index < ordered.size()) {
                    var b = ordered.get(index++);
                    if (standaloneFluid(b.state()) && b.fluid().is(net.minecraft.tags.FluidTags.WATER) == (phase == 6)) set(b.relativePos(), b.state());
                    continue;
                }
                phase++; index = 0;
            }
            if (phase == 8) {
                if (index < ordered.size()) {
                    var b = ordered.get(index++);
                    if (b.state().getBlock() instanceof BaseFireBlock) {
                        if (b.state().canSurvive(world, b.relativePos())) set(b.relativePos(), b.state());
                        else { fireWarnings++; PrefabLitematicaMod.LOGGER.warn("Skipped unsupported fire at {}", b.relativePos()); }
                    }
                    continue;
                }
                phase++; index = 0;
            }
            if (phase == 9) {
                if (index < ordered.size()) {
                    var b = ordered.get(index++); var pos = b.relativePos();
                    manager.write(() -> {
                        BlockState current = world.getBlockState(pos);
                        world.updateNeighborsAt(pos, current.getBlock());
                        current.updateNeighbourShapes(world, pos, Block.UPDATE_CLIENTS);
                        if (!current.getFluidState().isEmpty()) world.scheduleTick(pos, current.getFluidState().getType(), 1);
                        if (current.getBlock() instanceof FallingBlock || current.getBlock() instanceof BaseFireBlock) world.scheduleTick(pos, current.getBlock(), 1);
                    });
                    continue;
                }
                if (PrefabLitematicaMod.CONFIG.consumeBlueprintAfterPlacement) {
                    data.retired = true; PrefabLitematicaMod.manager(manager.server).saveProgress(data);
                    if (BlueprintItem.id(item) != null && BlueprintItem.id(item).equals(data.id)) item.shrink(1);
                }
                player.sendSystemMessage(Component.translatable("message.prefablitematica.complete", fireWarnings), false); return true;
            }
        }
        return false;
    }
    private void set(BlockPos pos, BlockState state) {
        if (!world.hasChunkAt(pos)) throw new IllegalStateException("Chunk unloaded during placement");
        manager.write(() -> {
            var old = world.getBlockEntity(pos); if (old instanceof Container container) container.clearContent();
            if (!world.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_SKIP_ON_PLACE | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS))
                if (world.getBlockState(pos) != state) throw new IllegalStateException("World refused block placement");
        });
    }
    private static int stage(BlockState state) {
        if (state.getBlock() instanceof BaseFireBlock) return 10;
        if (standaloneFluid(state)) return 9;
        if (state.getBlock() instanceof FallingBlock) return 5;
        return state.isSolid() ? 1 : 6;
    }
    private static boolean standaloneFluid(BlockState state) {
        return state.getBlock() instanceof LiquidBlock || state.getBlock() instanceof BubbleColumnBlock;
    }
    public void fail(String message) { player.sendSystemMessage(Component.literal("Blueprint placement stopped: " + message), false); }
    public void close() {
        data.locked = false;
        if (chargedConsumed) try { world.getChunkSource().save(true); PrefabLitematicaMod.manager(manager.server).saveProgress(data); }
        catch (Exception e) { PrefabLitematicaMod.LOGGER.error("Failed final placement save", e); }
        pinned.forEach(chunk -> world.getChunkSource().removeTicketWithRadius(PrefabLitematicaMod.PLACEMENT_TICKET, chunk, 0));
    }
}
