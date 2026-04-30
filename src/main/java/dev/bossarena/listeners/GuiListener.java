package dev.bossarena.listeners;

import dev.bossarena.BossArena;
import dev.bossarena.gui.LootEditorGui;
import dev.bossarena.gui.RewardClaimGui;
import dev.bossarena.managers.BossManager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

public class GuiListener implements Listener {

    private final BossArena plugin;
    private final BossManager bossManager;
    private final LootEditorGui lootGui;
    private final RewardClaimGui rewardGui;

    public GuiListener(BossArena plugin) {
        this.plugin     = plugin;
        this.bossManager = plugin.getBossManager();
        this.lootGui    = plugin.getLootEditorGui();
        this.rewardGui  = plugin.getRewardClaimGui();
    }

    /** Click sul villager NPC ricompense */
    @EventHandler
    public void onNpcClick(PlayerInteractEntityEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;
        if (!(event.getRightClicked() instanceof Villager)) return;

        var uuid = event.getRightClicked().getUniqueId();
        if (!bossManager.isRewardNpc(uuid)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        if (!bossManager.canClaim(player.getUniqueId())) {
            player.sendMessage(BossManager.color("&cNon hai ricompense disponibili o le hai gia' ritirate!"));
            return;
        }

        rewardGui.open(player);
    }

    /** Click nella GUI loot editor (admin) */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        // GUI Loot Editor
        String tier = LootEditorGui.getTierFromTitle(title);
        if (tier != null) {
            if (lootGui.isSaveButton(event.getRawSlot())) {
                event.setCancelled(true);
                lootGui.save(player, event.getInventory(), tier);
            }
            // Permetti drag & drop degli item (non cancellare altri click)
            return;
        }

        // GUI Reward Claim
        if (title.equals(RewardClaimGui.TITLE)) {
            event.setCancelled(true);
            if (rewardGui.isClaimButton(event.getRawSlot())) {
                bossManager.claimRewards(player);
                player.closeInventory();
            }
        }
    }
}
