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

/** Bridges player data and global bounty/playtime counters to AllInOneBungee. */
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
    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> sendHello(event.getPlayer()), 40L);
    }
    public void publish(Player player) {
        if (player == null || !player.isOnline()) return;
        sendPlayerPacket(player, "UPDATE");
    }
    private void sendHello(Player player) {
        if (player != null && player.isOnline()) sendPlayerPacket(player, "HELLO");
    }
    private void sendPlayerPacket(Player player, String operation) {
        send(player, out -> {
            out.writeUTF(operation); out.writeUTF(player.getUniqueId().toString());
            out.writeUTF(plugin.utilityManager().rankId(player));
            out.writeUTF(plugin.economyManager().balance(player.getUniqueId()).toPlainString());
        });
    }
    public void addPlaytime(Player player, long seconds) {
        if (player == null || !player.isOnline() || seconds < 1) return;
        send(player, out -> { out.writeUTF("PLAYTIME_ADD"); out.writeUTF(player.getUniqueId().toString()); out.writeLong(seconds); });
    }
    public void addBounty(Player payer, UUID target, double amount) {
        if (payer == null || !payer.isOnline() || target == null || amount <= 0 || !Double.isFinite(amount)) return;
        send(payer, out -> { out.writeUTF("BOUNTY_ADD"); out.writeUTF(target.toString()); out.writeDouble(amount); });
    }
    public void claimBounty(Player hunter, UUID target) {
        if (hunter == null || !hunter.isOnline() || target == null || target.equals(hunter.getUniqueId())) return;
        send(hunter, out -> { out.writeUTF("BOUNTY_CLAIM"); out.writeUTF(target.toString()); out.writeUTF(hunter.getUniqueId().toString()); });
    }
    private void send(Player carrier, PacketWriter writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
            writer.write(out); out.flush(); carrier.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (IOException ex) { plugin.getLogger().warning("Could not send network sync packet: " + ex.getMessage()); }
    }
    @Override public void onPluginMessageReceived(String channel, Player carrier, byte[] message) {
        if (!CHANNEL.equals(channel) || message == null || message.length > 4096) return;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String operation = in.readUTF();
            if ("REQUEST_ALL".equals(operation)) {
                if (in.available() != 0) return;
                for (Player online : Bukkit.getOnlinePlayers()) publish(online);
                return;
            }
            if ("BOUNTY_REWARD".equals(operation)) {
                UUID hunterId = UUID.fromString(in.readUTF()); double reward = in.readDouble();
                if (reward <= 0 || !Double.isFinite(reward) || in.available() != 0) return;
                plugin.economyManager().deposit(hunterId, reward);
                Player hunter = Bukkit.getPlayer(hunterId);
                if (hunter != null) hunter.sendMessage("§aDu hast ein Kopfgeld von §e" + String.format(java.util.Locale.US, "%.2f", reward) + " Coins §aeingesammelt!");
                return;
            }
            if (!"SYNC".equals(operation)) return;
            UUID uuid = UUID.fromString(in.readUTF());
            String rank = in.readUTF();
            BigDecimal balance = new BigDecimal(in.readUTF());
            long playtimeSeconds = in.readLong();
            double bounty = in.readDouble();
            if (!rank.matches("[a-zA-Z0-9_-]{1,32}") || balance.signum() < 0 || balance.scale() > 2
                    || balance.precision() > 18 || playtimeSeconds < 0 || !Double.isFinite(bounty) || bounty < 0 || in.available() != 0) return;
            plugin.utilityManager().applySyncedRank(uuid, rank);
            plugin.economyManager().applySyncedBalance(uuid, balance);
            plugin.playtimeManager().applySynced(uuid, playtimeSeconds);
            plugin.bountyManager().applySynced(uuid, bounty);
        } catch (Exception ex) { plugin.getLogger().warning("Rejected invalid network sync packet: " + ex.getMessage()); }
    }
    @FunctionalInterface private interface PacketWriter { void write(DataOutputStream out) throws IOException; }
}
