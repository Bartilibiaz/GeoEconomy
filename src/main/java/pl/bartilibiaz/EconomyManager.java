package pl.bartilibiaz;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.UUID;
import java.sql.ResultSet;


public class EconomyManager {

    private final GeoEconomyPlugin plugin;
    private final HashMap<UUID, Double> accounts = new HashMap<>();
    private final double startingBalance = 100.0;

    public EconomyManager(GeoEconomyPlugin plugin) {
        this.plugin = plugin;
        loadData(); // Wczytaj przy starcie
    }

    public boolean hasAccount(UUID uuid) {
        String sql = "SELECT balance FROM economy WHERE uuid = ?";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            return ps.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void createAccount(UUID uuid) {
        if (hasAccount(uuid)) return;
        double startBalance = plugin.getConfig().getDouble("economy.starting_balance", 100.0);

        String sql = "INSERT INTO economy (uuid, balance) VALUES (?, ?)";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setDouble(2, startBalance);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public double getBalance(UUID uuid) {
        String sql = "SELECT balance FROM economy WHERE uuid = ?";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("balance");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    public void setBalance(UUID uuid, double amount) {
        if (!hasAccount(uuid)) createAccount(uuid);
        String sql = "UPDATE economy SET balance = ? WHERE uuid = ?";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deposit(UUID uuid, double amount) {
        setBalance(uuid, getBalance(uuid) + amount);
    }

    public boolean withdraw(UUID uuid, double amount) {
        double current = getBalance(uuid);
        if (current >= amount) {
            setBalance(uuid, current - amount);
            return true;
        }
        return false;
    }

    // --- SYSTEM ZAPISU (Persistence) ---

    public void saveData() {
        File file = new File(plugin.getDataFolder(), "balances.yml");
        YamlConfiguration config = new YamlConfiguration();

        for (UUID uuid : accounts.keySet()) {
            config.set(uuid.toString(), accounts.get(uuid));
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Nie udalo sie zapisac balances.yml!");
            e.printStackTrace();
        }
    }

    public void loadData() {
        File file = new File(plugin.getDataFolder(), "balances.yml");
        if (!file.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        accounts.clear();

        for (String key : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                double balance = config.getDouble(key);
                accounts.put(uuid, balance);
            } catch (IllegalArgumentException e) {
                // Ignoruj błędne UUID
            }
        }
        plugin.getLogger().info("Wczytano konta " + accounts.size() + " graczy.");
    }
}