package dev.relay.docs;

import dev.relay.api.ClientApi;
import dev.relay.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.Optional;

public final class FailureHandlingExample {
    public enum Action { WAIT, CONNECTED, SHOW_ERROR, REFRESH, PAUSE, IGNORE }
    public record Outcome(Action action, String message) {}

    private FailureHandlingExample() {}

    // The caller has already checked that the custom-payload channel is ClientApi.CHANNEL.
    public static Optional<Outcome> read(byte[] message) {
        if (message.length > ClientApi.MAX_MESSAGE_BYTES) {
            return Optional.of(new Outcome(Action.SHOW_ERROR, "Oversized API reply."));
        }
        ByteBuf payload = Unpooled.wrappedBuffer(message);
        try {
            int type = ProtocolUtils.readVarInt(payload);
            if (type == ClientApi.Clientbound.ERROR) {
                int code = ProtocolUtils.readVarInt(payload);
                return Optional.of(switch (code) {
                    case ClientApi.Error.NO_PERMISSION -> new Outcome(Action.REFRESH, "Permission changed; refresh available actions.");
                    case ClientApi.Error.UNKNOWN_SERVER -> new Outcome(Action.REFRESH, "Destination no longer exists; refresh the list.");
                    case ClientApi.Error.SWITCH_IN_PROGRESS -> new Outcome(Action.WAIT, "A switch is already in progress.");
                    case ClientApi.Error.ALREADY_CONNECTED -> new Outcome(Action.CONNECTED, "Already on that backend.");
                    case ClientApi.Error.RATE_LIMITED -> new Outcome(Action.PAUSE, "Stop sending and wait for another user action.");
                    case ClientApi.Error.MALFORMED -> new Outcome(Action.SHOW_ERROR, "Request encoding was refused; check the integration.");
                    default -> new Outcome(Action.SHOW_ERROR, "Unknown API refusal code " + code + ".");
                });
            }
            if (type == ClientApi.Clientbound.SWITCH_STATUS) {
                String server = ProtocolUtils.readString(payload, 64);
                int status = ProtocolUtils.readVarInt(payload);
                String detail = ProtocolUtils.readString(payload, 32767);
                return Optional.of(switch (status) {
                    case 0 -> new Outcome(Action.WAIT, "Connecting to " + server + ".");
                    case 1 -> new Outcome(Action.CONNECTED, "Connected to " + server + ".");
                    case 2 -> new Outcome(Action.SHOW_ERROR, "Switch to " + server + " failed: " + detail);
                    default -> new Outcome(Action.IGNORE, "Unknown switch status " + status + ".");
                });
            }
            return Optional.empty(); // Welcome and server lists belong to other handlers.
        } catch (RuntimeException malformed) {
            return Optional.of(new Outcome(Action.SHOW_ERROR, "Malformed API reply; leave the feature inactive."));
        } finally {
            payload.release();
        }
    }
}
