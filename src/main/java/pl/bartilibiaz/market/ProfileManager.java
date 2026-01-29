package pl.bartilibiaz.market;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pl.bartilibiaz.GeoEconomyPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ProfileManager implements Listener {

    private final GeoEconomyPlugin plugin;
    private final Map<UUID, PlayerProfile> loadedProfiles = new HashMap<>();

    public ProfileManager(GeoEconomyPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // Start Auto-Save Task (co 5 minut)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveAllProfiles, 6000L, 6000L);
    }

    public PlayerProfile getProfile(UUID uuid) {
        // Jeśli profil nie jest załadowany (np. reload pluginu przy graczach online), spróbuj go załadować
        if (!loadedProfiles.containsKey(uuid)) {
            loadProfileFromDb(uuid);
        }
        return loadedProfiles.get(uuid);
    }

    // --- ŁADOWANIE (SQL -> RAM) ---
    private void loadProfileFromDb(UUID uuid) {
        PlayerProfile profile = new PlayerProfile(uuid);
        String sql = "SELECT material, amount FROM portfolios WHERE uuid = ?";

        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String matName = rs.getString("material");
                int amount = rs.getInt("amount");
                try {
                    profile.setItem(Material.valueOf(matName), amount);
                } catch (IllegalArgumentException ignored) {} // Ignoruj błędne materiały
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        loadedProfiles.put(uuid, profile);
    }

    // --- ZAPISYWANIE (RAM -> SQL) ---
    public void saveProfileToDb(UUID uuid) {
        PlayerProfile profile = loadedProfiles.get(uuid);
        if (profile == null) return;

        try {
            // Używamy transakcji dla szybkości
            plugin.getDatabaseManager().getConnection().setAutoCommit(false);

            // 1. Usuwamy stare wpisy tego gracza (najprostsza metoda aktualizacji)
            String delSql = "DELETE FROM portfolios WHERE uuid = ?";
            try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(delSql)) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            }

            // 2. Wstawiamy nowe
            String insSql = "INSERT INTO portfolios (uuid, material, amount) VALUES (?, ?, ?)";
            try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(insSql)) {
                for (Map.Entry<Material, Integer> entry : profile.getWallet().entrySet()) {
                    if (entry.getValue() <= 0) continue; // Nie zapisujemy zer
                    ps.setString(1, uuid.toString());
                    ps.setString(2, entry.getKey().name());
                    ps.setInt(3, entry.getValue());
                    ps.addBatch(); // Batchowanie zapytań
                }
                ps.executeBatch();
            }

            plugin.getDatabaseManager().getConnection().commit();
            plugin.getDatabaseManager().getConnection().setAutoCommit(true);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void saveAllProfiles() {
        for (UUID uuid : loadedProfiles.keySet()) {
            saveProfileToDb(uuid);
        }
    }

    // --- LISTENERY ---
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // Ładujemy asynchronicznie, żeby nie lagować wejścia
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> loadProfileFromDb(e.getPlayer().getUniqueId()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // Zapisujemy i usuwamy z RAMu
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            saveProfileToDb(e.getPlayer().getUniqueId());
            loadedProfiles.remove(e.getPlayer().getUniqueId());
        });
    }

    // Metoda wywoływana przy wyłączaniu serwera
    public void onDisable() {
        saveAllProfiles();
    }
}