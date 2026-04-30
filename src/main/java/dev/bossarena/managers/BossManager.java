package dev.bossarena.managers;

import dev.bossarena.BossArena;
import dev.bossarena.models.BossSession;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.BukkitAPIHelper;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.logging.Level;

public class BossManager {

    private final BossArena plugin;
    private BossSession currentSession = null;
    private UUID activeMobUUID = null;

    // UUID villager NPC ricompense -> set di UUID giocatori che possono ritirare
    private final Map<UUID, Set<UUID>> npcRewardMap = new HashMap<>();
    // UUID villager NPC
    private UUID rewardNpcUUID = null;
    // chi ha già ritirato
    private final Set<UUID> claimedRewards = new HashSet<>();
    // premi pendenti per giocatore: uuid -> posizione classifica
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

    private boolean spawnBossInternal() {
        Location arenaLoc = getArenaLocation();
        if (arenaLoc == null) {
            plugin.getLogger().warning("Arena non configurata! Usa /boss setarena");
            return false;
        }

        String mythicName = plugin.getConfig().getString("boss.mythic-name", "gilded_sentinel");

        try {
            String cmd = "mm mobs spawn " + mythicName + " 1 " +
                    arenaLoc.getWorld().getName() + "," +
                    arenaLoc.getBlockX() + "," +
                    arenaLoc.getBlockY() + "," +
                    arenaLoc.getBlockZ() + ",0,0";

            plugin.getLogger().info("[BossArena] Spawn command: " + cmd);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);

            if (currentSession == null)
                currentSession = new BossSession(mythicName);
            currentSession.start();
            lastSpawnTime = System.currentTimeMillis();

            // Cerca UUID del mob dopo 20 tick
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (org.bukkit.entity.Entity e : arenaLoc.getWorld().getNearbyEntities(arenaLoc, 10, 10, 10)) {
                    try {
                        if (io.lumine.mythic.bukkit.MythicBukkit.inst().getMobManager().isActiveMob(e.getUniqueId())) {
                            activeMobUUID = e.getUniqueId();
                            plugin.getLogger().info("[BossArena] Boss UUID trovato: " + activeMobUUID);
                            break;
                        }
                    } catch (Exception ex) {
                        plugin.getLogger().warning("Errore ricerca UUID boss: " + ex.getMessage());
                    }
                }
            }, 20L);

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Errore spawn boss", e);
            return false;
        }
    }

    private boolean announced = false;

    public void announceAndScheduleSpawn() {
        int delay = plugin.getConfig().getInt("boss.spawn-delay-seconds", 20);
        String msg = color(plugin.getConfig().getString("messages.announce", "&cIL BOSS ARRIVA!")
                .replace("%seconds%", String.valueOf(delay)));

        announced = true;
        currentSession = new BossSession(plugin.getConfig().getString("boss.mythic-name", "GildedSentinel"));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(msg);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.8f);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            announced = false;
            boolean ok = spawnBossInternal();
            if (!ok) {
                currentSession = null;
                plugin.getLogger().warning("Spawn automatico boss fallito!");
            }
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
        if (currentSession != null && currentSession.isActive()) {
            currentSession.addDamage(playerUUID, amount);
        }
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

        // Messaggio vittoria
        String msg = color(plugin.getConfig().getString("messages.boss-dead",
                "&6IL BOSS E' CADUTO!"));
        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(msg);

        // Calcola premi
        List<UUID> topDamagers = currentSession.getTopDamagersAlive();
        Set<UUID> survivors    = currentSession.getSurvivors();

        pendingRanks.clear();
        claimedRewards.clear();

        if (topDamagers.size() > 0) pendingRanks.put(topDamagers.get(0), 1);
        if (topDamagers.size() > 1) pendingRanks.put(topDamagers.get(1), 2);
        if (topDamagers.size() > 2) pendingRanks.put(topDamagers.get(2), 3);

        // Ritorna tutti nell'arena alle posizioni precedenti
        Bukkit.getScheduler().runTaskLater(plugin, () -> returnPlayers(), 40L);

        activeMobUUID = null;
    }

    private void returnPlayers() {
        if (currentSession == null) return;
        for (UUID uuid : currentSession.getAllParticipants()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            Location prev = currentSession.getPreviousLocation(uuid);
            if (prev != null) p.teleport(prev);
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
        // Rimuovi vecchio
        if (rewardNpcUUID != null) {
            for (World w : Bukkit.getWorlds())
                for (Entity e : w.getEntities())
                    if (e.getUniqueId().equals(rewardNpcUUID)) { e.remove(); break; }
        }

        Villager v = loc.getWorld().spawn(loc, Villager.class, villager -> {
            villager.setCustomName(color("&6&l\u2727 RICOMPENSE BOSS \u2727"));
            villager.setCustomNameVisible(true);
            villager.setAI(false);
            villager.setInvulnerable(true);
            villager.setSilent(true);
            villager.setPersistent(true);
            villager.setProfession(Villager.Profession.CARTOGRAPHER);
            villager.setVillagerType(Villager.Type.PLAINS);
        });
        rewardNpcUUID = v.getUniqueId();

        // Salva UUID nel config
        plugin.getConfig().set("npc.uuid", rewardNpcUUID.toString());
        plugin.getConfig().set("npc.world", loc.getWorld().getName());
        plugin.getConfig().set("npc.x", loc.getX());
        plugin.getConfig().set("npc.y", loc.getY());
        plugin.getConfig().set("npc.z", loc.getZ());
        plugin.saveConfig();
    }

    public boolean isRewardNpc(UUID uuid) {
        return uuid.equals(rewardNpcUUID);
    }

    public boolean canClaim(UUID uuid) {
        if (claimedRewards.contains(uuid)) return false;
        // Deve essere sopravvissuto
        return pendingRanks.containsKey(uuid) ||
               (currentSession == null && isEligibleSurvivor(uuid));
    }

    private boolean isEligibleSurvivor(UUID uuid) {
        // Controllato via pendingRanks e survivors già calcolati
        return pendingRanks.containsKey(uuid);
    }

    public void claimRewards(Player player) {
        UUID uuid = player.getUniqueId();
        if (claimedRewards.contains(uuid)) {
            player.sendMessage(color("&cHai gia' ritirato le tue ricompense!"));
            return;
        }

        List<ItemStack> rewards = new ArrayList<>();
        int rank = pendingRanks.getOrDefault(uuid, 0);

        // Loot per posizione
        if (rank == 1) rewards.addAll(getLootItems("primo"));
        else if (rank == 2) rewards.addAll(getLootItems("secondo"));
        else if (rank == 3) rewards.addAll(getLootItems("terzo"));

        // Loot per tutti i partecipanti vivi
        // Verifica se il giocatore era un sopravvissuto (non morto)
        rewards.addAll(getLootItems("tutti"));

        if (rewards.isEmpty()) {
            player.sendMessage(color("&eNessuna ricompensa disponibile per te."));
            return;
        }

        for (ItemStack item : rewards) {
            if (item != null) {
                player.getInventory().addItem(item).forEach((slot, leftover) ->
                        player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            }
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
        for (Object o : raw) {
            if (o instanceof ItemStack is) items.add(is);
        }
        return items;
    }

    public Map<UUID, Integer> getPendingRanks() { return pendingRanks; }
    public UUID getRewardNpcUUID() { return rewardNpcUUID; }

    // ── Arena ─────────────────────────────────────────────────────────────────

    public Location getArenaLocation() {
        String worldName = plugin.getConfig().getString("arena.world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        double x = plugin.getConfig().getDouble("arena.x");
        double y = plugin.getConfig().getDouble("arena.y");
        double z = plugin.getConfig().getDouble("arena.z");
        return new Location(world, x, y, z);
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
        if (worldName == null) return getArenaLocation(); // fallback
        World world = Bukkit.getWorld(worldName);
        if (world == null) return getArenaLocation();
        double x = plugin.getConfig().getDouble("player-spawn.x");
        double y = plugin.getConfig().getDouble("player-spawn.y");
        double z = plugin.getConfig().getDouble("player-spawn.z");
        return new Location(world, x, y, z);
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
        long elapsed = System.currentTimeMillis() - lastSpawnTime;
        return elapsed >= hours * 3600_000L;
    }

    public long getLastSpawnTime() { return lastSpawnTime; }

    // ── Util ──────────────────────────────────────────────────────────────────

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
