package dev.bossarena.models;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;

public class BossSession {

    private final String bossName;
    private boolean active = false;
    private boolean ended  = false;

    private final Map<UUID, Double>   damageMap         = new HashMap<>();
    private final Map<UUID, Location> previousLocations = new HashMap<>();
    private final Set<UUID>           deadPlayers       = new HashSet<>();
    private final Set<UUID>           activePlayers     = new HashSet<>();

    public BossSession(String bossName) { this.bossName = bossName; }

    public void start() { this.active = true; }
    public void end()   { this.active = false; this.ended = true; }

    public boolean isActive()   { return active; }
    public boolean hasEnded()   { return ended; }
    public String getBossName() { return bossName; }

    public void addDamage(UUID uuid, double amount) {
        damageMap.merge(uuid, amount, Double::sum);
    }

    public double getDamage(UUID uuid) {
        return damageMap.getOrDefault(uuid, 0.0);
    }

    public void joinPlayer(Player player) {
        previousLocations.put(player.getUniqueId(), player.getLocation().clone());
        activePlayers.add(player.getUniqueId());
    }

    public void playerDied(UUID uuid) {
        deadPlayers.add(uuid);
        activePlayers.remove(uuid);
    }

    public boolean isDead(UUID uuid)    { return deadPlayers.contains(uuid); }
    public boolean isInArena(UUID uuid) { return activePlayers.contains(uuid); }

    public Location getPreviousLocation(UUID uuid) { return previousLocations.get(uuid); }

    public Set<UUID> getActivePlayers()   { return Collections.unmodifiableSet(activePlayers); }
    public Set<UUID> getAllParticipants()  { return Collections.unmodifiableSet(previousLocations.keySet()); }

    public List<UUID> getTopDamagersAlive() {
        return damageMap.entrySet().stream()
                .filter(e -> !deadPlayers.contains(e.getKey()))
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();
    }

    public Set<UUID> getSurvivors() {
        Set<UUID> s = new HashSet<>(previousLocations.keySet());
        s.removeAll(deadPlayers);
        return s;
    }
}
