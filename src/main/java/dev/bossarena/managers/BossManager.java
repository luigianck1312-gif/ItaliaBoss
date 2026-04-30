package dev.bossarena.managers;

import dev.bossarena.BossArena;
import dev.bossarena.models.BossSession;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.logging.Level;

public class BossManager {

    private final BossArena plugin;
    private BossSession currentSession = null;
    private UUID activeMobUUID = null;
    private boolean announced = false;

    private UUID rewardNpcUUID = null;
    private final Set<UUID> claimedRewards = new HashSet<>();
    private final Map<UUID, Integer> pendingRanks = new HashMap<>();
    private long lastSpawnTime = 0;

    public BossManager(BossArena plugin) {
        this.plugin = plugin;
    }

    // ── Spawn ─────────────────────────────────────────────────────────────────

    public boolean spawnBoss() {
        if (isBossActive()) return false;
        announceAndScheduleSpawn();
        return true;
    }

    public void resetSession() {
        currentSession = null;
        activeMobUUID = null;
        announced = false;
    }

    public void announceAndScheduleSpawn() {
        int delay = plugin.getConfig().getInt("boss.spawn-delay-seconds", 20);
        String msg = color(plugin.getConfig().getString("messages.announce",
                "&4&l☠ &c&lIL GILDED SENTINEL SI È RISVEGLIATO! &4&l☠\n&7Scrivi &e/boss fight &7per entrare!\n&8(Spawna tra &e%seconds% secondi&8)")
                .replace("%seconds%", String.valueOf(delay)));

        announced = true;
        currentSession = new BossSession(plugin.getConfig().getString("boss.mythic-name", "gilded_sentinel"));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(msg);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.8f);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            announced = false;
            // Esegui esattamente il comando configurato
            String spawnCmd = plugin.getConfig().getString("boss.spawn-command",
                    "mm mobs spawn gilded_sentinel 1 world,50073,85,49956,0,0");
            plugin.getLogger().info("[BossArena] Esecuzione: " + spawnCmd);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), spawnCmd);

            currentSession.start();
            lastSpawnTime = System.currentTimeMillis();

            // Cerca UUID boss dopo 20 tick
            String mythicName = plugin.getConfig().getString("boss.mythic-name", "gilded_sentinel");
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (World w : Bukkit.getWorlds()) {
                    for (Entity e : w.getEntities()) {
                        try {
                            if (io.lumine.mythic.bukkit.MythicBukkit.inst()
                                    .getMobManager().isActiveMob(e.getUniqueId())) {
                                activeMobUUID = e.getUniqueId();
                                plugin.getLogger().info("[BossArena] Boss UUID: " + activeMobUUID);
                                break;
                            }
                        } catch (Exception ex) {
                            // ignora
                        }
                    }
                    if (activeMobUUID != null) break;
                }
            }, 20L);

        }, delay * 20L);
    }

    // ── Fight ─────────────────────────────────────────────────────────────────

    public boolean joinFight(Player player) {
        if (!isBossActive()) return false;
        if (currentSession == null) return false;
        if (currentSession.isDead(player.getUniqueId())) return false;
        if (currentSession.isInArena(player.getUniqueId())) return false;

        currentSession.joinPlayer(player);
        Location spawnLoc = getPlayerSpawnLocation();
        if (spawnLoc != null) player.teleport(spawnLoc);
        return true;
    }

    // ── Danno ─────────────────────────────────────────────────────────────────

    public void registerDamage(UUID playerUUID, double amount) {
        if (currentSession != null && currentSession.isActive())
            currentSession.addDamage(playerUUID, amount);
    }

    public boolean isActiveMob(UUID uuid) {
        return uuid.equals(activeMobUUID);
    }

    // ── Morte giocatore ───────────────────────────────────────────────────────

    public void handlePlayerDeath(Player player) {
        if (currentSession == null || !currentSession.isActive()) return;
        if (!currentSession.isInArena(player.getUniqueId())) return;
        currentSession.playerDied(player.getUniqueId());
    }

    // ── Morte boss ────────────────────────────────────────────────────────────

    public void handleBossDeath() {
        if (currentSession == null) return;
        currentSession.end();

        String msg = color(plugin.getConfig().getString("messages.boss-dead",
                "&6&lIL GILDED SENTINEL È CADUTO!"));
        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(msg);

        List<UUID> topDamagers = currentSession.getTopDamagersAlive();
        pendingRanks.clear();
        claimedRewards.clear();
        if (topDamagers.size() > 0) pendingRanks.put(topDamagers.get(0), 1);
        if (topDamagers.size() > 1) pendingRanks.put(topDamagers.get(1), 2);
        if (topDamagers.size() > 2) pendingRanks.put(topDamagers.get(2), 3);

        Bukkit.getScheduler().runTaskLater(plugin, () -> returnPlayers(), 40L);
        activeMobUUID = null;
    }

    private void returnPlayers() {
        if (currentSession == null) return;
        for (UUID uuid : currentSession.getAllParticipants()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            Location prev = currentSession.getPreviousLocation(uuid);
            if (prev != null) p.teleport(prev, PlayerTeleportEvent.TeleportCause.PLUGIN);
        }
        currentSession = null;
    }

    // ── Stato ─────────────────────────────────────────────────────────────────

    public boolean isBossActive() {
        return announced || (currentSession != null && currentSession.isActive());
    }

    public BossSession getCurrentSession() { return currentSession; }

    // ── NPC Ricompense ────────────────────────────────────────────────────────

    public void spawnRewardNpc(Location loc) {
        if (rewardNpcUUID != null) {
            for (World w : Bukkit.getWorlds())
                for (Entity e : w.getEntities())
                    if (e.getUniqueId().equals(rewardNpcUUID)) { e.remove(); break; }
        }

        Villager v = loc.getWorld().spawn(loc, Villager.class, villager -> {
            villager.setCustomName(color("&6&l✧ RICOMPENSE BOSS ✧"));
            villager.setCustomNameVisible(true);
            villager.setAI(false);
            villager.setInvulnerable(true);
            villager.setSilent(true);
            villager.setPersistent(true);
            villager.setProfession(Villager.Profession.CARTOGRAPHER);
            villager.setVillagerType(Villager.Type.PLAINS);
        });
        rewardNpcUUID = v.getUniqueId();
        plugin.getConfig().set("npc.uuid", rewardNpcUUID.toString());
        plugin.saveConfig();
    }

    public boolean isRewardNpc(UUID uuid) { return uuid.equals(rewardNpcUUID); }

    public boolean canClaim(UUID uuid) {
        return !claimedRewards.contains(uuid) && pendingRanks.containsKey(uuid);
    }

    public void claimRewards(Player player) {
        UUID uuid = player.getUniqueId();
        if (claimedRewards.contains(uuid)) {
            player.sendMessage(color("&cHai già ritirato le tue ricompense!"));
            return;
        }
        List<ItemStack> rewards = new ArrayList<>();
        int rank = pendingRanks.getOrDefault(uuid, 0);
        if (rank == 1)      rewards.addAll(getLootItems("primo"));
        else if (rank == 2) rewards.addAll(getLootItems("secondo"));
        else if (rank == 3) rewards.addAll(getLootItems("terzo"));
        rewards.addAll(getLootItems("tutti"));

        if (rewards.isEmpty()) {
            player.sendMessage(color("&eNessuna ricompensa disponibile."));
            return;
        }
        for (ItemStack item : rewards) {
            if (item != null)
                player.getInventory().addItem(item).forEach((slot, leftover) ->
                        player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
        claimedRewards.add(uuid);
        player.sendMessage(color("&aHai ritirato le tue ricompense!"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
    }

    @SuppressWarnings("unchecked")
    private List<ItemStack> getLootItems(String tier) {
        List<?> raw = plugin.getConfig().getList("loot.gilded." + tier);
        if (raw == null) return new ArrayList<>();
        List<ItemStack> items = new ArrayList<>();
        for (Object o : raw) if (o instanceof ItemStack is) items.add(is);
        return items;
    }

    public Map<UUID, Integer> getPendingRanks() { return pendingRanks; }
    public UUID getRewardNpcUUID() { return rewardNpcUUID; }

    // ── Location ──────────────────────────────────────────────────────────────

    public Location getArenaLocation() {
        String worldName = plugin.getConfig().getString("arena.world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world,
                plugin.getConfig().getDouble("arena.x"),
                plugin.getConfig().getDouble("arena.y"),
                plugin.getConfig().getDouble("arena.z"));
    }

    public void setArenaLocation(Location loc) {
        plugin.getConfig().set("arena.world", loc.getWorld().getName());
        plugin.getConfig().set("arena.x", loc.getX());
        plugin.getConfig().set("arena.y", loc.getY());
        plugin.getConfig().set("arena.z", loc.getZ());
        plugin.saveConfig();
    }

    public Location getPlayerSpawnLocation() {
        String worldName = plugin.getConfig().getString("player-spawn.world");
        if (worldName == null) return getArenaLocation();
        World world = Bukkit.getWorld(worldName);
        if (world == null) return getArenaLocation();
        return new Location(world,
                plugin.getConfig().getDouble("player-spawn.x"),
                plugin.getConfig().getDouble("player-spawn.y"),
                plugin.getConfig().getDouble("player-spawn.z"));
    }

    public void setPlayerSpawnLocation(Location loc) {
        plugin.getConfig().set("player-spawn.world", loc.getWorld().getName());
        plugin.getConfig().set("player-spawn.x", loc.getX());
        plugin.getConfig().set("player-spawn.y", loc.getY());
        plugin.getConfig().set("player-spawn.z", loc.getZ());
        plugin.saveConfig();
    }

    // ── Auto spawn ────────────────────────────────────────────────────────────

    public boolean shouldAutoSpawn() {
        if (isBossActive()) return false;
        int minPlayers = plugin.getConfig().getInt("boss.min-players", 5);
        if (Bukkit.getOnlinePlayers().size() < minPlayers) return false;
        long hours = plugin.getConfig().getLong("boss.auto-spawn-hours", 48);
        return System.currentTimeMillis() - lastSpawnTime >= hours * 3600_000L;
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
