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

public class RewardClaimGui {

    private final BossArena plugin;
    private final BossManager bossManager;

    public static final String TITLE = "\u00a76\u00a7lRicompense Boss";
    private static final int CLAIM_SLOT = 49;

    public RewardClaimGui(BossArena plugin) {
        this.plugin = plugin;
        this.bossManager = plugin.getBossManager();
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        int rank = bossManager.getPendingRanks().getOrDefault(player.getUniqueId(), 0);

        // Mostra preview loot
        List<ItemStack> preview = new ArrayList<>();
        if (rank == 1)      preview.addAll(getLootItems("primo"));
        else if (rank == 2) preview.addAll(getLootItems("secondo"));
        else if (rank == 3) preview.addAll(getLootItems("terzo"));
        preview.addAll(getLootItems("tutti"));

        int slot = 0;
        for (ItemStack item : preview) {
            if (slot >= CLAIM_SLOT) break;
            if (item != null) inv.setItem(slot++, item.clone());
        }

        // Pulsante riscatta
        inv.setItem(CLAIM_SLOT, makeClaimButton(rank));

        player.openInventory(inv);
    }

    private ItemStack makeClaimButton(int rank) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "RISCATTA TUTTO");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Clicca per ricevere le tue ricompense");
        if (rank > 0) lore.add(ChatColor.YELLOW + "Posizione: " + ChatColor.GOLD + rank + "\u00b0");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @SuppressWarnings("unchecked")
    private List<ItemStack> getLootItems(String tier) {
        List<?> raw = plugin.getConfig().getList("loot.gilded." + tier);
        if (raw == null) return new ArrayList<>();
        List<ItemStack> items = new ArrayList<>();
        for (Object o : raw) {
            if (o instanceof ItemStack is) items.add(is);
        }
        return items;
    }

    public boolean isClaimButton(int slot) { return slot == CLAIM_SLOT; }
}
