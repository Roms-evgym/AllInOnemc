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
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "fakebase" -> placeFakeBase(player);
            case "fakore" -> placeOreVein(player, args);
            default -> { return false; }
        }
        return true;
    }

    private void placeFakeBase(Player player) {
        Location forward = player.getLocation().clone().add(player.getLocation().getDirection().setY(0).normalize().multiply(6));
        World world = player.getWorld();
        int x0 = forward.getBlockX() - 2;
        int z0 = forward.getBlockZ() - 2;
        int y0 = world.getHighestBlockYAt(forward) + 1;

        // A compact 5x5 stone-brick shell with a two-block entrance.
        for (int x = x0; x < x0 + 5; x++) {
            for (int z = z0; z < z0 + 5; z++) {
                for (int y = y0; y <= y0 + 3; y++) {
                    boolean doorway = z == z0 && x == x0 + 2 && (y == y0 + 1 || y == y0 + 2);
                    if (doorway) continue;
                    Block block = world.getBlockAt(x, y, z);
                    if (!block.isPassable()) {
                        player.sendMessage(color("&cHier ist nicht genug Platz. Die Fakebase wurde nicht gebaut."));
                        return;
                    }
                }
            }
        }
        for (int x = x0; x < x0 + 5; x++) {
            for (int z = z0; z < z0 + 5; z++) {
                for (int y = y0; y <= y0 + 3; y++) {
                    boolean floorOrRoof = y == y0 || y == y0 + 3;
                    boolean wall = x == x0 || x == x0 + 4 || z == z0 || z == z0 + 4;
                    boolean doorway = z == z0 && x == x0 + 2 && (y == y0 + 1 || y == y0 + 2);
                    if (!doorway && (floorOrRoof || wall)) world.getBlockAt(x, y, z).setType(Material.STONE_BRICKS, false);
                }
            }
        }
        player.sendMessage(color("&aFakebase aus Steinziegeln vor dir platziert."));
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

        Location origin = player.getLocation();
        World world = player.getWorld();
        int baseY = Math.max(world.getMinHeight(), origin.getBlockY() - 6);
        int x = origin.getBlockX();
        int y = baseY;
        int z = origin.getBlockZ();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Block> vein = new ArrayList<>();
        int attempts = size * 40;
        while (vein.size() < size && attempts-- > 0) {
            Block candidate = world.getBlockAt(x, y, z);
            if (isStoneHost(candidate.getType()) && !vein.contains(candidate)) vein.add(candidate);
            switch (random.nextInt(6)) {
                case 0 -> x++;
                case 1 -> x--;
                case 2 -> y++;
                case 3 -> y--;
                case 4 -> z++;
                default -> z--;
            }
            y = Math.max(world.getMinHeight(), Math.min(world.getMaxHeight() - 1, y));
        }
        if (vein.isEmpty()) {
            player.sendMessage(color("&cUnter dir wurde kein Stein oder Tiefenschiefer für die Erzader gefunden."));
            return;
        }
        for (Block block : vein) block.setType(ore, false);
        player.sendMessage(color("&a" + vein.size() + " Blöcke &f" + ore.name().toLowerCase(Locale.ROOT)
                + " &ain eine Erzader unter dir gesetzt."));
    }

    private boolean isStoneHost(Material material) {
        return material == Material.STONE || material == Material.DEEPSLATE
                || material == Material.ANDESITE || material == Material.DIORITE
                || material == Material.GRANITE || material == Material.TUFF;
    }

    private String color(String text) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
    }
}
