// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.placement;

import dev.tensin.prefablitematica.PrefabLitematicaMod;
import dev.tensin.prefablitematica.item.BlueprintItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.*;

public final class BlueprintPlacementManager {
    public final MinecraftServer server;
    private final List<BlueprintPlacementTask> tasks = new ArrayList<>();
    private boolean writing;
    public BlueprintPlacementManager(MinecraftServer server) { this.server = server; }
    public void start(ServerPlayer player, ItemStack stack, BlockPos origin, BlueprintRotation rotation) {
        if (!player.mayBuild() || player.isSpectator()) throw new IllegalArgumentException("Building is not permitted");
        if (tasks.size() >= PrefabLitematicaMod.CONFIG.maxConcurrentTasks) throw new IllegalArgumentException("Placement server is busy");
        var data = PrefabLitematicaMod.manager(server).get(BlueprintItem.id(stack));
        if (data == null) throw new IllegalArgumentException("Import a projection at the blueprint workbench first");
        if (data.locked) throw new IllegalArgumentException("Blueprint is already in use");
        if (!data.fullyCharged()) throw new IllegalArgumentException("Blueprint must be charged to 100%");
        var task = new BlueprintPlacementTask(this, player, stack, data, origin, rotation);
        if (tasks.stream().anyMatch(other -> other.intersects(task))) throw new IllegalArgumentException("Another placement reserves this area");
        data.locked = true; tasks.add(task);
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.prefablitematica.validating"), false);
    }
    public boolean reserved(Level world, BlockPos pos) {
        return !writing && tasks.stream().anyMatch(task -> task.world == world && task.contains(pos));
    }
    public void write(Runnable action) { writing = true; try { action.run(); } finally { writing = false; } }
    public void tick() {
        int budget = Math.max(1, PrefabLitematicaMod.CONFIG.blocksPlacedPerTick / Math.max(1, tasks.size()));
        var it = tasks.iterator();
        while (it.hasNext()) {
            var task = it.next();
            try { if (task.tick(budget)) { task.close(); it.remove(); } }
            catch (Exception e) { PrefabLitematicaMod.LOGGER.error("Placement failed for {}", task.data.id, e); task.fail(e.getMessage()); task.close(); it.remove(); }
        }
    }
    public void stop() { tasks.forEach(BlueprintPlacementTask::close); tasks.clear(); }
}
