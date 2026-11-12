package dev.codex.servertoolkit;

import org.bukkit.Location;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Lightweight seat based /sit command without an external GSit dependency. */
public final class SitManager implements CommandExecutor, Listener {
    private final JavaPlugin plugin;
    private final Map<UUID, ArmorStand> seats = new HashMap<>();

    public SitManager(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Befehl ist nur im Spiel verfügbar.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("off")) {
            standUp(player);
            return true;
        }
        ArmorStand current = seats.get(player.getUniqueId());
        if (current != null && current.isValid()) {
            standUp(player);
            return true;
        }
        if (!player.isOnGround() || player.isInsideVehicle() || player.isDead()) {
            player.sendMessage(color("&cDu kannst gerade nicht sitzen."));
            return true;
        }

        Location seatLocation = player.getLocation().getBlock().getLocation().add(0.5, 0.1, 0.5);
        ArmorStand seat = player.getWorld().spawn(seatLocation, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setPersistent(false);
        });
        seats.put(player.getUniqueId(), seat);
        seat.addPassenger(player);
        player.sendMessage(color("&7Du sitzt. Nutze &f/sit &7oder &f/sit off &7zum Aufstehen."));
        return true;
    }

    private void standUp(Player player) {
        ArmorStand seat = seats.remove(player.getUniqueId());
        if (seat == null) return;
        if (seat.getPassengers().contains(player)) seat.removePassenger(player);
        if (seat.isValid()) seat.remove();
        player.sendMessage(color("&7Du bist aufgestanden."));
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player
                && event.getDismounted() instanceof ArmorStand stand
                && seats.get(player.getUniqueId()) == stand) {
            seats.remove(player.getUniqueId());
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (stand.isValid()) stand.remove();
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeSeat(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        removeSeat(event.getPlayer().getUniqueId());
    }

    private void removeSeat(UUID playerId) {
        ArmorStand seat = seats.remove(playerId);
        if (seat != null && seat.isValid()) seat.remove();
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
