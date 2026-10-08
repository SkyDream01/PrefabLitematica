// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.item;

import dev.tensin.prefablitematica.PrefabLitematicaMod;
import dev.tensin.prefablitematica.blueprint.BlueprintData;
import dev.tensin.prefablitematica.placement.BlueprintRotation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import java.util.UUID;

public final class BlueprintItem extends Item {
    public BlueprintItem(Properties properties) { super(properties); }
    public static boolean isBlueprint(ItemStack stack) { return stack.is(PrefabLitematicaMod.BLUEPRINT) || stack.is(PrefabLitematicaMod.BLANK_BLUEPRINT); }
    public static boolean isBlank(ItemStack stack) {
        return stack.is(PrefabLitematicaMod.BLANK_BLUEPRINT) || (stack.is(PrefabLitematicaMod.BLUEPRINT)
                && !stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().contains("blueprint_id"));
    }
    public static ItemStack loaded(BlueprintData data) { var stack = new ItemStack(PrefabLitematicaMod.BLUEPRINT); bind(stack, data); return stack; }
    public static UUID id(ItemStack stack) {
        if (!stack.is(PrefabLitematicaMod.BLUEPRINT)) return null;
        try { return UUID.fromString(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getStringOr("blueprint_id", "")); }
        catch (IllegalArgumentException e) { return null; }
    }
    public static int rotation(ItemStack stack) { return Math.floorMod(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr("rotation", 0), 4); }
    public static void bind(ItemStack stack, BlueprintData data) {
        CompoundTag tag = new CompoundTag(); tag.putString("blueprint_id", data.id.toString()); tag.putString("name", data.name);
        tag.putInt("rotation", 0); tag.putInt("blocks", data.blocks.size()); tag.putInt("size_x", data.sizeX); tag.putInt("size_y", data.sizeY); tag.putInt("size_z", data.sizeZ);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag)); stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.prefablitematica.loaded_named", data.name));
    }
    @Override public Component getName(ItemStack stack) { return Component.translatable(isBlank(stack) ? "item.prefablitematica.blank_blueprint" : "item.prefablitematica.blueprint"); }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        if (isBlank(stack)) { tooltip.accept(Component.translatable("tooltip.prefablitematica.blank")); return; }
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tooltip.accept(Component.translatable("tooltip.prefablitematica.loaded"));
        if (tag.contains("size_x")) tooltip.accept(Component.translatable("tooltip.prefablitematica.size", tag.getIntOr("size_x", 0), tag.getIntOr("size_y", 0), tag.getIntOr("size_z", 0)));
        tooltip.accept(Component.translatable("tooltip.prefablitematica.blocks", tag.getIntOr("blocks", 0)));
        tooltip.accept(Component.translatable("tooltip.prefablitematica.use"));
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.FAIL;
        ItemStack stack = context.getItemInHand();
        if (player.isShiftKeyDown()) {
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(); int turns = (rotation(stack) + 1) % 4;
            tag.putInt("rotation", turns); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            player.sendSystemMessage(Component.translatable("message.prefablitematica.rotation", turns * 90), true); return InteractionResult.SUCCESS;
        }
        var origin = context.getClickedPos().relative(context.getClickedFace());
        try {
            PrefabLitematicaMod.placements(player.level().getServer()).start(player, stack, origin, BlueprintRotation.values()[rotation(stack)]);
            return InteractionResult.SUCCESS;
        } catch (Exception e) { player.sendSystemMessage(Component.literal(e.getMessage()), false); return InteractionResult.FAIL; }
    }
}
