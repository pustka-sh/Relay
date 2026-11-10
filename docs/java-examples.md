# Checked Java examples

These complete examples compile against the current Relay proxy classes during the documentation build. Their published code blocks must exactly match the checked source files. A renamed method or a changed signature fails the documentation build before publication.

## Encode a login handshake

This builds the complete uncompressed frame for a sample Java 1.20.2 login handshake. The packet body uses Relay’s own `HandshakePacket` encoder; the example adds the packet ID and outer length prefix. It returns bytes without opening a connection.

[Checked source](examples/java/dev/relay/docs/HandshakeExample.java) · [Protocol IDs](protocol-ids.md)

```java
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
```

The example releases both Netty buffers in `finally`. For another supported version, select a `ProtocolVersion` constant that matches the client. The next-state value `2` asks for login, while `1` asks for server status.

## Verify a forwarding signature

This illustrates a signing and verification round trip using an explicitly supplied test key and profile. The address is reserved example data. It performs no Minecraft authentication and opens no network connection. In normal proxy operation Relay signs the profile; the backend verifies it.

[Checked source](examples/java/dev/relay/docs/ForwardingExample.java) · [Backend API](backend-api.md)

```java
package dev.relay.docs;

import dev.relay.auth.GameProfile;
import dev.relay.forwarding.ModernForwarding;

public final class ForwardingExample {
    private ForwardingExample() {}

    public static boolean signatureRoundTrip(byte[] testKey, GameProfile testProfile) {
        byte[] response = ModernForwarding.createResponse(
                testKey, "203.0.113.10", testProfile, 4);
        return ModernForwarding.verify(testKey, response);
    }
}
```

The sample requests modern forwarding version `4`, which Relay supports. Authentication and backend forwarding remain separate: a valid signature proves that the sender knows the shared forwarding key, not that an arbitrary supplied profile was authenticated. Use the profile produced by the actual login flow in a real integration.

## Keep examples current

From Relay, run `bash ./gradlew checkPustkaExamples` (or `gradlew.bat checkPustkaExamples` on Windows). Then generate/package the docs and run Site’s `scripts/check-docs.mjs`, as described in [Writing documentation](authoring-docs.md#documentation-checks). The first check compiles the real source files; the second checks that the published blocks still match them.
