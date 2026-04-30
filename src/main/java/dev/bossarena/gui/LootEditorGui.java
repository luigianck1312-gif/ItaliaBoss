package dev.bossarena.gui;

import dev.bossarena.BossArena;
import dev.bossarena.managers.BossManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class LootEditorGui {

    private final BossArena plugin;

    // Titoli GUI
    public static final String TITLE_PRIMO   = "\u00a76\u00a7lLoot - 1\u00b0 Posto";
    public static final String TITLE_SECONDO = "\u00a76\u00a7lLoot - 2\u00b0 Posto";
    public static final String TITLE_TERZO   = "\u00a76\u00a7lLoot - 3\u00b0 Posto";
    public static final String TITLE_TUTTI   = "\u00a76\u00a7lLoot - Tutti";

    private static final int SIZE = 54;
    private static final int SAVE_SLOT = 49;

    public LootEditorGui(BossArena plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, String tier) {
        String title = switch (tier) {
            case "primo"   -> TITLE_PRIMO;
            case "secondo" -> TITLE_SECONDO;
            case "terzo"   -> TITLE_TERZO;
            default        -> TITLE_TUTTI;
        };

        Inventory inv = Bukkit.createInventory(null, SIZE, title);

        // Carica item salvati
        List<?> saved = plugin.getConfig().getList("loot.gilded." + tier);
        if (saved != null) {
            int slot = 0;
            for (Object o : saved) {
                if (slot >= SAVE_SLOT) break;
                if (o instanceof ItemStack is) inv.setItem(slot++, is);
            }
        }

        // Pulsante salva
        inv.setItem(SAVE_SLOT, makeSaveButton());

        player.openInventory(inv);
    }

    public void save(Player player, Inventory inv, String tier) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < SAVE_SLOT; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                items.add(item.clone());
            }
        }
        plugin.getConfig().set("loot.gilded." + tier, items);
        plugin.saveConfig();
        player.sendMessage(BossManager.color(plugin.getConfig()
                .getString("messages.loot-saved", "&aLoot salvato!")));
        player.closeInventory();
    }

    public boolean isSaveButton(int slot) {
        return slot == SAVE_SLOT;
    }

    private ItemStack makeSaveButton() {
        ItemStack item = new ItemStack(Material.LIME_WOOL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "SALVA LOOT");
        meta.setLore(List.of(ChatColor.GRAY + "Clicca per salvare gli oggetti"));
        item.setItemMeta(meta);
        return item;
    }

    public static String getTierFromTitle(String title) {
        if (title.equals(TITLE_PRIMO))   return "primo";
        if (title.equals(TITLE_SECONDO)) return "secondo";
        if (title.equals(TITLE_TERZO))   return "terzo";
        if (title.equals(TITLE_TUTTI))   return "tutti";
        return null;
    }
}
