package dev.codex.servertoolkit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Counts active playtime and periodically sends increments to the proxy's central store. */
public final class PlaytimeManager implements Listener {
    private final ServerToolkit plugin;
    private final Map<UUID, Long> totals = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastFlush = new ConcurrentHashMap<>();
    public PlaytimeManager(ServerToolkit plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::flushOnline, 20L, 20L * 30L);
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) {
        long now = System.currentTimeMillis();
        lastFlush.put(event.getPlayer().getUniqueId(), now);
        Bukkit.getScheduler().runTask(plugin, () -> plugin.utilityManager().updateScoreboard(event.getPlayer()));
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        flush(event.getPlayer());
        lastFlush.remove(event.getPlayer().getUniqueId());
    }
    private void flushOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            flush(player);
            plugin.utilityManager().updateScoreboard(player);
        }
    }
    private void flush(Player player) {
        UUID uuid = player.getUniqueId();
        Long last = lastFlush.get(uuid);
        long now = System.currentTimeMillis();
        if (last == null) { lastFlush.put(uuid, now); return; }
        long seconds = Math.max(0L, (now - last) / 1000L);
        if (seconds < 1) return;
        lastFlush.put(uuid, last + seconds * 1000L);
        totals.merge(uuid, seconds, Long::sum);
        plugin.networkSyncManager().addPlaytime(player, seconds);
    }
    public long totalSeconds(UUID uuid) { return Math.max(0L, totals.getOrDefault(uuid, 0L)); }
    public long liveSeconds(Player player) {
        Long last = lastFlush.get(player.getUniqueId());
        return totalSeconds(player.getUniqueId()) + (last == null ? 0L : Math.max(0L, (System.currentTimeMillis() - last) / 1000L));
    }
    public void applySynced(UUID uuid, long seconds) {
        if (seconds < 0) return;
        totals.put(uuid, seconds);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) plugin.utilityManager().updateScoreboard(player);
    }
    public String format(long seconds) {
        long days = seconds / 86400; long hours = (seconds % 86400) / 3600; long minutes = (seconds % 3600) / 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }
    public void handle(Player viewer, String[] args) {
        Player target = args.length == 0 ? viewer : Bukkit.getPlayerExact(args[0]);
        if (target == null) { viewer.sendMessage(ChatColor.RED + "Spieler nicht online."); return; }
        viewer.sendMessage(ChatColor.GOLD + "Spielzeit von " + target.getName() + ": " + ChatColor.YELLOW + format(liveSeconds(target)));
    }
}
