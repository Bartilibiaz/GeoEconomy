package pl.bartilibiaz.market;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import pl.bartilibiaz.GeoEconomyPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AlertManager {

    private final GeoEconomyPlugin plugin;

    public AlertManager(GeoEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void addAlert(UUID uuid, Material mat, double targetPrice) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            double currentPrice = 0.0;
            boolean found = false;

            for (List<MarketItem> list : plugin.getMarketManager().getAllItemsValues()) {
                for (MarketItem item : list) {
                    if (item.getMaterial() == mat) {
                        currentPrice = item.getSellPrice();
                        found = true;
                        break;
                    }
                }
                if (found) break;
            }

            if (!found) {
                plugin.getLogger().warning("Próba ustawienia alertu dla nieistniejącego przedmiotu: " + mat);
                return;
            }

            boolean isHighAlert = targetPrice > currentPrice;

            String sql = "INSERT INTO market_alerts (uuid, material, target_price, is_high) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, mat.name());
                ps.setDouble(3, targetPrice);
                ps.setBoolean(4, isHighAlert);
                ps.executeUpdate();

                plugin.getLogger().info("Zapisano alert SQL dla " + uuid + ": " + mat + " (High: " + isHighAlert + ")");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public void checkAlerts(Material mat, double newPrice) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Integer> alertsToRemove = new ArrayList<>();

            String sql = "SELECT id, uuid, target_price, is_high FROM market_alerts WHERE material = ?";

            try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
                ps.setString(1, mat.name());
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    int id = rs.getInt("id");
                    String uuidStr = rs.getString("uuid");
                    double targetPrice = rs.getDouble("target_price");
                    boolean isHighAlert = rs.getBoolean("is_high");
                    UUID uuid = UUID.fromString(uuidStr);

                    boolean triggered = false;

                    if (isHighAlert) {
                        if (newPrice >= targetPrice) triggered = true;
                    } else {
                        if (newPrice <= targetPrice) triggered = true;
                    }

                    if (triggered) {
                        if (plugin.getDiscordManager().isLinked(uuid)) {
                            String arrow = isHighAlert ? "📈 **Wzrost!**" : "📉 **Okazja!**";

                            String msg = arrow + " **ALERT CENOWY!**\n" +
                                    "Przedmiot: **" + mat.name() + "**\n" +
                                    "Aktualna cena: **" + String.format("%.2f", newPrice) + "$**\n" +
                                    "Twój cel: " + String.format("%.2f", targetPrice) + "$";

                            plugin.getDiscordManager().sendPrivateMessage(uuid, msg);
                        }

                        alertsToRemove.add(id);
                    }
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }

            if (!alertsToRemove.isEmpty()) {
                deleteAlerts(alertsToRemove);
            }
        });
    }

    private void deleteAlerts(List<Integer> ids) {
        if (ids.isEmpty()) return;

        StringBuilder sb = new StringBuilder("DELETE FROM market_alerts WHERE id IN (");
        for (int i = 0; i < ids.size(); i++) {
            sb.append(ids.get(i));
            if (i < ids.size() - 1) sb.append(",");
        }
        sb.append(")");

        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sb.toString())) {
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}