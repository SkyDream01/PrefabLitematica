// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.screen;

import dev.tensin.prefablitematica.PrefabLitematicaMod;
import dev.tensin.prefablitematica.block.entity.BlueprintWorkbenchBlockEntity;
import dev.tensin.prefablitematica.item.BlueprintItem;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;

public final class BlueprintWorkbenchScreenHandler extends AbstractContainerMenu {
    public final Container inventory;
    public final BlueprintWorkbenchBlockEntity bench;
    public int page;
    public static final int RETURN_START = 10, RETURN_END = RETURN_START + 9, PLAYER_START = RETURN_END;
    private static final int END_PLAYER_SLOTS = PLAYER_START + 36;
    public BlueprintWorkbenchScreenHandler(int syncId, Inventory playerInventory) { this(syncId, playerInventory, new SimpleContainer(BlueprintWorkbenchBlockEntity.SLOTS) {
        @Override public int getMaxStackSize() { return BlueprintWorkbenchBlockEntity.MATERIAL_STACK_LIMIT; }
        @Override public int getMaxStackSize(ItemStack stack) { return BlueprintWorkbenchBlockEntity.materialStackLimit(stack); }
    }); }
    public BlueprintWorkbenchScreenHandler(int syncId, Inventory playerInventory, Container inventory) {
        super(PrefabLitematicaMod.WORKBENCH_MENU, syncId); this.inventory = inventory;
        bench = inventory instanceof BlueprintWorkbenchBlockEntity b ? b : null;
        addSlot(new Slot(inventory, 0, 8, 29) {
            @Override public boolean mayPlace(ItemStack stack) { return BlueprintItem.isBlueprint(stack); }
            @Override public int getMaxStackSize() { return 1; }
        });
        for (int i = 1; i <= 9; i++) addSlot(new Slot(inventory, i, 8 + ((i - 1) % 3) * 18, 62 + ((i - 1) / 3) * 18) {
            @Override public int getMaxStackSize() { return BlueprintWorkbenchBlockEntity.MATERIAL_STACK_LIMIT; }
            @Override public int getMaxStackSize(ItemStack stack) { return BlueprintWorkbenchBlockEntity.materialStackLimit(stack); }
            @Override public Optional<ItemStack> tryRemove(int amount, int maxAmount, Player player) {
                // Large stacks stay in the bench; cursor and player slots use vanilla limits.
                return super.tryRemove(Math.min(amount, getItem().getMaxStackSize()), maxAmount, player);
            }
        });
        for (int i = 0; i < 9; i++) addSlot(new Slot(inventory, BlueprintWorkbenchBlockEntity.OUTPUT_SLOT + i, 104 + (i % 3) * 18, 62 + (i / 3) * 18) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public int getMaxStackSize(ItemStack stack) { return stack.getMaxStackSize(); }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 161 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(playerInventory, col, 8 + col * 18, 220));
    }
    @Override public boolean stillValid(Player player) { return inventory.stillValid(player); }
    @Override public void clicked(int index, int button, ContainerInput input, Player player) {
        if (index >= 1 && index < 10) {
            Slot slot = slots.get(index); ItemStack stack = slot.getItem();
            if (stack.getCount() > stack.getMaxStackSize()) {
                if (input == ContainerInput.SWAP && (button >= 0 && button < 9 || button == 40)) {
                    ItemStack hotbar = player.getInventory().getItem(button);
                    if (hotbar.isEmpty()) player.getInventory().setItem(button, slot.safeTake(stack.getMaxStackSize(), stack.getMaxStackSize(), player));
                    else if (ItemStack.isSameItemSameComponents(stack, hotbar)) slot.safeInsert(hotbar);
                    return;
                }
                // A vanilla swap would put the entire large stack on the cursor.
                if (input == ContainerInput.PICKUP && !getCarried().isEmpty() && !ItemStack.isSameItemSameComponents(stack, getCarried())) return;
            }
        }
        super.clicked(index, button, input, player);
    }
    @Override protected boolean moveItemStackTo(ItemStack stack, int start, int end, boolean backwards) {
        int before = stack.getCount(), step = backwards ? -1 : 1, first = backwards ? end - 1 : start;
        // Merge first, including materials such as fluid buckets with a vanilla limit of one.
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (int index = first; index >= start && index < end && !stack.isEmpty(); index += step) {
                Slot target = slots.get(index);
                if (target.hasItem() != (pass == 0) || !target.mayPlace(stack)) continue;
                target.safeInsert(stack);
            }
        }
        return stack.getCount() != before;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), copy = stack.copy();
        if (index < PLAYER_START) { if (!moveItemStackTo(stack, PLAYER_START, END_PLAYER_SLOTS, true)) return ItemStack.EMPTY; }
        else if (BlueprintItem.isBlueprint(stack)) { if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY; }
        else if (!moveItemStackTo(stack, 1, 10, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack); return copy;
    }
}
