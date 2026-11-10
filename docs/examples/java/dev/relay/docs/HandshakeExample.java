package dev.relay.docs;

import dev.relay.protocol.PacketDirection;
import dev.relay.protocol.ProtocolUtils;
import dev.relay.protocol.ProtocolVersion;
import dev.relay.protocol.packet.HandshakePacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public final class HandshakeExample {
    private HandshakeExample() {}

    public static byte[] sampleLoginHandshake() {
        ProtocolVersion version = ProtocolVersion.MINECRAFT_1_20_2;
        HandshakePacket packet = new HandshakePacket(
                version.id(), "play.example.com", 25577, 2);
        ByteBuf body = Unpooled.buffer();
        ByteBuf frame = Unpooled.buffer();
        try {
            ProtocolUtils.writeVarInt(body, 0); // Handshake packet ID.
            packet.encode(body, PacketDirection.SERVERBOUND, version);
            ProtocolUtils.writeVarInt(frame, body.readableBytes());
            frame.writeBytes(body);
            byte[] bytes = new byte[frame.readableBytes()];
            frame.readBytes(bytes);
            return bytes;
        } finally {
            frame.release();
            body.release();
        }
    }
}
