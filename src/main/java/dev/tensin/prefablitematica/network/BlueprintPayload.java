// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.UUID;

public record BlueprintPayload(int operation, int syncId, UUID transferId, int sequence, byte[] data) implements CustomPacketPayload {
    public static final int MAX_CHUNK = 24576;
    public static final UUID EMPTY = new UUID(0, 0);
    public static final Type<BlueprintPayload> C2S = new Type<>(Identifier.parse("prefablitematica:request"));
    public static final Type<BlueprintPayload> S2C = new Type<>(Identifier.parse("prefablitematica:response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintPayload> CODEC = new StreamCodec<>() {
        @Override public BlueprintPayload decode(RegistryFriendlyByteBuf buf) {
            int operation = buf.readVarInt(), sync = buf.readVarInt(); UUID transfer = buf.readUUID(); int sequence = buf.readVarInt(); byte[] bytes = buf.readByteArray(MAX_CHUNK);
            if (operation < 0 || operation > 10 || sequence < 0) throw new IllegalArgumentException("Invalid blueprint packet");
            return new BlueprintPayload(operation, sync, transfer, sequence, bytes);
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, BlueprintPayload payload) {
            if (payload.data.length > MAX_CHUNK) throw new IllegalArgumentException("Blueprint packet too large");
            buf.writeVarInt(payload.operation); buf.writeVarInt(payload.syncId); buf.writeUUID(payload.transferId); buf.writeVarInt(payload.sequence); buf.writeByteArray(payload.data);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return C2S; }
    public record Response(BlueprintPayload value) implements CustomPacketPayload {
        public static final Type<Response> TYPE = new Type<>(Identifier.parse("prefablitematica:response"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Response> CODEC = BlueprintPayload.CODEC.map(Response::new, Response::value);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
