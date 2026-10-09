// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.UUID;

public final class BlueprintPreviewPayload {
    public static boolean validOrigin(BlockPos pos) { return Math.abs((long) pos.getX()) <= 30000000 && Math.abs((long) pos.getZ()) <= 30000000 && pos.getY() >= -2048 && pos.getY() <= 2047; }
    public static final int MOVE = 0, CONFIRM = 1, CANCEL = 2, START = 3, SHOW = 4;
    public static final int OPEN = 0, STRUCTURE = 1, STATUS = 2, CLOSED = 3, ERROR = 4;
    public record Request(UUID session, int revision, int action, BlockPos origin, int rotation) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(Identifier.parse("prefablitematica:preview_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = new StreamCodec<>() {
            @Override public Request decode(RegistryFriendlyByteBuf buf) {
                var result = new Request(buf.readUUID(), buf.readVarInt(), buf.readVarInt(), buf.readBlockPos(), buf.readUnsignedByte());
                if (result.revision < 0 || result.action < MOVE || result.action > SHOW || result.rotation > 3 || !validOrigin(result.origin)) throw new IllegalArgumentException("Invalid preview request");
                return result;
            }
            @Override public void encode(RegistryFriendlyByteBuf buf, Request value) {
                buf.writeUUID(value.session); buf.writeVarInt(value.revision); buf.writeVarInt(value.action); buf.writeBlockPos(value.origin); buf.writeByte(value.rotation);
            }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    /** Structure/status packets are bounded, ordered chunks; total is the complete byte length. */
    public record Response(UUID session, int revision, int kind, BlockPos origin, int rotation, int offset, int total, byte[] data) implements CustomPacketPayload {
        public static final Type<Response> TYPE = new Type<>(Identifier.parse("prefablitematica:preview_response"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Response> CODEC = new StreamCodec<>() {
            @Override public Response decode(RegistryFriendlyByteBuf buf) {
                var result = new Response(buf.readUUID(), buf.readVarInt(), buf.readVarInt(), buf.readBlockPos(), buf.readUnsignedByte(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(BlueprintPayload.MAX_CHUNK));
                if (result.revision < 0 || result.kind < OPEN || result.kind > ERROR || result.rotation > 3 || result.offset < 0 || result.total < 0 || result.total > 33554432 || result.offset > result.total - result.data.length)
                    throw new IllegalArgumentException("Invalid preview response");
                return result;
            }
            @Override public void encode(RegistryFriendlyByteBuf buf, Response value) {
                buf.writeUUID(value.session); buf.writeVarInt(value.revision); buf.writeVarInt(value.kind); buf.writeBlockPos(value.origin); buf.writeByte(value.rotation);
                buf.writeVarInt(value.offset); buf.writeVarInt(value.total); buf.writeByteArray(value.data);
            }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private BlueprintPreviewPayload() {}
}
