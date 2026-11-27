package dev.codex.servertoolkit;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Admin tools for placing a decoy base and a configurable ore vein. */
public final class WorldSpawnManager implements CommandExecutor {
    private static final int MAX_VEIN_SIZE = 64;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Befehl ist nur im Spiel verfügbar.");
            return true;
        }
        if (!player.hasPermission("servertoolkit.admin") && !player.isOp()) {
            player.sendMessage(color("&cDafür hast du keine Rechte."));
            return true;
        }
        switch (label.toLowerCase(Locale.ROOT)) {
            case "fakebase" -> placeFakeBase(player);
            case "fakore" -> placeOreVein(player, args);
            default -> { return false; }
        }
        return true;
    }

    private void placeFakeBase(Player player) {
        Location center = player.getLocation();
        World world = player.getWorld();
        int x0 = center.getBlockX() - 2;
        int z0 = center.getBlockZ() - 2;
        int y0 = Math.max(world.getMinHeight(), center.getBlockY() - 1);

        // Place the complete shell at the player's current position.
        // Existing blocks are deliberately replaced instead of aborting when space is occupied.
        for (int x = x0; x < x0 + 5; x++) {
            for (int z = z0; z < z0 + 5; z++) {
                for (int y = y0; y <= y0 + 3 && y < world.getMaxHeight(); y++) {
                    boolean floorOrRoof = y == y0 || y == y0 + 3;
                    boolean wall = x == x0 || x == x0 + 4 || z == z0 || z == z0 + 4;
                    boolean doorway = z == z0 && x == x0 + 2 && (y == y0 + 1 || y == y0 + 2);
                    if (!doorway && (floorOrRoof || wall)) {
                        world.getBlockAt(x, y, z).setType(Material.STONE_BRICKS, false);
                    }
                }
            }
        }
        player.sendMessage(color("&aFakebase direkt an deiner Position platziert. Vorhandene Blöcke wurden bei Bedarf ersetzt."));
    }

    private void placeOreVein(Player player, String[] args) {
        if (args.length < 1 || args.length > 2) {
            player.sendMessage(color("&cNutzung: /fakore <erz> [anzahl 1-64]"));
            return;
        }
        String input = args[0].toUpperCase(Locale.ROOT);
        Material ore = Material.matchMaterial(input.endsWith("_ORE") ? input : input + "_ORE");
        if (ore == null || !ore.isBlock() || !ore.name().endsWith("_ORE")) {
            player.sendMessage(color("&cUngültiges Erz. Beispiel: /fakore diamond 8"));
            return;
        }
        int size = 8;
        if (args.length == 2) {
            try { size = Integer.parseInt(args[1]); }
            catch (NumberFormatException exception) {
                player.sendMessage(color("&cDie Anzahl muss eine Zahl zwischen 1 und 64 sein."));
                return;
            }
        }
        if (size < 1 || size > MAX_VEIN_SIZE) {
            player.sendMessage(color("&cDie Erzader darf 1 bis 64 Blöcke enthalten."));
            return;
        }

        // Start exactly at the player's block position and replace whatever is there.
        World world = player.getWorld();
        Location origin = player.getLocation();
        int x = origin.getBlockX();
        int y = Math.max(world.getMinHeight(), Math.min(world.getMaxHeight() - 1, origin.getBlockY()));
        int z = origin.getBlockZ();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Block> vein = new ArrayList<>();
        vein.add(world.getBlockAt(x, y, z));
        int attempts = size * 40;
        while (vein.size() < size && attempts-- > 0) {
            Block last = vein.get(random.nextInt(vein.size()));
            int nextX = last.getX();
            int nextY = last.getY();
            int nextZ = last.getZ();
            switch (random.nextInt(6)) {
                case 0 -> nextX++;
                case 1 -> nextX--;
                case 2 -> nextY++;
                case 3 -> nextY--;
                case 4 -> nextZ++;
                default -> nextZ--;
            }
            if (nextY < world.getMinHeight() || nextY >= world.getMaxHeight()) continue;
            Block candidate = world.getBlockAt(nextX, nextY, nextZ);
            if (!vein.contains(candidate)) vein.add(candidate);
        }
        for (Block block : vein) block.setType(ore, false);
        player.sendMessage(color("&a" + vein.size() + " Blöcke &f" + ore.name().toLowerCase(Locale.ROOT)
                + " &adirekt an deiner Position als Erzader platziert. Vorhandene Blöcke wurden ersetzt."));
    }

    private String color(String text) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
    }
}
