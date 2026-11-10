package dev.relay.docs;

import dev.relay.api.ClientApi;
import dev.relay.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.ArrayList;
import java.util.List;

public final class ClientHelloExample {
    public record Welcome(int version, String proxy, List<String> capabilities) {}

    private ClientHelloExample() {}

    public static byte[] hello(String modName) {
        if (modName == null || modName.isBlank() || modName.length() > 64) {
            throw new IllegalArgumentException("Use a mod name of 1 to 64 characters.");
        }
        ByteBuf payload = Unpooled.buffer();
        try {
            ProtocolUtils.writeVarInt(payload, ClientApi.Serverbound.HELLO);
            ProtocolUtils.writeVarInt(payload, ClientApi.PROTOCOL_VERSION);
            ProtocolUtils.writeString(payload, modName);
            byte[] bytes = new byte[payload.readableBytes()];
            payload.readBytes(bytes);
            return bytes;
        } finally {
            payload.release();
        }
    }

    public static Welcome readWelcome(byte[] message) {
        if (message.length > ClientApi.MAX_MESSAGE_BYTES) {
            throw new IllegalArgumentException("Oversized API payload.");
        }
        ByteBuf payload = Unpooled.wrappedBuffer(message);
        try {
            if (ProtocolUtils.readVarInt(payload) != ClientApi.Clientbound.WELCOME) {
                throw new IllegalArgumentException("Expected Welcome.");
            }
            int version = ProtocolUtils.readVarInt(payload);
            if (version < 1 || version > ClientApi.PROTOCOL_VERSION) {
                throw new IllegalArgumentException("Unsupported negotiated version.");
            }
            String proxy = ProtocolUtils.readString(payload, 256);
            int count = ProtocolUtils.readVarInt(payload);
            if (count < 0 || count > 64) {
                throw new IllegalArgumentException("Invalid capability count.");
            }
            List<String> capabilities = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                capabilities.add(ProtocolUtils.readString(payload, 64));
            }
            return new Welcome(version, proxy, List.copyOf(capabilities));
        } finally {
            payload.release();
        }
    }
}
