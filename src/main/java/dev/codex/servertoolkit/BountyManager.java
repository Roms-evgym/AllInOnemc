package dev.codex.servertoolkit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Global proxy-backed player bounty system. */
public final class BountyManager implements Listener {
    private final ServerToolkit plugin;
    private final Map<UUID, Double> bounties = new ConcurrentHashMap<>();
    public BountyManager(ServerToolkit plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    public double bounty(UUID uuid) { return Math.max(0.0D, bounties.getOrDefault(uuid, 0.0D)); }
    public void applySynced(UUID uuid, double amount) {
        if (!Double.isFinite(amount) || amount < 0) return;
        bounties.put(uuid, amount);
        Player target = Bukkit.getPlayer(uuid);
        if (target != null) plugin.utilityManager().updateScoreboard(target);
    }
    public void handle(Player player, String[] args) {
        if (args.length < 1 || args.length > 2) {
            player.sendMessage(ChatColor.YELLOW + "/bounty <Spieler> [Betrag]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { player.sendMessage(ChatColor.RED + "Der Spieler muss online sein."); return; }
        if (target.equals(player)) { player.sendMessage(ChatColor.RED + "Du kannst kein Kopfgeld auf dich selbst setzen."); return; }
        if (args.length == 1) {
            player.sendMessage(ChatColor.GOLD + "Kopfgeld auf " + target.getName() + ": " + ChatColor.YELLOW
                    + String.format(java.util.Locale.GERMANY, "%.2f", bounty(target.getUniqueId())) + " Coins");
            return;
        }
        double amount;
        try { amount = Double.parseDouble(args[1].replace(',', '.')); }
        catch (NumberFormatException ex) { player.sendMessage(ChatColor.RED + "Ungültiger Betrag."); return; }
        if (!Double.isFinite(amount) || amount <= 0 || amount > 1_000_000_000D || Math.round(amount * 100) != amount * 100) {
            player.sendMessage(ChatColor.RED + "Der Betrag muss positiv sein und maximal 2 Nachkommastellen haben."); return;
        }
        if (!plugin.economyManager().withdraw(player.getUniqueId(), amount)) {
            player.sendMessage(ChatColor.RED + "Du hast nicht genug Coins."); return;
        }
        plugin.networkSyncManager().addBounty(player, target.getUniqueId(), amount);
        player.sendMessage(ChatColor.GREEN + "Du hast " + String.format(java.util.Locale.GERMANY, "%.2f", amount)
                + " Coins Kopfgeld auf " + target.getName() + " gesetzt.");
    }
    @EventHandler public void onPlayerDeath(PlayerDeathEvent event) {
        Player target = event.getEntity();
        Player hunter = target.getKiller();
        if (hunter == null || bounty(target.getUniqueId()) <= 0) return;
        plugin.networkSyncManager().claimBounty(hunter, target.getUniqueId());
        bounties.put(target.getUniqueId(), 0.0D);
        target.sendMessage(ChatColor.RED + "Dein Kopfgeld wurde eingesammelt.");
    }
}
