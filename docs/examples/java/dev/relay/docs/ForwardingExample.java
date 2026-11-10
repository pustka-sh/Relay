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
