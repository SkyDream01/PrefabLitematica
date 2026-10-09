// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.network;

import com.google.gson.*;
import dev.tensin.prefablitematica.PrefabLitematicaMod;
import dev.tensin.prefablitematica.blueprint.*;
import dev.tensin.prefablitematica.item.BlueprintItem;
import dev.tensin.prefablitematica.material.MaterialConversionRegistry;
import dev.tensin.prefablitematica.screen.BlueprintWorkbenchScreenHandler;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class BlueprintNetworking {
    public static final int STATUS = 0, CHARGE = 1, BEGIN = 2, CHUNK = 3, END = 4, ACK = 5, ERROR = 6;
    public static final int PAGE_SIZE = 5;
    private static final Map<UUID, Upload> uploads = new HashMap<>();
    private static final Map<UUID, Long> cooldown = new HashMap<>();
    private static final Map<UUID, Long> chargeCooldown = new HashMap<>();
    private static final Map<UUID, Long> statusCooldown = new HashMap<>();
    private static final List<ImportJob> jobs = new ArrayList<>();
    private static ExecutorService decoder;
    private static final class Upload {
        final UUID transfer;
        final int sync, total;
        final UUID original;
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int sequence;
        long touched = System.currentTimeMillis();
        Upload(BlueprintPayload packet, int total, UUID original) { transfer = packet.transferId(); sync = packet.syncId(); this.total = total; this.original = original; }
    }
    private record ImportJob(ServerPlayer player, int syncId, BlueprintManager.Analysis analysis, boolean refresh) {}
    private BlueprintNetworking() {}
    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(BlueprintPreviewPayload.Request.TYPE, BlueprintPreviewPayload.Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BlueprintPreviewPayload.Response.TYPE, BlueprintPreviewPayload.Response.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(BlueprintPreviewPayload.Request.TYPE, (packet, context) -> PrefabLitematicaMod.previews(context.server()).handle(context.player(), packet));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PrefabLitematicaMod.previews(server).disconnect(handler.player));
        PayloadTypeRegistry.serverboundPlay().register(BlueprintPayload.C2S, BlueprintPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BlueprintPayload.Response.TYPE, BlueprintPayload.Response.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(BlueprintPayload.C2S, (packet, context) -> handle(context.player(), packet));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> { uploads.remove(handler.player.getUUID()); cooldown.remove(handler.player.getUUID()); chargeCooldown.remove(handler.player.getUUID()); statusCooldown.remove(handler.player.getUUID()); });
    }
    private static BlueprintWorkbenchScreenHandler menu(ServerPlayer player, int sync) {
        if (!(player.containerMenu instanceof BlueprintWorkbenchScreenHandler menu) || menu.containerId != sync || menu.bench == null || !menu.stillValid(player))
            throw new IllegalArgumentException("Open a nearby blueprint workbench");
        return menu;
    }
    private static void handle(ServerPlayer player, BlueprintPayload packet) {
        try {
            var menu = menu(player, packet.syncId());
            switch (packet.operation()) {
                case STATUS -> {
                    long now = System.currentTimeMillis();
                    if (now - statusCooldown.getOrDefault(player.getUUID(), 0L) < 100) return;
                    statusCooldown.put(player.getUUID(), now); menu.page = Math.min(10000, packet.sequence()); sendStatus(player, menu, "");
                }
                case CHARGE -> { rateLimit(player, 500, chargeCooldown); menu.bench.charge(player); menu.page = 0; sendStatus(player, menu, ""); }
                case BEGIN -> {
                    rateLimit(player, 3000);
                    if (!PrefabLitematicaMod.CONFIG.allowSurvivalImports && !player.isCreative()) throw new IllegalArgumentException("Only creative imports are enabled");
                    if (uploads.size() + jobs.size() >= 2 || uploads.containsKey(player.getUUID())) throw new IllegalArgumentException("Import server is busy");
                    var old = menu.bench.blueprint();
                    if (!BlueprintItem.isBlank(menu.bench.getItem(0)) && (old == null || !old.requiresReimport || old.locked)) throw new IllegalArgumentException("Insert an unused blueprint first");
                    if (packet.data().length != 4) throw new IllegalArgumentException("Invalid upload header");
                    int total = java.nio.ByteBuffer.wrap(packet.data()).getInt();
                    if (total < 1 || total > PrefabLitematicaMod.CONFIG.maxUploadBytes) throw new IllegalArgumentException("Upload exceeds size limit");
                    uploads.put(player.getUUID(), new Upload(packet, total, old != null && old.requiresReimport ? old.id : null)); ack(player, packet, 0);
                }
                case CHUNK -> {
                    Upload upload = uploads.get(player.getUUID());
                    if (upload == null || upload.sync != packet.syncId() || !upload.transfer.equals(packet.transferId()) || packet.sequence() != upload.sequence || packet.data().length == 0)
                        throw new IllegalArgumentException("Out of order upload chunk");
                    if (upload.bytes.size() > upload.total - packet.data().length) throw new IllegalArgumentException("Too many upload bytes");
                    upload.bytes.write(packet.data()); upload.sequence++; upload.touched = System.currentTimeMillis(); ack(player, packet, upload.sequence);
                }
                case END -> {
                    Upload upload = uploads.get(player.getUUID());
                    if (upload == null || !upload.transfer.equals(packet.transferId()) || upload.sequence != packet.sequence() || upload.bytes.size() != upload.total)
                        throw new IllegalArgumentException("Incomplete upload");
                    uploads.remove(player.getUUID());
                    if (decoder == null || decoder.isShutdown()) decoder = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("blueprint-decoder").factory());
                    // One bounded worker decodes structure; world and inventories remain on the server thread.
                    byte[] bytes = upload.bytes.toByteArray(); MinecraftServer server = player.level().getServer();
                    jobs.add(new ImportJob(player, packet.syncId(), null, false));
                    decoder.submit(() -> {
                        try {
                            BlueprintData data = BlueprintSerializer.decode(bytes, upload.original == null ? UUID.randomUUID() : upload.original, PrefabLitematicaMod.CONFIG);
                            if (data.requiresReimport) throw new IOException("Update the client and reimport the original schematic to restore missing comparator data");
                            server.execute(() -> {
                                jobs.removeIf(job -> job.player == player && job.analysis == null);
                                try { menu(player, packet.syncId()); jobs.add(new ImportJob(player, packet.syncId(), PrefabLitematicaMod.manager(server).new Analysis(data), upload.original != null)); sendStatus(player, menu, "Validating materials…"); }
                                catch (Exception e) { error(player, packet.syncId(), e.getMessage()); }
                            });
                        } catch (Exception e) { server.execute(() -> { jobs.removeIf(job -> job.player == player && job.analysis == null); error(player, packet.syncId(), e.getMessage()); }); }
                    });
                    sendStatus(player, menu, "Validating structure…");
                }
                default -> throw new IllegalArgumentException("Unknown request");
            }
        } catch (Exception e) { uploads.remove(player.getUUID()); error(player, packet.syncId(), e.getMessage()); }
    }
    private static void rateLimit(ServerPlayer player, long delay) {
        rateLimit(player, delay, cooldown);
    }
    private static void rateLimit(ServerPlayer player, long delay, Map<UUID, Long> times) {
        long now = System.currentTimeMillis(), previous = times.getOrDefault(player.getUUID(), 0L);
        if (now - previous < delay) throw new IllegalArgumentException("Please wait before retrying"); times.put(player.getUUID(), now);
    }
    private static void ack(ServerPlayer player, BlueprintPayload request, int next) {
        ServerPlayNetworking.send(player, new BlueprintPayload.Response(new BlueprintPayload(ACK, request.syncId(), request.transferId(), next, new byte[0])));
    }
    public static void tick(MinecraftServer server) {
        uploads.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue().touched > 60000);
        var iterator = jobs.iterator();
        while (iterator.hasNext()) {
            ImportJob job = iterator.next(); if (job.analysis == null) continue;
            try {
                var menu = menu(job.player, job.syncId);
                var old = menu.bench.blueprint();
                if (job.refresh ? old == null || !old.requiresReimport || old.locked || !old.id.equals(job.analysis.data.id)
                        : !BlueprintItem.isBlank(menu.bench.getItem(0))) throw new IllegalArgumentException("Blueprint slot changed during import");
                if (job.analysis.tick(PrefabLitematicaMod.CONFIG.blocksPlacedPerTick)) {
                    if (job.refresh) BlueprintManager.refreshLegacy(old, job.analysis.data);
                    PrefabLitematicaMod.manager(server).create(job.analysis.data); menu.bench.setItem(0, BlueprintItem.loaded(job.analysis.data));
                    sendStatus(job.player, menu, "Imported"); iterator.remove();
                }
            } catch (Exception e) { error(job.player, job.syncId, e.getMessage()); iterator.remove(); }
        }
        if (server.getTickCount() % 20 == 0) for (var player : server.getPlayerList().getPlayers())
            if (player.containerMenu instanceof BlueprintWorkbenchScreenHandler menu && menu.stillValid(player)) sendStatus(player, menu, "");
    }
    public static void sendStatus(ServerPlayer player, BlueprintWorkbenchScreenHandler menu, String message) {
        JsonObject result = new JsonObject(); result.addProperty("message", message);
        BlueprintData data = menu.bench.blueprint();
        if (data != null) {
            result.addProperty("name", data.name); result.addProperty("size", data.sizeX + " × " + data.sizeY + " × " + data.sizeZ);
            result.addProperty("blocks", data.blocks.size()); result.addProperty("charge", data.charge()); result.addProperty("locked", data.locked); result.addProperty("requiresReimport", data.requiresReimport);
            result.addProperty("portalNeedsIgnition", data.portalNeedsIgnition);
            result.addProperty("portalNeedsSlicing", data.portalNeedsSlicing);
            var rows = data.requirementsForInput();
            int pages = Math.max(1, (rows.size() + PAGE_SIZE - 1) / PAGE_SIZE); menu.page = Math.min(menu.page, pages - 1);
            result.addProperty("page", menu.page); result.addProperty("pages", pages); JsonArray materials = new JsonArray();
            for (int i = menu.page * PAGE_SIZE; i < Math.min(rows.size(), menu.page * PAGE_SIZE + PAGE_SIZE); i++) {
                var r = rows.get(i); JsonObject row = new JsonObject(); row.addProperty("item", r.representativeItem); row.addProperty("group", r.group);
                JsonArray conversions = new JsonArray();
                MaterialConversionRegistry.hints(r, PrefabLitematicaMod.manager(player.level().getServer()).equivalence).forEach(conversions::add);
                row.add("conversions", conversions);
                row.addProperty("required", r.required); row.addProperty("supplied", r.supplied); row.addProperty("remaining", r.remaining()); materials.add(row);
            }
            result.add("materials", materials);
        }
        byte[] bytes = result.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= BlueprintPayload.MAX_CHUNK) ServerPlayNetworking.send(player, new BlueprintPayload.Response(new BlueprintPayload(STATUS, menu.containerId, BlueprintPayload.EMPTY, 0, bytes)));
    }
    private static void error(ServerPlayer player, int sync, String message) {
        String safe = message == null ? "Blueprint operation failed" : message.substring(0, Math.min(message.length(), 500));
        ServerPlayNetworking.send(player, new BlueprintPayload.Response(new BlueprintPayload(ERROR, sync, BlueprintPayload.EMPTY, 0, safe.getBytes(StandardCharsets.UTF_8))));
    }
    public static void stop() { if (decoder != null) decoder.shutdownNow(); uploads.clear(); jobs.clear(); cooldown.clear(); chargeCooldown.clear(); statusCooldown.clear(); }
}
