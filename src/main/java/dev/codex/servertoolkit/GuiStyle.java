package dev.codex.servertoolkit;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Shared red-accent inventory frame and ServerToolkit title helpers. */
public final class GuiStyle {
    private GuiStyle() {}

    public static String accent() { return "&c"; }

    public static String title(String section) {
        return color("&8AllInOne Survival &8» " + accent() + section);
    }

    /** Adds a quiet charcoal frame in any currently unused border slot. */
    public static void frame(Inventory inventory) {
        ItemStack pane = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        int rows = inventory.getSize() / 9;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            int row = slot / 9, col = slot % 9;
            if ((row == 0 || row == rows - 1 || col == 0 || col == 8) && inventory.getItem(slot) == null)
                inventory.setItem(slot, pane);
        }
    }

    private static String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }
}
