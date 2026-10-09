// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.placement;

import dev.tensin.prefablitematica.PrefabLitematicaMod;
import dev.tensin.prefablitematica.blueprint.*;
import dev.tensin.prefablitematica.item.BlueprintItem;
import dev.tensin.prefablitematica.network.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static dev.tensin.prefablitematica.network.BlueprintPreviewPayload.*;

/** Projections never reserve terrain, lock a blueprint, or debit charge. */
public final class BlueprintPreviewManager {
    private final MinecraftServer server;
    private final Map<UUID, Session> sessions = new LinkedHashMap<>();
    private final ExecutorService encoder = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("blueprint-preview-encoder").factory());
    public static final class Session {
        public final UUID token = UUID.randomUUID();
        public final ServerPlayer player;
        public final ServerLevel world;
        public final BlueprintData data;
        public BlockPos origin;
        public BlueprintRotation rotation;
        public int revision;
        public BlueprintPreviewScan scan;
        public boolean shown;
        private BlueprintPreviewScan lastComplete;
        private final CompletableFuture<byte[]> encoded;
        private byte[] structure;
        private int sent, nextScanTick;
        private long lastMove;
        private boolean confirming;
        private Session(ServerPlayer player, BlueprintData data, BlockPos origin, BlueprintRotation rotation, Executor executor) {
            this.player = player; world = player.level(); this.data = data; this.origin = origin.immutable(); this.rotation = rotation;
            scan = new BlueprintPreviewScan(player, data, origin, rotation);
            encoded = CompletableFuture.supplyAsync(() -> {
                try {
                    // Projection clients need block states, never saved container or sign data.
                    var blocks = data.blocks.stream().map(b -> new BlueprintBlock(b.relativePos(), b.state(), null)).toList();
                    return BlueprintSerializer.encode(new BlueprintData(data.id, data.name, data.sizeX, data.sizeY, data.sizeZ, blocks, new LinkedHashMap<>()));
                } catch (IOException e) { throw new CompletionException(e); }
            }, executor);
        }
    }
    public BlueprintPreviewManager(MinecraftServer server) { this.server = server; }
    public Session session(ServerPlayer player) { return sessions.get(player.getUUID()); }
    public void begin(ServerPlayer player, ItemStack stack, BlockPos origin, BlueprintRotation rotation) {
        var data = PrefabLitematicaMod.manager(server).get(BlueprintItem.id(stack));
        if (data == null || data.retired) throw new IllegalArgumentException("Import a building at the blueprint workbench first");
        if (data.requiresReimport) throw new IllegalArgumentException("Reimport the original schematic: this legacy blueprint lost comparator output data");
        if (!data.fullyCharged()) throw new IllegalArgumentException("Blueprint must be charged to 100%");
        if (data.locked) throw new IllegalArgumentException("Blueprint is already in use");
        var old = session(player);
        // Reopening the same panel preserves its projection and in-flight scan.
        if (old != null && old.data.id.equals(data.id) && old.world == player.level() && old.origin.equals(origin) && old.rotation == rotation) return;
        if (old != null) old.encoded.cancel(false);
        var session = new Session(player, data, origin, rotation, encoder);
        sessions.put(player.getUUID(), session);
        try {
            var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
            out.writeLong(data.id.getMostSignificantBits()); out.writeLong(data.id.getLeastSignificantBits());
            out.writeUTF(data.name); out.writeInt(data.sizeX); out.writeInt(data.sizeY); out.writeInt(data.sizeZ);
            out.writeInt(data.blocks.size()); out.writeDouble(data.charge());
            send(session, OPEN, bytes.toByteArray());
            status(session);
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
    public void handle(ServerPlayer player, Request request) {
        if (request.action() == START) {
            try { begin(player, held(player, request.session()), request.origin(), BlueprintRotation.values()[request.rotation()]); }
            catch (Exception e) {
                String message = e.getMessage() == null ? "Blueprint is unavailable" : e.getMessage();
                byte[] bytes = message.substring(0, Math.min(500, message.length())).getBytes(StandardCharsets.UTF_8);
                if (ServerPlayNetworking.canSend(player, Response.TYPE)) ServerPlayNetworking.send(player,
                        new Response(request.session(), 0, ERROR, request.origin(), request.rotation(), 0, bytes.length, bytes));
            }
            return;
        }
        var session = session(player);
        if (session == null || !session.token.equals(request.session())) return;
        try {
            if (request.action() == CANCEL) { close(session); return; }
            if (request.action() == SHOW) {
                if (request.revision() < session.revision || session.confirming) return;
                session.revision = request.revision(); session.origin = request.origin(); session.rotation = BlueprintRotation.values()[request.rotation()];
                session.shown = true; session.lastComplete = null;
                session.scan = new BlueprintPreviewScan(player, session.data, session.origin, session.rotation);
                status(session); return;
            }
            if (request.action() == MOVE) {
                if (request.revision() <= session.revision) return;
                long now = System.currentTimeMillis();
                if (now - session.lastMove < 40) throw new IllegalArgumentException("Please wait before moving the projection again");
                session.lastMove = now;
                session.revision = request.revision(); session.origin = request.origin(); session.rotation = BlueprintRotation.values()[request.rotation()];
                session.confirming = false; session.lastComplete = null; session.scan = new BlueprintPreviewScan(player, session.data, session.origin, session.rotation);
                status(session); return;
            }
            if (request.revision() != session.revision || !request.origin().equals(session.origin) || request.rotation() != session.rotation.ordinal()) return;
            if (session.confirming) return;
            held(player, session.data.id);
            if (!session.shown) throw new IllegalArgumentException("Show the projection before executing placement");
            if (session.lastComplete == null || !session.lastComplete.clear()) throw new IllegalArgumentException("Wait for the full scan and clear all red conflicts first");
            // Recheck the entire area at confirmation, then use the normal authoritative placement preflight.
            session.confirming = true; session.scan = new BlueprintPreviewScan(player, session.data, session.origin, session.rotation); status(session);
        } catch (Exception e) { session.confirming = false; error(session, e); }
    }
    private static ItemStack held(ServerPlayer player, UUID id) {
        for (var stack : List.of(player.getMainHandItem(), player.getOffhandItem())) if (id.equals(BlueprintItem.id(stack))) return stack;
        throw new IllegalArgumentException("Hold the same blueprint to confirm placement");
    }
    private static boolean owns(ServerPlayer player, UUID id) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) if (id.equals(BlueprintItem.id(inventory.getItem(i)))) return true;
        return false;
    }
    public void tick() {
        int scanBudget = Math.max(1, PrefabLitematicaMod.CONFIG.blocksPlacedPerTick / Math.max(1, sessions.size()));
        for (var session : List.copyOf(sessions.values())) {
            try {
                if (session.player.hasDisconnected() || !session.player.isAlive() || session.player.level() != session.world || !owns(session.player, session.data.id)) { close(session); continue; }
                if (session.structure == null && session.encoded.isDone()) {
                    session.structure = session.encoded.join();
                    if (session.structure.length > PrefabLitematicaMod.CONFIG.maxUploadBytes) throw new IllegalArgumentException("Projection exceeds transfer limit");
                }
                if (session.structure != null && session.sent < session.structure.length) {
                    int end = Math.min(session.sent + BlueprintPayload.MAX_CHUNK, session.structure.length);
                    send(session, STRUCTURE, session.sent, session.structure.length, Arrays.copyOfRange(session.structure, session.sent, end)); session.sent = end;
                }
                if (!session.shown) continue;
                if (!session.scan.complete()) {
                    if (session.scan.tick(scanBudget)) {
                        session.lastComplete = session.scan;
                        status(session); session.nextScanTick = server.getTickCount() + 20;
                        if (session.confirming) {
                            session.confirming = false;
                            if (session.scan.clear()) {
                                try {
                                    PrefabLitematicaMod.placements(server).start(session.player, held(session.player, session.data.id), session.origin, session.rotation);
                                    close(session);
                                } catch (Exception e) { error(session, e); }
                            }
                        }
                    } else if ((session.lastComplete == null || session.confirming) && server.getTickCount() % 10 == 0) status(session);
                } else if (server.getTickCount() >= session.nextScanTick) {
                    session.scan = new BlueprintPreviewScan(session.player, session.data, session.origin, session.rotation);
                }
            } catch (Exception e) { session.confirming = false; error(session, e); close(session); }
        }
    }
    private static void status(Session session) throws IOException {
        var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
        var current = PrefabLitematicaMod.manager(session.world.getServer()).get(session.data.id);
        out.writeBoolean(session.scan.complete()); out.writeBoolean(session.shown);
        out.writeBoolean(current != null && current.fullyCharged() && !current.locked && !current.retired);
        out.writeUTF(session.scan.firstProblem()); out.write(session.scan.conflicts().toByteArray());
        byte[] data = bytes.toByteArray();
        for (int offset = 0; offset < data.length; offset += BlueprintPayload.MAX_CHUNK)
            send(session, STATUS, offset, data.length, Arrays.copyOfRange(data, offset, Math.min(data.length, offset + BlueprintPayload.MAX_CHUNK)));
    }
    private static void error(Session session, Exception error) {
        String message = error.getMessage() == null ? "Projection failed" : error.getMessage();
        send(session, ERROR, message.substring(0, Math.min(500, message.length())).getBytes(StandardCharsets.UTF_8));
    }
    private static void send(Session session, int kind, byte[] data) { send(session, kind, 0, data.length, data); }
    private static void send(Session session, int kind, int offset, int total, byte[] data) {
        if (ServerPlayNetworking.canSend(session.player, Response.TYPE)) ServerPlayNetworking.send(session.player,
                new Response(session.token, session.revision, kind, session.origin, session.rotation.ordinal(), offset, total, data));
    }
    private void close(Session session) { sessions.remove(session.player.getUUID(), session); session.encoded.cancel(false); send(session, CLOSED, new byte[0]); }
    public void disconnect(ServerPlayer player) { var session = sessions.remove(player.getUUID()); if (session != null) session.encoded.cancel(false); }
    public void stop() { sessions.clear(); encoder.shutdownNow(); }
}
