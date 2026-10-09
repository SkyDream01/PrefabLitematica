// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.client;

import dev.tensin.prefablitematica.PrefabLitematicaMod;
import dev.tensin.prefablitematica.blueprint.*;
import dev.tensin.prefablitematica.client.screen.BlueprintProjectionScreen;
import dev.tensin.prefablitematica.config.BlueprintConfig;
import dev.tensin.prefablitematica.integration.litematica.*;
import dev.tensin.prefablitematica.item.BlueprintItem;
import dev.tensin.prefablitematica.network.*;
import dev.tensin.prefablitematica.placement.BlueprintRotation;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static dev.tensin.prefablitematica.network.BlueprintPreviewPayload.*;

public final class BlueprintProjectionClient {
    public static final BlueprintProjectionClient INSTANCE = new BlueprintProjectionClient();
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.parse("prefablitematica:projection"));
    public static final KeyMapping OPEN_UI = key("open_panel", InputConstants.KEY_G);
    private static KeyMapping key(String name, int key) { return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.prefablitematica." + name, key, CATEGORY)); }
    private UUID session, blueprint, opening;
    private int revision, acknowledged, ticks, sizeX, sizeY, sizeZ, blockCount;
    private double charge;
    private BlockPos origin = BlockPos.ZERO;
    private BlueprintRotation rotation = BlueprintRotation.NONE;
    private String name = "", problem = "", error = "";
    private boolean complete, charged, confirming, shown, serverShown, litematicaVisible, adapterFailed;
    private BitSet conflicts = new BitSet();
    private BlueprintData data;
    private Assembly structure, status;
    private CompletableFuture<BlueprintData> decoding;
    private LitematicaProjection projection;
    private Object dimension;
    private static final class Assembly {
        final byte[] bytes;
        int offset;
        Assembly(int total) { bytes = new byte[total]; }
        boolean append(Response packet) {
            if (packet.offset() != offset || packet.total() != bytes.length) throw new IllegalArgumentException("Out of order projection data");
            System.arraycopy(packet.data(), 0, bytes, offset, packet.data().length); offset += packet.data().length;
            return offset == bytes.length;
        }
    }
    private BlueprintProjectionClient() {}
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(Response.TYPE, (packet, context) -> INSTANCE.receive(packet));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> INSTANCE.clear());
        ClientTickEvents.END_CLIENT_TICK.register(INSTANCE::tick);
        BlueprintProjectionRenderer.register();
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.parse("prefablitematica:projection"), (g, delta) -> {
            var preview = INSTANCE; var client = Minecraft.getInstance();
            if (!preview.active() || !preview.shown || client.gui.screen() != null) return;
            g.fill(6, 6, Math.min(g.guiWidth() - 6, 400), 49, 0xA0000000);
            g.text(client.font, Component.translatable("projection.prefablitematica.position", preview.origin.getX(), preview.origin.getY(), preview.origin.getZ(), preview.rotation.ordinal() * 90), 10, 10, 0xFFFFFFFF, true);
            g.text(client.font, preview.statusText(), 10, 23, preview.statusColor(), true);
            g.text(client.font, Component.translatable("projection.prefablitematica.keys", OPEN_UI.getTranslatedKeyMessage()), 10, 36, 0xFFE0E0E0, true);
        });
    }
    public void receive(Response packet) {
        try {
            if (packet.kind() == ERROR && opening != null && opening.equals(packet.session())) {
                opening = null;
                var player = Minecraft.getInstance().player;
                if (player != null) player.sendSystemMessage(Component.translatable("projection.prefablitematica.open_failed"));
                return;
            }
            if (packet.kind() == OPEN) {
                clear(); session = packet.session(); origin = packet.origin(); rotation = BlueprintRotation.values()[packet.rotation()]; revision = acknowledged = packet.revision();
                var in = new DataInputStream(new ByteArrayInputStream(packet.data()));
                blueprint = new UUID(in.readLong(), in.readLong()); name = in.readUTF(); sizeX = in.readInt(); sizeY = in.readInt(); sizeZ = in.readInt();
                blockCount = in.readInt(); charge = in.readDouble(); charged = charge >= 1;
                if (sizeX < 1 || sizeY < 1 || sizeZ < 1 || sizeX > 2048 || sizeY > 2048 || sizeZ > 2048 || (long) sizeX * sizeY * sizeZ > 500000) throw new IOException("Invalid projection dimensions");
                var client = Minecraft.getInstance(); dimension = client.level == null ? null : client.level.dimension();
                client.gui.setScreen(new BlueprintProjectionScreen());
                return;
            }
            if (!active() || !session.equals(packet.session())) return;
            if (packet.kind() == CLOSED) { clear(); return; }
            if (packet.kind() == STRUCTURE) {
                if (packet.offset() == 0) structure = new Assembly(packet.total());
                if (structure == null) throw new IOException("Missing projection header");
                if (structure.append(packet)) {
                    byte[] bytes = structure.bytes; UUID id = blueprint; structure = null;
                    decoding = CompletableFuture.supplyAsync(() -> {
                        try { var config = new BlueprintConfig(); config.maxDimension = 2048; return BlueprintSerializer.decode(bytes, id, config); }
                        catch (IOException e) { throw new java.util.concurrent.CompletionException(e); }
                    });
                }
                return;
            }
            if (packet.kind() == ERROR) {
                error = new String(packet.data(), StandardCharsets.UTF_8); confirming = false; return;
            }
            if (packet.revision() != revision || !packet.origin().equals(origin) || packet.rotation() != rotation.ordinal()) return;
            acknowledged = packet.revision();
            if (packet.kind() == STATUS) {
                if (packet.total() > (sizeX * sizeY * sizeZ + 7) / 8 + 2048) throw new IOException("Projection status exceeds volume");
                if (packet.offset() == 0) status = new Assembly(packet.total());
                if (status == null) throw new IOException("Missing projection status");
                if (status.append(packet)) {
                    var in = new DataInputStream(new ByteArrayInputStream(status.bytes));
                    complete = in.readBoolean(); serverShown = in.readBoolean(); charged = in.readBoolean(); problem = in.readUTF(); conflicts = BitSet.valueOf(in.readAllBytes()); status = null;
                    if (conflicts.length() > sizeX * sizeY * sizeZ) throw new IOException("Projection conflicts exceed volume");
                    if (complete) confirming = false;
                    BlueprintProjectionRenderer.invalidate();
                }
            }
        } catch (Exception e) { fail(e); }
    }
    private void tick(Minecraft client) {
        ticks++;
        boolean open = OPEN_UI.consumeClick();
        if (open && client.gui.screen() == null) openPanel(client);
        if (!active()) return;
        if (client.level == null || client.player == null || !client.level.dimension().equals(dimension) || !client.player.isAlive()) { cancel(); return; }
        try {
            if (decoding != null && decoding.isDone()) {
                data = decoding.join(); decoding = null;
                BlueprintProjectionRenderer.invalidate();
            }
            if (shown && data != null && projection == null && !adapterFailed && LitematicaIntegration.available()) {
                try { projection = new LitematicaProjection(data); }
                catch (Exception e) { adapterFailed = true; PrefabLitematicaMod.LOGGER.warn("Using built-in projection: Litematica adapter failed", e); }
            }
            if (projection != null) {
                try {
                    projection.tick(4096, origin, rotation); litematicaVisible = projection.visible();
                    var pose = projection.pose();
                    if (pose != null && (!pose.origin().equals(origin) || pose.rotation() != rotation)) projection.move(origin, rotation);
                } catch (Exception e) { adapterFailed = true; closeProjection(); PrefabLitematicaMod.LOGGER.warn("Using built-in projection: Litematica update failed", e); }
            }
            // Retransmit a coalesced movement until the server acknowledges this exact pose.
            if (revision != acknowledged && ticks % 4 == 0) request(MOVE);
        } catch (Exception e) { fail(e); }
    }
    private void openPanel(Minecraft client) {
        if (client.player == null || client.level == null) return;
        ItemStack stack = client.player.getMainHandItem();
        if (BlueprintItem.id(stack) == null) stack = client.player.getOffhandItem();
        UUID id = BlueprintItem.id(stack);
        if (id == null) { client.player.sendSystemMessage(Component.translatable("projection.prefablitematica.open_failed")); return; }
        if (active() && id.equals(blueprint)) { client.gui.setScreen(new BlueprintProjectionScreen()); return; }
        if (opening != null || !ClientPlayNetworking.canSend(Request.TYPE)) return;
        HitResult hit = client.player.pick(6, 1, false);
        BlockPos initial = hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                ? block.getBlockPos().relative(block.getDirection()) : client.player.blockPosition().relative(client.player.getDirection(), 3);
        opening = id;
        ClientPlayNetworking.send(new Request(id, 0, START, initial, BlueprintItem.rotation(stack)));
    }
    public void move(BlockPos pos, BlueprintRotation turns) {
        if (!(Minecraft.getInstance().gui.screen() instanceof BlueprintProjectionScreen) || !active() || confirming || !BlueprintPreviewPayload.validOrigin(pos) || pos.equals(origin) && turns == rotation) return;
        origin = pos.immutable(); rotation = turns; revision++; complete = false; confirming = false; conflicts.clear(); status = null; error = "";
        if (projection != null) try { projection.move(origin, rotation); } catch (Exception e) { adapterFailed = true; closeProjection(); }
        BlueprintProjectionRenderer.invalidate(); request(MOVE);
    }
    public void show() {
        if (!(Minecraft.getInstance().gui.screen() instanceof BlueprintProjectionScreen) || !canShow()) return;
        shown = true; serverShown = false; complete = false; error = "";
        BlueprintProjectionRenderer.invalidate(); request(SHOW);
    }
    public void confirm() {
        if (!(Minecraft.getInstance().gui.screen() instanceof BlueprintProjectionScreen)) return;
        if (!canConfirm()) { var player = Minecraft.getInstance().player; if (player != null) player.sendSystemMessage(statusText()); return; }
        error = ""; confirming = true; request(CONFIRM);
    }
    public void cancel() { if (active()) request(CANCEL); clear(); }
    private void request(int action) { if (active() && ClientPlayNetworking.canSend(Request.TYPE)) ClientPlayNetworking.send(new Request(session, revision, action, origin, rotation.ordinal())); }
    private void fail(Exception error) {
        PrefabLitematicaMod.LOGGER.error("Projection failed", error);
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(Component.translatable("projection.prefablitematica.failed"));
        cancel();
    }
    private void closeProjection() {
        if (projection != null) try { projection.close(); } catch (Exception e) { PrefabLitematicaMod.LOGGER.warn("Cannot remove temporary Litematica projection", e); }
        projection = null; litematicaVisible = false;
    }
    public void clear() {
        closeProjection(); if (decoding != null) decoding.cancel(false);
        session = blueprint = opening = null; data = null; decoding = null; structure = status = null; conflicts = new BitSet();
        complete = charged = confirming = shown = serverShown = adapterFailed = false; error = problem = ""; dimension = null;
        BlueprintProjectionRenderer.invalidate();
        var client = Minecraft.getInstance(); if (client.gui.screen() instanceof BlueprintProjectionScreen) client.gui.setScreen(null);
    }
    public boolean active() { return session != null; }
    public boolean holdingBlueprint() {
        var player = Minecraft.getInstance().player;
        return blueprint != null && player != null && (blueprint.equals(BlueprintItem.id(player.getMainHandItem())) || blueprint.equals(BlueprintItem.id(player.getOffhandItem())));
    }
    public boolean canShow() { return active() && data != null && charged && !confirming && holdingBlueprint(); }
    public boolean canConfirm() { return canShow() && shown && serverShown && complete && conflicts.isEmpty() && revision == acknowledged; }
    public Component statusText() {
        if (confirming) return Component.translatable("projection.prefablitematica.confirming");
        if (data == null) return Component.translatable("projection.prefablitematica.loading");
        if (!shown) return Component.translatable("projection.prefablitematica.show_first");
        if (!conflicts.isEmpty()) return Component.translatable("projection.prefablitematica.conflicts", conflicts.cardinality());
        if (!complete || revision != acknowledged) return Component.translatable("projection.prefablitematica.scanning");
        if (!charged) return Component.translatable("projection.prefablitematica.unavailable");
        if (!holdingBlueprint()) return Component.translatable("projection.prefablitematica.hold");
        return Component.translatable("projection.prefablitematica.ready");
    }
    public int statusColor() { return !conflicts.isEmpty() ? 0xFFFF5555 : canConfirm() ? 0xFF55FFAA : 0xFFFFDD77; }
    public BlockPos origin() { return origin; }
    public BlueprintRotation rotation() { return rotation; }
    public BlueprintData data() { return data; }
    public int width() { return rotation.ordinal() % 2 == 0 ? sizeX : sizeZ; }
    public int height() { return sizeY; }
    public int depth() { return rotation.ordinal() % 2 == 0 ? sizeZ : sizeX; }
    public BitSet conflicts() { return (BitSet) conflicts.clone(); }
    public String name() { return name; }
    public Component info() { return Component.translatable("projection.prefablitematica.info", sizeX, sizeY, sizeZ, blockCount); }
    public String chargeText() { return String.format(Locale.ROOT, "%.0f%%", charge * 100); }
    public boolean shown() { return shown; }
    public boolean busy() { return confirming; }
    public String error() { return error.isEmpty() ? problem : error; }
    public boolean litematicaVisible() { return litematicaVisible; }
    public LitematicaProjection projection() { return projection; }
}
