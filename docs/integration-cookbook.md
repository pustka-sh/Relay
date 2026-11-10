# Integration cookbook

Four complete recipes use APIs present in this checkout. The build compiles the Paper recipes against the actual Paper plugin output and dependency classpath with Java 17 API/bytecode constraints, and the client protocol recipes against the actual proxy output and dependencies with Java 21. The documentation checker also compares every block below with its source file. These are integration helpers, not complete deployable plugins or a Fabric/NeoForge mod.

Read [API stability](api-stability.md) before depending on implementation classes. The supported labels on protocol constants describe the documented wire surface; Relay does not ship an in-process proxy plugin API or promise Java binary compatibility. The Java helpers used by these recipes remain implementation details.

## Send a typed packet

Use this helper from a Paper plugin with a hard `depend: [Relay]` in its `plugin.yml`. Inside this repository, use `compileOnly(project(":paper-plugin"))` plus the Paper API, as the real [BlockMirror build](../examples/block-mirror/build.gradle.kts) does. Deploy Relay's companion plugin and your plugin on both backends. Do not shade a second copy of Relay's packet classes.

Both ends must use the identical factory name, fully qualified packet class name, registration order and field encoding. Construct one helper during your plugin's enable lifecycle, then call `invite("survival", senderUuid, "recipient")` on the Paper server thread. The receiving handler logs the invitation; replace that action with your application behavior.

[Checked source](examples/paper/dev/relay/docs/TypedPacketExample.java) · [Typed packet protocol](server-packets.md) · [Backend channels](backend-api.md#channels)

```java
package dev.relay.docs;

import dev.relay.paper.PacketBuffer;
import dev.relay.paper.PacketFactory;
import dev.relay.paper.RelayPacket;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public final class TypedPacketExample {
    private final Plugin plugin;
    private final PacketFactory packets;

    public TypedPacketExample(Plugin plugin) {
        this.plugin = plugin;
        packets = PacketFactory.named("cookbook-parties")
                .register(Invite.class, Invite::new)
                .build(plugin);
        packets.on(Invite.class, (from, invite) -> plugin.getLogger().info(
                "Invite from " + from + " for " + invite.recipient));
    }

    public boolean invite(String backend, UUID sender, String recipient) {
        boolean accepted = packets.send(backend, new Invite(sender, recipient));
        if (!accepted) {
            plugin.getLogger().warning("Invite could not be sent locally; no delivery was confirmed.");
        }
        return accepted;
    }

    public static final class Invite implements RelayPacket {
        private UUID sender;
        private String recipient;

        public Invite() {}

        public Invite(UUID sender, String recipient) {
            this.sender = sender;
            this.recipient = recipient;
        }

        @Override
        public void write(PacketBuffer out) {
            out.writeUuid(sender);
            out.writeString(recipient);
        }

        @Override
        public void read(PacketBuffer in) {
            sender = in.readUuid();
            recipient = in.readString();
        }
    }
}
```

The return value reports local acceptance, including possible holding while the source learns its name. It is **not** an acknowledgement of remote delivery. A backend with no player cannot carry a plugin message; unknown or empty destinations may produce no delivery. A workflow that needs confirmation must define its own acknowledgement packet, correlation ID and deadline. Registry fingerprints detect mismatched registrations, not changed field layouts inside a class. Deploy matching encoders on both ends.

## Query a backend

`GetServer` asks the proxy for the current backend's configured name; it is not a TCP status ping. Enable `backend-api = true` in `relay.toml`. Construct this helper during plugin enable, use an online player as the carrier, and call `close()` during plugin disable. Calls and callbacks belong on the Paper server thread. Register `BungeeCord` with Bukkit even though the wire channel is `bungeecord:main`.

[Checked source](examples/paper/dev/relay/docs/QueryBackendExample.java) · [Reply fields](backend-api.md#requests-that-reply) · [Real RelayApi source](../paper-plugin/src/main/java/dev/relay/paper/RelayApi.java)

```java
package dev.relay.docs;

import dev.relay.paper.RelayApi;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.function.Consumer;

public final class QueryBackendExample implements PluginMessageListener, AutoCloseable {
    public enum Status { ANSWERED, TIMED_OUT, MALFORMED, CANCELLED, SEND_FAILED }
    public record Result(Status status, String server) {}

    private final Plugin plugin;
    private final RelayApi api;
    private Consumer<Result> pending;
    private UUID carrierId;
    private BukkitTask deadline;
    private boolean closed;

    public QueryBackendExample(Plugin plugin) {
        this.plugin = plugin;
        api = new RelayApi(plugin);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, RelayApi.CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, RelayApi.CHANNEL, this);
    }

    // Call from the Paper server thread, using an online player on this backend.
    public void query(Player carrier, Consumer<Result> callback) {
        if (closed || pending != null || !carrier.isOnline()) {
            throw new IllegalStateException("Use an open query helper with no outstanding request and an online carrier.");
        }
        pending = java.util.Objects.requireNonNull(callback);
        carrierId = carrier.getUniqueId();
        deadline = plugin.getServer().getScheduler().runTaskLater(
                plugin, () -> finish(new Result(Status.TIMED_OUT, "")), 60L);
        try {
            api.requestServerName(carrier);
        } catch (RuntimeException failed) {
            finish(new Result(Status.SEND_FAILED, ""));
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player carrier, byte[] message) {
        if (!RelayApi.CHANNEL.equals(channel) || pending == null
                || !carrier.getUniqueId().equals(carrierId)) {
            return;
        }
        final String[] answer = {null};
        try {
            RelayApi.read(message, in -> {
                if ("GetServer".equals(in.readUTF())) {
                    answer[0] = in.readUTF();
                }
            });
        } catch (IllegalStateException malformed) {
            // Shared-channel traffic need not belong to this request. Keep waiting.
            return;
        }
        if (answer[0] != null) {
            finish(new Result(answer[0].isBlank() ? Status.MALFORMED : Status.ANSWERED, answer[0]));
        }
    }

    private void finish(Result result) {
        Consumer<Result> callback = pending;
        pending = null;
        carrierId = null;
        if (deadline != null) {
            deadline.cancel();
            deadline = null;
        }
        if (callback != null) {
            callback.accept(result);
        }
    }

    @Override
    public void close() {
        closed = true;
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, RelayApi.CHANNEL, this);
        finish(new Result(Status.CANCELLED, ""));
    }
}
```

The callback gets `ANSWERED`, `TIMED_OUT`, `MALFORMED`, `CANCELLED` or `SEND_FAILED`. The deadline is 60 server ticks, whose wall-clock duration depends on server performance. Silence cannot distinguish a disabled API, lost carrier, unsupported proxy or lost reply. Malformed shared-channel messages that cannot be identified are ignored until that deadline. Do not loop automatic retries.

This protocol has no request ID. The helper allows one outstanding query and matches its carrier, but a very late reply or another plugin's `GetServer` query on that same player can still be indistinguishable. Use one owner for this query per carrier; do not treat this recipe as a general concurrent RPC transport. The outgoing channel is shared by the owning plugin and remains registered until that plugin is disabled.

## Construct a client handshake

This is the Client API **Hello/Welcome** handshake carried inside a Minecraft custom payload, not the TCP login handshake. A mod sends `hello("MyMod")` on `ClientApi.CHANNEL` after joining, registers a receiver on that channel first, and feeds a Welcome payload to `readWelcome`. The code constructs payload bytes only; it does not pretend to supply a Fabric or NeoForge networking adapter. Enable `client-api = true` in `relay.toml`.

[Checked source](examples/java/dev/relay/docs/ClientHelloExample.java) · [Client wire format](client-api.md#wire-format) · [TCP login handshake example](java-examples.md#encode-a-login-handshake)

```java
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
```

Do not add a packet ID or channel string inside these payload bytes; your mod loader's custom-payload adapter supplies the outer transport. Keep the interface inactive until a valid Welcome arrives. If no Welcome arrives by an application-defined deadline, leave the feature inactive so the mod can work on other proxies. Capabilities control which actions to display; they do not authorize requests, and permissions can change during the session. Both encoding and decoding release their Netty buffers.

The build imports current Relay classes to detect source drift. For a separately built mod, implement the documented wire encoding in its loader's buffer API rather than embedding the entire proxy or depending on these internal helpers. Unknown capabilities may be retained and ignored. The count/string bounds in this example are defensive client limits rather than new wire protocol promises.

## Handle failed requests

Call this decoder only for payloads received on `ClientApi.CHANNEL`. It handles the actual Error codes and Switch Status fields described by the current protocol. Route Welcome and Server List messages to their own handlers. Render its returned text as plain text, and marshal resulting UI changes onto your mod loader's client thread.

[Checked source](examples/java/dev/relay/docs/FailureHandlingExample.java) · [Clientbound fields and errors](client-api.md#clientbound-proxy--client)

```java
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
```

`WAIT` keeps a switch pending, `CONNECTED` marks its current result, `REFRESH` invalidates stale UI data, and `PAUSE` stops repeated sends after a rate-limit refusal. No action automatically retries a switch. A failed switch should preserve the current connection and show the returned reason. The documented status numbers are 0 connecting, 1 connected and 2 failed; they are wire values, not imports of the proxy's private enum.

Unknown message types are left to other handlers. Unknown refusal codes produce a generic explanation; unknown status values can be ignored. Truncated or oversized replies leave the feature inactive. A UI also needs a local deadline for an unanswered request: expiry means an unknown result, not proof that the proxy refused it. The backend query above demonstrates a bounded deadline and cleanup.

## Verify and evolve recipes

Run `gradlew.bat checkPustkaExamples` on Windows or `bash ./gradlew checkPustkaExamples` elsewhere. This checks all six complete examples, including the two existing protocol examples. Then package the docs and run the [documentation checks](authoring-docs.md#documentation-checks). Update the checked source and its exact fence together. A changed Java signature fails compilation; a drifted guide fails fence comparison. Neither proves delivery, permissions, loader integration or live server behavior; exercise those in a real network before deployment.

Use [migration guidance](migration.md) when updating an integration between actual published versions.
