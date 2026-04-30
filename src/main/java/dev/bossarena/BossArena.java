package dev.bossarena;

import dev.bossarena.commands.BossCommand;
import dev.bossarena.gui.LootEditorGui;
import dev.bossarena.gui.RewardClaimGui;
import dev.bossarena.listeners.BossListener;
import dev.bossarena.listeners.GuiListener;
import dev.bossarena.managers.BossManager;
import org.bukkit.plugin.java.JavaPlugin;

public class BossArena extends JavaPlugin {

    private BossManager    bossManager;
    private LootEditorGui  lootEditorGui;
    private RewardClaimGui rewardClaimGui;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.bossManager    = new BossManager(this);
        this.lootEditorGui  = new LootEditorGui(this);
        this.rewardClaimGui = new RewardClaimGui(this);

        // Comandi
        var cmd = new BossCommand(this);
        getCommand("boss").setExecutor(cmd);
        getCommand("boss").setTabCompleter(cmd);

        // Listener
        getServer().getPluginManager().registerEvents(new BossListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);

        // Auto spawn check ogni 10 minuti
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (bossManager.shouldAutoSpawn()) {
                bossManager.announceAndScheduleSpawn();
            }
        }, 20L * 60 * 10, 20L * 60 * 10);

        getLogger().info("BossArena v" + getDescription().getVersion() + " abilitato!");
    }

    @Override
    public void onDisable() {
        getLogger().info("BossArena disabilitato.");
    }

    public BossManager    getBossManager()    { return bossManager; }
    public LootEditorGui  getLootEditorGui()  { return lootEditorGui; }
    public RewardClaimGui getRewardClaimGui() { return rewardClaimGui; }
}
