package pl.bartilibiaz;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pl.bartilibiaz.database.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EconomyManager implements Listener {

    private final GeoEconomyPlugin plugin;
    private final Map<UUID, Double> balanceCache = new ConcurrentHashMap<>();

    public EconomyManager(GeoEconomyPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void loadAccount(UUID uuid) {
        String sql = "SELECT balance FROM economy WHERE uuid = ?";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                balanceCache.put(uuid, rs.getDouble("balance"));
            } else {
                createAccount(uuid);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void saveAccount(UUID uuid) {
        if (!balanceCache.containsKey(uuid)) return;
        double balance = balanceCache.get(uuid);

        String sql = "UPDATE economy SET balance = ? WHERE uuid = ?";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setDouble(1, balance);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        balanceCache.remove(uuid);
    }

    public void saveAll() {
        for (UUID uuid : new HashSet<>(balanceCache.keySet())) {
            saveAccount(uuid);
        }
    }

    public boolean hasAccount(UUID uuid) {
        return balanceCache.containsKey(uuid);
    }

    public void createAccount(UUID uuid) {
        double startBalance = plugin.getConfig().getDouble("economy.starting_balance", 100.0);
        balanceCache.put(uuid, startBalance);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO economy (uuid, balance) VALUES (?, ?)";
            try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setDouble(2, startBalance);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public double getBalance(UUID uuid) {
        return balanceCache.getOrDefault(uuid, 0.0);
    }

    public void setBalance(UUID uuid, double amount) {
        balanceCache.put(uuid, amount);
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

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            loadAccount(e.getPlayer().getUniqueId());
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            saveAccount(e.getPlayer().getUniqueId());
        });
    }
}