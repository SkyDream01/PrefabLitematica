// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.client.screen;

import dev.tensin.prefablitematica.client.BlueprintProjectionClient;
import dev.tensin.prefablitematica.network.BlueprintPreviewPayload;
import dev.tensin.prefablitematica.placement.BlueprintRotation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Blueprint data, pose, explicit preview and execution are controlled only from this panel. */
public final class BlueprintProjectionScreen extends Screen {
    private final BlueprintProjectionClient preview = BlueprintProjectionClient.INSTANCE;
    private final EditBox[] axes = new EditBox[3];
    private Button execute, show, previousRotation, nextRotation;
    private int left, top, turns, draftAge;
    public BlueprintProjectionScreen() { super(Component.translatable("projection.prefablitematica.title")); }
    @Override protected void init() {
        left = 10; top = Math.max(2, (height - 236) / 2); turns = preview.rotation().ordinal();
        for (int axis = 0; axis < 3; axis++) {
            int index = axis, y = top + 50 + axis * 22;
            axes[axis] = addRenderableWidget(new EditBox(font, left + 42, y, 100, 18, Component.literal("XYZ".substring(axis, axis + 1))));
            axes[axis].setMaxLength(11);
            axes[axis].setValue(Integer.toString(coordinate(preview.origin(), axis)));
            axes[axis].setResponder(value -> { draftAge = 0; updateButtons(); });
            addRenderableWidget(Button.builder(Component.literal("−"), button -> nudge(index, -1)).bounds(left + 146, y, 22, 18).build());
            addRenderableWidget(Button.builder(Component.literal("+"), button -> nudge(index, 1)).bounds(left + 172, y, 22, 18).build());
        }
        previousRotation = addRenderableWidget(Button.builder(Component.literal("−90°"), button -> rotate(-1)).bounds(left + 50, top + 116, 42, 20).build());
        nextRotation = addRenderableWidget(Button.builder(Component.literal("+90°"), button -> rotate(1)).bounds(left + 152, top + 116, 42, 20).build());
        show = addRenderableWidget(Button.builder(Component.translatable("projection.prefablitematica.show"), button -> {
            if (applyDraft()) preview.show(); updateButtons();
        }).bounds(left + 12, top + 141, 182, 20).build());
        execute = addRenderableWidget(Button.builder(Component.translatable("projection.prefablitematica.confirm"), button -> {
            if (matchesPreview() && preview.canConfirm()) preview.confirm(); updateButtons();
        }).bounds(left + 12, top + 187, 182, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("projection.prefablitematica.inspect"), button -> onClose()).bounds(left + 12, top + 211, 87, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("projection.prefablitematica.cancel"), button -> preview.cancel()).bounds(left + 105, top + 211, 89, 20).build());
        updateButtons();
    }
    private static int coordinate(BlockPos pos, int axis) { return axis == 0 ? pos.getX() : axis == 1 ? pos.getY() : pos.getZ(); }
    private BlockPos draft() {
        try {
            var pos = new BlockPos(Integer.parseInt(axes[0].getValue()), Integer.parseInt(axes[1].getValue()), Integer.parseInt(axes[2].getValue()));
            return BlueprintPreviewPayload.validOrigin(pos) ? pos : null;
        } catch (NumberFormatException e) { return null; }
    }
    private boolean matchesPreview() { return preview.origin().equals(draft()) && preview.rotation().ordinal() == turns; }
    private boolean applyDraft() {
        var pos = draft(); if (pos == null || preview.busy()) return false;
        preview.move(pos, BlueprintRotation.values()[turns]); return true;
    }
    private void nudge(int axis, int delta) {
        var pos = draft(); if (pos == null || preview.busy()) return;
        int step = minecraft.hasShiftDown() ? 10 : 1;
        axes[axis].setValue(Integer.toString(coordinate(pos, axis) + delta * step)); applyDraft(); updateButtons();
    }
    private void rotate(int delta) {
        if (preview.busy()) return;
        turns = Math.floorMod(turns + delta, 4); applyDraft(); updateButtons();
    }
    private void updateButtons() {
        if (show == null || execute == null) return;
        show.active = draft() != null && preview.canShow();
        execute.visible = preview.shown();
        execute.active = matchesPreview() && preview.canConfirm();
        previousRotation.active = nextRotation.active = !preview.busy();
        for (var axis : axes) axis.setEditable(!preview.busy());
    }
    @Override public void tick() {
        if (!preview.active()) { onClose(); return; }
        // Coalesce typing; only this screen can submit a pose change.
        if (++draftAge >= 4 && !matchesPreview()) applyDraft();
        updateButtons();
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(left, top, left + 206, top + 236, 0xD0182028);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.text(font, title, left + 12, top + 8, 0xFFFFFFFF, false);
        String charge = preview.chargeText(); g.text(font, charge, left + 194 - font.width(charge), top + 8, 0xFF55FFAA, false);
        g.text(font, font.plainSubstrByWidth(preview.name(), 182), left + 12, top + 21, 0xFFBBDDEE, false);
        g.text(font, font.plainSubstrByWidth(preview.info().getString(), 182), left + 12, top + 34, 0xFFBBDDEE, false);
        for (int axis = 0; axis < 3; axis++) g.text(font, "XYZ".substring(axis, axis + 1), left + 18, top + 55 + axis * 22, 0xFFFFFFFF, false);
        g.text(font, Component.translatable("projection.prefablitematica.rotation_label"), left + 12, top + 122, 0xFFFFFFFF, false);
        String degrees = turns * 90 + "°"; g.text(font, degrees, left + 122 - font.width(degrees) / 2, top + 122, 0xFFFFFFFF, false);
        var status = draft() == null ? Component.translatable("projection.prefablitematica.invalid_coordinates")
                : !matchesPreview() ? Component.translatable("projection.prefablitematica.updating") : preview.statusText();
        g.textWithWordWrap(font, status, left + 12, top + 165, 182, preview.statusColor());
        if (!preview.error().isEmpty() && mouseY >= top + 163 && mouseY < top + 185)
            g.setTooltipForNextFrame(font, Component.literal(preview.error()), mouseX, mouseY);
    }
}
