package dev.bossarena.listeners;

import dev.bossarena.BossArena;
import dev.bossarena.managers.BossManager;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.lumine.mythic.bukkit.events.MythicMobDamageEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public class BossListener implements Listener {

    private final BossArena plugin;
    private final BossManager bossManager;

    public BossListener(BossArena plugin) {
        this.plugin = plugin;
        this.bossManager = plugin.getBossManager();
    }

    /** Tracka i danni al boss da parte dei giocatori */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossDamage(EntityDamageByEntityEvent event) {
        if (!bossManager.isActiveMob(event.getEntity().getUniqueId())) return;
        if (!(event.getDamager() instanceof Player player)) return;
        bossManager.registerDamage(player.getUniqueId(), event.getFinalDamage());
    }

    /** Boss morto */
    @EventHandler
    public void onMythicDeath(MythicMobDeathEvent event) {
        if (!bossManager.isActiveMob(event.getEntity().getUniqueId())) return;
        bossManager.handleBossDeath();
    }

    /** Giocatore morto nell'arena */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!bossManager.isBossActive()) return;
        if (bossManager.getCurrentSession() == null) return;
        if (!bossManager.getCurrentSession().isInArena(player.getUniqueId())) return;

        bossManager.handlePlayerDeath(player);

        // Teletrasporta al respawn (bed o spawn) dopo 1 tick
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.spigot().respawn();
            }
        }, 1L);
    }
}
