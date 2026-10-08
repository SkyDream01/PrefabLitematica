// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.client.screen;

import com.google.gson.*;
import dev.tensin.prefablitematica.blueprint.BlueprintSerializer;
import dev.tensin.prefablitematica.integration.litematica.LitematicaIntegration;
import dev.tensin.prefablitematica.network.*;
import dev.tensin.prefablitematica.screen.BlueprintWorkbenchScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import dev.tensin.prefablitematica.item.BlueprintItem;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class BlueprintWorkbenchScreen extends AbstractContainerScreen<BlueprintWorkbenchScreenHandler> {
    private static final Identifier PANEL_TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");
    private static final Identifier CHARGE_BACKGROUND = Identifier.withDefaultNamespace("container/villager/experience_bar_background");
    private static final Identifier CHARGE_PROGRESS = Identifier.withDefaultNamespace("container/villager/experience_bar_current");
    private static final int TEXT_COLOR = 0xFF404040, SECONDARY_COLOR = 0xFF5E5E5E, COMPLETE_COLOR = 0xFF777777;
    private static final int CHARGE_COLOR = 0xFF3F621D;
    private JsonObject status = new JsonObject();
    private String message = "";
    private LitematicaIntegration.Capture capture;
    private CompletableFuture<byte[]> encoded;
    private byte[] upload;
    private UUID transfer;
    private int page, pages = 1, offset;
    private Button importButton, chargeButton, previousButton, nextButton;
    private boolean catalogOpen, awaitingImport;
    private int catalogPage;
    private final List<LitematicaIntegration.Source> sources = new ArrayList<>();
    private final List<Button> sourceButtons = new ArrayList<>();
    private List<LitematicaIntegration.Source> filteredSources = List.of();
    private EditBox sourceSearch;
    private CompletableFuture<List<LitematicaIntegration.Source>> scannedFiles;
    public BlueprintWorkbenchScreen(BlueprintWorkbenchScreenHandler menu, Inventory inventory, Component title) { super(menu, inventory, title, 320, 240); inventoryLabelY = 148; inventoryLabelX = 8; }
    private void request(int operation, int sequence, byte[] bytes) { ClientPlayNetworking.send(new BlueprintPayload(operation, menu.containerId, transfer == null ? BlueprintPayload.EMPTY : transfer, sequence, bytes)); }
    @Override protected void init() {
        super.init(); titleLabelX = 8;
        importButton = addRenderableWidget(Button.builder(Component.translatable("gui.prefablitematica.import"), button -> { if (catalogOpen) closeCatalog(); else openCatalog(); }).bounds(leftPos + 8, topPos + 118, 87, 20).build());
        importButton.active = LitematicaIntegration.available();
        chargeButton = addRenderableWidget(Button.builder(Component.translatable("gui.prefablitematica.charge"), button -> request(BlueprintNetworking.CHARGE, 0, new byte[0])).bounds(leftPos + 98, topPos + 118, 72, 20).build());
        previousButton = addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            if (catalogOpen) { catalogPage = Math.max(0, catalogPage - 1); refreshSourceButtons(); }
            else { page = Math.max(0, page - 1); request(BlueprintNetworking.STATUS, page, new byte[0]); }
        }).bounds(leftPos + 253, topPos + 218, 25, 20).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            if (catalogOpen) { catalogPage = Math.min(catalogPages() - 1, catalogPage + 1); refreshSourceButtons(); }
            else { page = Math.min(pages - 1, page + 1); request(BlueprintNetworking.STATUS, page, new byte[0]); }
        }).bounds(leftPos + 282, topPos + 218, 25, 20).build());
        if (catalogOpen) buildSourceSearch();
        request(BlueprintNetworking.STATUS, page, new byte[0]);
    }
    private void openCatalog() {
        if (!BlueprintItem.isBlank(menu.inventory.getItem(0))) { message = Component.translatable("gui.prefablitematica.need_blank").getString(); return; }
        try {
            sources.clear(); sources.addAll(LitematicaIntegration.memorySources()); catalogPage = 0; catalogOpen = true;
            var directory = LitematicaIntegration.schematicDirectory();
            scannedFiles = CompletableFuture.supplyAsync(() -> { try { return LitematicaIntegration.fileSources(directory); } catch (Exception e) { throw new java.util.concurrent.CompletionException(e); } });
            buildSourceSearch(); importButton.setMessage(Component.translatable("gui.prefablitematica.cancel")); message = Component.translatable("gui.prefablitematica.scanning").getString();
        } catch (Exception e) { message = unwrap(e); }
    }
    private void buildSourceSearch() {
        sourceSearch = addRenderableWidget(new EditBox(font, leftPos + 190, topPos + 42, 116, 16, Component.translatable("gui.prefablitematica.search")));
        sourceSearch.setHint(Component.translatable("gui.prefablitematica.search"));
        sourceSearch.setMaxLength(80); sourceSearch.setResponder(text -> { catalogPage = 0; refreshSourceButtons(); }); refreshSourceButtons();
    }
    private int catalogPages() { return Math.max(1, (filteredSources.size() + 4) / 5); }
    private void refreshSourceButtons() {
        sourceButtons.forEach(this::removeWidget); sourceButtons.clear();
        if (!catalogOpen || sourceSearch == null) return;
        String query = sourceSearch.getValue().strip().toLowerCase(Locale.ROOT);
        filteredSources = sources.stream().filter(source -> (source.name() + " " + source.detail()).toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(LitematicaIntegration.Source::kind).thenComparing(LitematicaIntegration.Source::name, String.CASE_INSENSITIVE_ORDER)).toList();
        catalogPage = Math.min(catalogPage, catalogPages() - 1);
        for (int row = 0; row < 5; row++) {
            int index = catalogPage * 5 + row; if (index >= filteredSources.size()) break;
            var source = filteredSources.get(index);
            var tooltip = Component.literal(source.name()).append("\n").append(Component.translatable(source.translationKey())).append("\n").append(source.detail());
            sourceButtons.add(addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(source.name(), 104)), button -> startImport(source))
                    .bounds(leftPos + 190, topPos + 65 + row * 27, 116, 23).tooltip(Tooltip.create(tooltip)).build()));
        }
    }
    private void closeCatalog() {
        catalogOpen = false; sourceButtons.forEach(this::removeWidget); sourceButtons.clear();
        if (sourceSearch != null) { sourceSearch.setFocused(false); removeWidget(sourceSearch); sourceSearch = null; }
        importButton.setMessage(Component.translatable("gui.prefablitematica.import")); scannedFiles = null;
    }
    private void startImport(LitematicaIntegration.Source source) {
        try { capture = new LitematicaIntegration.Capture(source); closeCatalog(); awaitingImport = true; importButton.active = false; message = Component.translatable("gui.prefablitematica.reading").getString(); }
        catch (Exception e) { message = unwrap(e); }
    }
    @Override protected void containerTick() {
        super.containerTick();
        previousButton.active = (catalogOpen ? catalogPage : page) > 0;
        nextButton.active = (catalogOpen ? catalogPage + 1 < catalogPages() : page + 1 < pages);
        try {
            if (catalogOpen && scannedFiles != null && scannedFiles.isDone()) {
                sources.addAll(scannedFiles.join()); scannedFiles = null; refreshSourceButtons();
                message = Component.translatable(sources.isEmpty() ? "gui.prefablitematica.no_sources" : "gui.prefablitematica.choose_source").getString();
            }
            if (capture != null && capture.tick(4096)) {
                var data = capture.finish(); capture = null; message = Component.translatable("gui.prefablitematica.compressing").getString();
                encoded = CompletableFuture.supplyAsync(() -> { try { return BlueprintSerializer.encode(data); } catch (Exception e) { throw new java.util.concurrent.CompletionException(e); } });
            }
            if (encoded != null && encoded.isDone()) {
                upload = encoded.join(); encoded = null; offset = 0; transfer = UUID.randomUUID();
                if (upload.length > 33554432) throw new IllegalArgumentException("Compressed projection exceeds 32 MiB");
                request(BlueprintNetworking.BEGIN, 0, ByteBuffer.allocate(4).putInt(upload.length).array()); message = Component.translatable("gui.prefablitematica.uploading").getString();
            }
        } catch (Exception e) { message = unwrap(e); capture = null; encoded = null; upload = null; scannedFiles = null; awaitingImport = false; importButton.active = LitematicaIntegration.available(); }
    }
    public void receive(BlueprintPayload packet) {
        if (packet.operation() == BlueprintNetworking.ACK && transfer != null && transfer.equals(packet.transferId()) && upload != null) {
            if (offset == upload.length) { request(BlueprintNetworking.END, packet.sequence(), new byte[0]); upload = null; message = Component.translatable("gui.prefablitematica.validating").getString(); }
            else {
                int next = Math.min(upload.length, offset + BlueprintPayload.MAX_CHUNK); byte[] bytes = Arrays.copyOfRange(upload, offset, next); offset = next;
                request(BlueprintNetworking.CHUNK, packet.sequence(), bytes);
            }
        } else if (packet.operation() == BlueprintNetworking.ERROR) {
            message = new String(packet.data(), StandardCharsets.UTF_8); upload = null; awaitingImport = false; importButton.active = LitematicaIntegration.available();
        } else if (packet.operation() == BlueprintNetworking.STATUS) {
            status = JsonParser.parseString(new String(packet.data(), StandardCharsets.UTF_8)).getAsJsonObject();
            if (status.has("page")) { page = status.get("page").getAsInt(); pages = status.get("pages").getAsInt(); }
            if (status.has("message") && !status.get("message").getAsString().isEmpty()) {
                String serverMessage = status.get("message").getAsString();
                message = switch (serverMessage) {
                    case "Imported" -> Component.translatable("gui.prefablitematica.imported").getString();
                    case "Validating materials…", "Validating structure…" -> Component.translatable("gui.prefablitematica.validating").getString();
                    default -> serverMessage;
                };
            }
            if (status.has("name")) awaitingImport = false;
            importButton.active = catalogOpen || (!awaitingImport && capture == null && encoded == null && upload == null && LitematicaIntegration.available() && !status.has("name"));
        }
    }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        extractPanel(g);
        // Vanilla inset edges: dark at the top/left, white at the bottom/right.
        g.fill(leftPos + 184, topPos + 24, leftPos + 312, topPos + 216, 0xFF8B8B8B);
        g.fill(leftPos + 185, topPos + 25, leftPos + 312, topPos + 216, 0xFFFFFFFF);
        g.fill(leftPos + 185, topPos + 25, leftPos + 311, topPos + 215, 0xFFB3B3B3);
        for (var slot : menu.slots) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, leftPos + slot.x - 1, topPos + slot.y - 1, 18, 18);
        }
    }
    private void extractPanel(GuiGraphicsExtractor g) {
        // Resize only the blank interior and edges; keep the vanilla seven-pixel corners.
        // Referencing Minecraft's textures also lets resource packs style the container.
        int[] sourceX = {0, 7, 169}, sourceY = {0, 7, 215};
        int[] widths = {7, imageWidth - 14, 7}, heights = {7, imageHeight - 14, 7};
        int y = topPos;
        for (int row = 0; row < 3; row++) {
            int x = leftPos;
            for (int col = 0; col < 3; col++) {
                g.blit(RenderPipelines.GUI_TEXTURED, PANEL_TEXTURE, x, y, sourceX[col], sourceY[row],
                        widths[col], heights[row], col == 1 ? 1 : 7, row == 1 ? 1 : 7, 256, 256);
                x += widths[col];
            }
            y += heights[row];
        }
    }
    @Override protected void extractSlot(GuiGraphicsExtractor g, Slot slot, int mouseX, int mouseY) {
        var stack = slot.getItem();
        if (slot.container != menu.inventory || slot.getContainerSlot() < 1 || slot.getContainerSlot() > 9 || stack.getCount() < 100) {
            super.extractSlot(g, slot, mouseX, mouseY); return;
        }
        g.item(stack, slot.x, slot.y, slot.x + slot.y * imageWidth);
        g.itemDecorations(font, stack, slot.x, slot.y, "");
        String count = Integer.toString(stack.getCount()); float scale = Math.min(1, 16f / font.width(count));
        g.pose().pushMatrix(); g.pose().translate(slot.x + 17, slot.y + 17 - font.lineHeight * scale); g.pose().scale(scale, scale);
        g.text(font, count, -font.width(count), 0, 0xFFFFFFFF, true); g.pose().popMatrix();
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 7, TEXT_COLOR, false);
        g.text(font, Component.translatable("gui.prefablitematica.blueprint_slot"), 31, 33, TEXT_COLOR, false);
        g.text(font, Component.translatable("gui.prefablitematica.material_slot"), 8, 50, TEXT_COLOR, false);
        g.text(font, Component.translatable("gui.prefablitematica.return_slot"), 104, 50, TEXT_COLOR, false);
        g.text(font, playerInventoryTitle, 8, 148, TEXT_COLOR, false);
        if (catalogOpen) {
            g.text(font, Component.translatable("gui.prefablitematica.choose_source"), 190, 28, TEXT_COLOR, false);
            if (filteredSources.isEmpty() && scannedFiles == null) g.textWithWordWrap(font, Component.translatable("gui.prefablitematica.no_sources"), 190, 70, 114, SECONDARY_COLOR);
            g.text(font, (catalogPage + 1) + "/" + catalogPages(), 190, 224, TEXT_COLOR, false);
        } else if (status.has("name")) {
            g.text(font, font.plainSubstrByWidth(status.get("name").getAsString(), 114), 190, 28, TEXT_COLOR, false);
            g.text(font, font.plainSubstrByWidth(status.get("size").getAsString() + " · " + status.get("blocks").getAsInt(), 114), 190, 40, SECONDARY_COLOR, false);
            int i = 0;
            for (JsonElement value : status.getAsJsonArray("materials")) {
                JsonObject row = value.getAsJsonObject(); var id = Identifier.tryParse(row.get("item").getAsString());
                var item = id == null ? net.minecraft.world.item.ItemStack.EMPTY : new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.getValue(id));
                String label = item.isEmpty() ? "?" : item.getHoverName().getString();
                String group = Component.translatable("material.prefablitematica." + row.get("group").getAsString()).getString();
                boolean needed = row.get("remaining").getAsInt() > 0;
                g.item(item, 190, 55 + i * 28);
                g.text(font, font.plainSubstrByWidth(label, 94), 210, 55 + i * 28, needed ? TEXT_COLOR : COMPLETE_COLOR, false);
                g.text(font, font.plainSubstrByWidth(group, 94), 210, 64 + i * 28, needed ? SECONDARY_COLOR : COMPLETE_COLOR, false);
                g.text(font, font.plainSubstrByWidth(row.get("supplied").getAsInt() + "/" + row.get("required").getAsInt() + " · " + row.get("remaining").getAsInt(), 114), 190, 73 + i * 28, needed ? CHARGE_COLOR : COMPLETE_COLOR, false); i++;
            }
            double charge = Math.clamp(status.get("charge").getAsDouble(), 0, 1);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, CHARGE_BACKGROUND, 8, 141, 98, 5);
            int progress = (int) (98 * charge);
            if (progress > 0) g.blitSprite(RenderPipelines.GUI_TEXTURED, CHARGE_PROGRESS, 98, 5, 0, 0, 8, 141, progress, 5);
            g.text(font, String.format(java.util.Locale.ROOT, "%.1f%%", charge * 100), 118, 141, CHARGE_COLOR, false);
            g.text(font, (page + 1) + "/" + pages, 190, 224, TEXT_COLOR, false);
        } else { int y = 31; for (var line : font.split(Component.translatable("gui.prefablitematica.empty"), 114)) { g.text(font, line, 190, y, SECONDARY_COLOR, false); y += 11; } }
        g.text(font, font.plainSubstrByWidth(message, 114), 190, 204, TEXT_COLOR, false);
    }
    @Override protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        int x = mouseX - leftPos, y = mouseY - topPos;
        if (x >= 8 && x < 72 && y >= 49 && y < 60) g.setTooltipForNextFrame(font, Component.translatable("gui.prefablitematica.material_input_help"), mouseX, mouseY);
        else if (!catalogOpen && x >= 184 && x < 312 && y >= 55 && y < 195 && status.has("materials")) {
            int rowIndex = (y - 55) / 28; var rows = status.getAsJsonArray("materials");
            if (rowIndex < rows.size()) {
                var row = rows.get(rowIndex).getAsJsonObject(); var id = Identifier.tryParse(row.get("item").getAsString());
                var label = id == null ? Component.literal("?") : new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.getValue(id)).getHoverName();
                g.setComponentTooltipForNextFrame(font, java.util.List.of(label, Component.translatable("material.prefablitematica." + row.get("group").getAsString()),
                        Component.literal(row.get("supplied").getAsInt() + "/" + row.get("required").getAsInt()),
                        Component.literal(Component.translatable("gui.prefablitematica.remaining").getString() + ": " + row.get("remaining").getAsInt())), mouseX, mouseY);
            }
        } else if (x >= 184 && x < 312 && y >= 203 && y < 219 && !message.isEmpty()) g.setTooltipForNextFrame(font, Component.literal(message), mouseX, mouseY);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (catalogOpen && event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) { closeCatalog(); return true; }
        return super.keyPressed(event);
    }
    private static String unwrap(Throwable error) { while (error.getCause() != null) error = error.getCause(); return error.getMessage() == null ? "Import failed" : error.getMessage(); }
}
