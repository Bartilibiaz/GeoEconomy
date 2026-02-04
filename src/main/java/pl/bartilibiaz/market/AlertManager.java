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

    // Ta metoda jest wywoływana, gdy gracz wpisze /alert na Discordzie
    public void addAlert(UUID uuid, Material mat, double targetPrice) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            double currentPrice = 0.0;
            boolean found = false;

            // 1. Pobieramy aktualną cenę (Twoja logika)
            // Dzięki temu wiemy, czy gracz czeka na WZROST czy SPADEK
            for (List<MarketItem> list : plugin.getMarketManager().getAllItemsValues()) {
                for (MarketItem item : list) {
                    if (item.getMaterial() == mat) {
                        currentPrice = item.getSellPrice(); // lub getBuyPrice() zależnie co wolisz
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

            // Twoja "fajna" logika:
            // Jeśli cel (150) > obecna (100) -> To czekamy na WZROST (isHigh = true)
            // Jeśli cel (50) < obecna (100) -> To czekamy na SPADEK (isHigh = false)
            boolean isHighAlert = targetPrice > currentPrice;

            // 2. Zapisujemy do BAZY DANYCH (zamiast do mapy)
            String sql = "INSERT INTO market_alerts (uuid, material, target_price, is_high) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, mat.name());
                ps.setDouble(3, targetPrice);
                ps.setBoolean(4, isHighAlert);
                ps.executeUpdate();

                // Opcjonalnie wyślij potwierdzenie na konsolę
                plugin.getLogger().info("Zapisano alert SQL dla " + uuid + ": " + mat + " (High: " + isHighAlert + ")");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    // Ta metoda jest wywoływana przy każdej zmianie ceny w MarketManagerze
    public void checkAlerts(Material mat, double newPrice) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Integer> alertsToRemove = new ArrayList<>();

            // Pobieramy z bazy tylko alerty dotyczące tego materiału
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

                    // Twoja logika sprawdzania warunków (zachowana!)
                    if (isHighAlert) {
                        // Czekamy na wzrost: Czy nowa cena przebiła cel w górę?
                        if (newPrice >= targetPrice) triggered = true;
                    } else {
                        // Czekamy na spadek: Czy nowa cena spadła poniżej celu?
                        if (newPrice <= targetPrice) triggered = true;
                    }

                    if (triggered) {
                        // Sprawdzamy czy konto połączone i wysyłamy Twoją ładną wiadomość
                        if (plugin.getDiscordManager().isLinked(uuid)) {
                            String arrow = isHighAlert ? "📈 **Wzrost!**" : "📉 **Okazja!**";

                            String msg = arrow + " **ALERT CENOWY!**\n" +
                                    "Przedmiot: **" + mat.name() + "**\n" +
                                    "Aktualna cena: **" + String.format("%.2f", newPrice) + "$**\n" +
                                    "Twój cel: " + String.format("%.2f", targetPrice) + "$";

                            plugin.getDiscordManager().sendPrivateMessage(uuid, msg);
                        }

                        // Dodajemy ID do usunięcia (żeby alert nie wyskakiwał w kółko)
                        alertsToRemove.add(id);
                    }
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }

            // Usuwamy spełnione alerty z bazy
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