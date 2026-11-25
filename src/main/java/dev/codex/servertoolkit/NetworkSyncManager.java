package dev.codex.servertoolkit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;

/** Bridges ServerToolkit player data to the AllInOneBungee proxy store. */
public final class NetworkSyncManager implements Listener, PluginMessageListener {
    public static final String CHANNEL = "allinone:sync";
    private final ServerToolkit plugin;

    public NetworkSyncManager(ServerToolkit plugin) {
        this.plugin = plugin;
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("AllInOne network sync bridge enabled on channel " + CHANNEL);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> sendHello(event.getPlayer()), 40L);
    }

    public void publish(Player player) {
        if (player == null || !player.isOnline()) return;
        sendPacket(player, "UPDATE", plugin.utilityManager().rankId(player),
                plugin.economyManager().balance(player.getUniqueId()).toPlainString());
    }

    private void sendHello(Player player) {
        if (player == null || !player.isOnline()) return;
        sendPacket(player, "HELLO", plugin.utilityManager().rankId(player),
                plugin.economyManager().balance(player.getUniqueId()).toPlainString());
    }

    private void sendPacket(Player player, String operation, String rank, String balance) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF(operation);
            out.writeUTF(player.getUniqueId().toString());
            out.writeUTF(rank);
            out.writeUTF(balance);
            out.flush();
            player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not send network sync packet: " + ex.getMessage());
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel) || message.length > 4096) return;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String operation = in.readUTF();
            if ("REQUEST_ALL".equals(operation)) {
                if (in.available() != 0) return;
                for (Player online : Bukkit.getOnlinePlayers()) {
                    publish(online);
                }
                plugin.getLogger().info("Proxy requested a full rank and balance sync; published "
                        + Bukkit.getOnlinePlayers().size() + " online player(s).");
                return;
            }
            UUID uuid = UUID.fromString(in.readUTF());
            String rank = in.readUTF();
            BigDecimal balance = new BigDecimal(in.readUTF());
            // Proxy-to-backend packets may be carried by any player connected to the
            // target server; the UUID in the trusted proxy payload identifies the real owner.
            if (!"SYNC".equals(operation) || !rank.matches("[a-zA-Z0-9_-]{1,32}")
                    || balance.signum() < 0 || balance.scale() > 2
                    || balance.precision() > 18 || in.available() != 0) return;

            plugin.utilityManager().applySyncedRank(uuid, rank);
            plugin.economyManager().applySyncedBalance(uuid, balance);
            plugin.getLogger().fine("Applied proxy data for " + uuid + " (rank " + rank + ").");
        } catch (Exception ex) {
            plugin.getLogger().warning("Rejected invalid network sync packet: " + ex.getMessage());
        }
    }
}
