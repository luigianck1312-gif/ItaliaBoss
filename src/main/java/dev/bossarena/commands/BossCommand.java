package dev.bossarena.commands;

import dev.bossarena.BossArena;
import dev.bossarena.managers.BossManager;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BossCommand implements CommandExecutor, TabCompleter {

    private final BossArena plugin;
    private final BossManager bossManager;

    private static final List<String> ADMIN_SUBS = List.of("spawn", "loot", "npc", "setarena");
    private static final List<String> USER_SUBS  = List.of("fight");
    private static final List<String> LOOT_TIERS = List.of("primo", "secondo", "terzo", "tutti");

    public BossCommand(BossArena plugin) {
        this.plugin      = plugin;
        this.bossManager = plugin.getBossManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo i giocatori possono usare questo comando.");
            return true;
        }

        if (args.length == 0) { sendHelp(player); return true; }

        switch (args[0].toLowerCase()) {
            case "spawn"    -> handleSpawn(player, args);
            case "fight"    -> handleFight(player);
            case "loot"     -> handleLoot(player, args);
            case "npc"      -> handleNpc(player);
            case "setarena" -> handleSetArena(player);
            default         -> sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player p) {
        p.sendMessage(BossManager.color("&6=== BossArena ==="));
        p.sendMessage(BossManager.color("&e/boss fight &7- Entra nell'arena del boss attivo"));
        if (p.hasPermission("bossarena.admin")) {
            p.sendMessage(BossManager.color("&e/boss spawn <nome> &7- Spawna il boss nell'arena"));
            p.sendMessage(BossManager.color("&e/boss loot <nome> <primo|secondo|terzo|tutti> &7- Modifica loot"));
            p.sendMessage(BossManager.color("&e/boss npc &7- Piazza il villager ricompense dove sei"));
            p.sendMessage(BossManager.color("&e/boss setarena &7- Imposta spawn arena dove sei"));
        }
    }

    private void handleSpawn(Player player, String[] args) {
        if (!player.hasPermission("bossarena.admin")) {
            player.sendMessage(BossManager.color("&cNon hai il permesso!"));
            return;
        }
        if (bossManager.isBossActive()) {
            player.sendMessage(BossManager.color("&cC'e' gia' un boss attivo!"));
            return;
        }
        if (bossManager.getArenaLocation() == null) {
            player.sendMessage(BossManager.color(plugin.getConfig()
                    .getString("messages.no-arena-set", "&cArena non configurata! Usa /boss setarena")));
            return;
        }
        bossManager.announceAndScheduleSpawn();
        player.sendMessage(BossManager.color("&aAnnuncio inviato! Il boss spawnerà tra " +
                plugin.getConfig().getInt("boss.spawn-delay-seconds", 20) + " secondi."));
    }

    private void handleFight(Player player) {
        if (!bossManager.isBossActive()) {
            player.sendMessage(BossManager.color(plugin.getConfig()
                    .getString("messages.fight-no-boss", "&cNessun boss attivo!")));
            return;
        }
        if (bossManager.getCurrentSession().isDead(player.getUniqueId())) {
            player.sendMessage(BossManager.color(plugin.getConfig()
                    .getString("messages.fight-already-dead", "&cSei morto, non puoi rientrare!")));
            return;
        }
        if (bossManager.getCurrentSession().isInArena(player.getUniqueId())) {
            player.sendMessage(BossManager.color(plugin.getConfig()
                    .getString("messages.fight-already-in", "&cSei gia' nell'arena!")));
            return;
        }
        boolean ok = bossManager.joinFight(player);
        if (ok) player.sendMessage(BossManager.color(plugin.getConfig()
                .getString("messages.fight-join", "&aEntrando nell'arena!")));
    }

    private void handleLoot(Player player, String[] args) {
        if (!player.hasPermission("bossarena.admin")) {
            player.sendMessage(BossManager.color("&cNon hai il permesso!"));
            return;
        }
        if (args.length < 3) {
            player.sendMessage(BossManager.color("&cUso: /boss loot <nome_boss> <primo|secondo|terzo|tutti>"));
            return;
        }
        String tier = args[2].toLowerCase();
        if (!LOOT_TIERS.contains(tier)) {
            player.sendMessage(BossManager.color("&cTier non valido! Usa: primo, secondo, terzo, tutti"));
            return;
        }
        plugin.getLootEditorGui().open(player, tier);
    }

    private void handleNpc(Player player) {
        if (!player.hasPermission("bossarena.admin")) {
            player.sendMessage(BossManager.color("&cNon hai il permesso!"));
            return;
        }
        bossManager.spawnRewardNpc(player.getLocation());
        player.sendMessage(BossManager.color(plugin.getConfig()
                .getString("messages.npc-placed", "&aVillager ricompense piazzato!")));
    }

    private void handleSetArena(Player player) {
        if (!player.hasPermission("bossarena.admin")) {
            player.sendMessage(BossManager.color("&cNon hai il permesso!"));
            return;
        }
        bossManager.setArenaLocation(player.getLocation());
        player.sendMessage(BossManager.color(plugin.getConfig()
                .getString("messages.arena-set", "&aArena impostata!")));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            List<String> subs = new java.util.ArrayList<>(USER_SUBS);
            if (sender.hasPermission("bossarena.admin")) subs.addAll(ADMIN_SUBS);
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("loot"))
            return LOOT_TIERS.stream().filter(t -> t.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
        return List.of();
    }
}
