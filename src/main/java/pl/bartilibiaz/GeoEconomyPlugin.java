package pl.bartilibiaz;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import pl.bartilibiaz.commands.MarketCommand;
import pl.bartilibiaz.gui.ProfileGUI;
import pl.bartilibiaz.hooks.VaultHook;
import pl.bartilibiaz.market.MarketManager;
import pl.bartilibiaz.gui.MarketGUI;
import pl.bartilibiaz.market.ProfileManager;
import pl.bartilibiaz.discord.DiscordManager;
import pl.bartilibiaz.market.AlertManager;
import pl.bartilibiaz.web.WebManager;
import pl.bartilibiaz.utils.LanguageManager;
import pl.bartilibiaz.database.DatabaseManager;
import pl.bartilibiaz.utils.UpdateManager;

import java.io.File;

public class GeoEconomyPlugin extends JavaPlugin {

    private EconomyManager economyManager;
    private MarketManager marketManager;
    private VaultHook vaultHook;
    private ProfileManager profileManager;
    private DiscordManager discordManager;
    private AlertManager alertManager;
    private WebManager webManager;
    private LanguageManager languageManager;
    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        this.economyManager = new EconomyManager(this);
        this.marketManager = new MarketManager(this);
        this.profileManager = new ProfileManager(this);
        this.alertManager = new AlertManager(this);
        this.languageManager = new LanguageManager(this);
        this.databaseManager = new DatabaseManager(this);
        saveDefaultConfig();

        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            this.vaultHook = new VaultHook(this);
            getServer().getServicesManager().register(Economy.class, this.vaultHook, this, ServicePriority.Highest);
            getLogger().info("Zarejestrowano ekonomię w Vault!");
        } else {
            getLogger().severe("Brak pluginu Vault! Ekonomia nie będzie działać z innymi pluginami.");
        }

        getServer().getPluginManager().registerEvents(new MarketGUI(this), this);
        getServer().getPluginManager().registerEvents(new ProfileGUI(this), this);
        if (getCommand("market") != null) {
            getCommand("market").setExecutor(new MarketCommand(this));
        }

        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            this.discordManager = new DiscordManager(this);
        });

        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            economyManager.saveAll();
        }, 6000L, 6000L);

        this.webManager = new WebManager(this);
        this.webManager.start();

        getServer().getScheduler().runTaskLaterAsynchronously(this, () -> {
            new UpdateManager(this).checkAndDownload();
        }, 100L);
    }

    @Override
    public void onDisable() {
        if (economyManager != null) {
            economyManager.saveAll();
        }
        if (marketManager != null) {
            marketManager.saveMarket();
        }
        if (webManager != null) webManager.stop();
        if (discordManager != null) discordManager.stopBot();
        if (profileManager != null) profileManager.onDisable();
        if (databaseManager != null) databaseManager.close();

        getLogger().info("Plugin GeoEconomy został wyłączony.");
    }

    public LanguageManager getLang() { return languageManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public ProfileManager getProfileManager() { return profileManager; }
    public DiscordManager getDiscordManager() { return discordManager; }
    public AlertManager getAlertManager() { return alertManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public MarketManager getMarketManager() { return marketManager; }

    public File getPluginFile() {
        return getFile();
    }
}