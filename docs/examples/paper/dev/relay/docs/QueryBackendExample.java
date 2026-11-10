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
