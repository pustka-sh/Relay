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
