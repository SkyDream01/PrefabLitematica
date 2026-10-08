// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.block.entity;

import dev.tensin.prefablitematica.PrefabLitematicaMod;
import dev.tensin.prefablitematica.blueprint.BlueprintData;
import dev.tensin.prefablitematica.item.BlueprintItem;
import dev.tensin.prefablitematica.screen.BlueprintWorkbenchScreenHandler;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import java.io.IOException;
import java.util.ArrayList;

public final class BlueprintWorkbenchBlockEntity extends BlockEntity implements Container, MenuProvider {
    // Keep old persisted indices; slot 10 is migration storage only, never a new input slot.
    public static final int BLUEPRINT_SLOT = 0, MATERIAL_START = 1, MATERIAL_END = 10, LEGACY_BATTERY_SLOT = 10, OUTPUT_SLOT = 11, OUTPUT_END = 20, SLOTS = OUTPUT_END;
    public static final int MATERIAL_STACK_LIMIT = 4096;
    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    public BlueprintWorkbenchBlockEntity(BlockPos pos, BlockState state) { super(PrefabLitematicaMod.WORKBENCH_ENTITY, pos, state); }
    public BlueprintData blueprint() { return level instanceof ServerLevel server ? PrefabLitematicaMod.manager(server.getServer()).get(BlueprintItem.id(getItem(0))) : null; }
    public void charge(ServerPlayer player) throws IOException {
        BlueprintData data = blueprint();
        if (data == null || data.locked) throw new IllegalArgumentException("No available blueprint");
        if (data.fullyCharged()) return;
        var manager = PrefabLitematicaMod.manager(player.level().getServer());
        int[] before = data.requirements.values().stream().mapToInt(r -> r.supplied).toArray();
        boolean changed = false;
        int batterySlot = -1;
        if (PrefabLitematicaMod.CONFIG.creativeBatteryEnabled) for (int slot = MATERIAL_START; slot < MATERIAL_END; slot++)
            if (getItem(slot).is(PrefabLitematicaMod.BATTERY)) { batterySlot = slot; break; }
        if (batterySlot >= 0) {
            getItem(batterySlot).shrink(1); data.fill(); changed = true;
        } else {
            for (int slot = MATERIAL_START; slot < MATERIAL_END; slot++) {
                ItemStack stack = getItem(slot); if (stack.isEmpty()) continue;
                if (isShulkerBox(stack)) {
                    var contents = new ArrayList<>(stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).itemCopies().toList());
                    if (contents.stream().anyMatch(item -> !item.isEmpty())) {
                        boolean used = false;
                        for (ItemStack contained : contents) {
                            // Do not consume another container's contents as a shell material.
                            if (!isShulkerBox(contained)) used |= consumeMaterial(data, contained, player);
                        }
                        if (used) {
                            ItemStack returned = stack.split(1);
                            returned.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
                            returnContainer(player, returned); changed = true;
                        }
                        continue;
                    }
                }
                changed |= consumeMaterial(data, stack, player);
            }
        }
        if (!changed) return;
        setChanged();
        // Persist consumed input and returned containers before making charge durable.
        // save(true) waits for region storage synchronization in Minecraft 26.3.
        ((ServerLevel) level).getChunkSource().save(true);
        player.level().getServer().getPlayerList().saveAll();
        try { manager.saveProgress(data); }
        catch (IOException e) { int index = 0; for (var r : data.requirements.values()) r.supplied = before[index++]; throw e; }
    }
    private boolean consumeMaterial(BlueprintData data, ItemStack stack, ServerPlayer player) {
        if (stack.isEmpty()) return false;
        boolean changed = false;
        var equivalence = PrefabLitematicaMod.manager(player.level().getServer()).equivalence;
        for (var requirement : data.requirements.values()) {
            if (!equivalence.accepts(requirement, stack)) continue;
            int accepted = requirement.offer(stack.getCount());
            if (accepted > 0) {
                boolean bucket = stack.is(Items.WATER_BUCKET) || stack.is(Items.LAVA_BUCKET) || stack.is(Items.POWDER_SNOW_BUCKET);
                stack.shrink(accepted); changed = true;
                if (bucket) returnContainer(player, new ItemStack(Items.BUCKET, accepted));
            }
            if (stack.isEmpty()) break;
        }
        return changed;
    }
    private void returnContainer(ServerPlayer player, ItemStack returned) {
        // Fill matching returns before using an empty slot in the 3x3 output grid.
        for (int slot = OUTPUT_SLOT; slot < OUTPUT_END && !returned.isEmpty(); slot++) {
            ItemStack output = getItem(slot);
            if (!output.isEmpty() && ItemStack.isSameItemSameComponents(output, returned)) {
                int amount = Math.min(returned.getCount(), output.getMaxStackSize() - output.getCount());
                if (amount > 0) { output.grow(amount); returned.shrink(amount); }
            }
        }
        for (int slot = OUTPUT_SLOT; slot < OUTPUT_END && !returned.isEmpty(); slot++) {
            if (getItem(slot).isEmpty()) setItem(slot, returned.split(Math.min(returned.getCount(), returned.getMaxStackSize())));
        }
        if (!returned.isEmpty()) {
            player.getInventory().add(returned);
            if (!returned.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + .5, worldPosition.getY() + 1, worldPosition.getZ() + .5, returned);
        }
    }
    public static boolean isShulkerBox(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof ShulkerBoxBlock;
    }
    public static int materialStackLimit(ItemStack stack) {
        return isShulkerBox(stack) || stack.isDamageableItem() ? stack.getMaxStackSize() : MATERIAL_STACK_LIMIT;
    }
    private void migrateLegacyInput() {
        if (getItem(LEGACY_BATTERY_SLOT).isEmpty()) return;
        for (int slot = MATERIAL_START; slot < MATERIAL_END; slot++) if (getItem(slot).isEmpty()) {
            items.set(slot, ContainerHelper.takeItem(items, LEGACY_BATTERY_SLOT)); setChanged(); return;
        }
    }
    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input); items.clear(); ContainerHelper.loadAllItems(input, items);
        input.getIntArray("MaterialCounts").ifPresent(counts -> {
            for (int slot = MATERIAL_START; slot < MATERIAL_END && slot - MATERIAL_START < counts.length; slot++) {
                ItemStack stack = getItem(slot); int count = counts[slot - MATERIAL_START];
                if (!stack.isEmpty() && count > 0 && count <= materialStackLimit(stack)) stack.setCount(count);
            }
        });
        migrateLegacyInput();
    }
    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        // Vanilla's disk ItemStack codec accepts at most 99. Store each material's
        // components on a single item and its actual quantity separately.
        var saved = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        int[] counts = new int[MATERIAL_END - MATERIAL_START];
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = getItem(slot);
            if (slot >= MATERIAL_START && slot < MATERIAL_END) {
                counts[slot - MATERIAL_START] = stack.getCount(); saved.set(slot, stack.copyWithCount(1));
            } else saved.set(slot, stack);
        }
        ContainerHelper.saveAllItems(output, saved); output.putIntArray("MaterialCounts", counts);
    }
    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) { if (level != null) Containers.dropContents(level, pos, this); super.preRemoveSideEffects(pos, state); }
    @Override public Component getDisplayName() { return Component.translatable("block.prefablitematica.workbench"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        migrateLegacyInput();
        if (!getItem(LEGACY_BATTERY_SLOT).isEmpty()) {
            ItemStack overflow = removeItemNoUpdate(LEGACY_BATTERY_SLOT);
            if (!player.getInventory().add(overflow)) Containers.dropItemStack(level, worldPosition.getX() + .5, worldPosition.getY() + 1, worldPosition.getZ() + .5, overflow);
            setChanged();
        }
        return new BlueprintWorkbenchScreenHandler(id, inventory, this);
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == BLUEPRINT_SLOT ? BlueprintItem.isBlueprint(stack) : slot >= MATERIAL_START && slot < MATERIAL_END;
    }
    @Override public int getContainerSize() { return SLOTS; }
    @Override public int getMaxStackSize() { return MATERIAL_STACK_LIMIT; }
    @Override public int getMaxStackSize(ItemStack stack) { return materialStackLimit(stack); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) { ItemStack result = ContainerHelper.removeItem(items, slot, count); setChanged(); return result; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) {
        int limit = slot >= MATERIAL_START && slot < MATERIAL_END ? materialStackLimit(stack) : stack.getMaxStackSize();
        if (stack.getCount() > limit) stack.setCount(limit);
        items.set(slot, stack); setChanged();
    }
    @Override public boolean stillValid(Player player) { return level != null && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5) <= 64; }
    @Override public void clearContent() { items.clear(); setChanged(); }
}
