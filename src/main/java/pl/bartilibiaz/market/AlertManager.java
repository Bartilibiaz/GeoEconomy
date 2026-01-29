package pl.bartilibiaz.market;

import org.bukkit.Material;
import pl.bartilibiaz.GeoEconomyPlugin;

import java.util.*;

public class AlertManager {

    private final GeoEconomyPlugin plugin;
    // Klucz: Material -> Lista Alertów
    private final Map<Material, List<PriceAlert>> alerts = new HashMap<>();

    public static class PriceAlert {
        public UUID playerUUID;
        public double targetPrice;
        public boolean isHighAlert; // true = czekamy na wzrost, false = czekamy na spadek

        public PriceAlert(UUID uuid, double target, boolean isHighAlert) {
            this.playerUUID = uuid;
            this.targetPrice = target;
            this.isHighAlert = isHighAlert;
        }
    }

    public AlertManager(GeoEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    // Dodawanie alertu - określamy kierunek (W górę czy w dół)
    public void addAlert(UUID uuid, Material mat, double targetPrice) {
        // Musimy pobrać aktualną cenę, żeby wiedzieć w którą stronę czekamy
        double currentPrice = 0.0;

        // Szukamy itemu w managerze, żeby znać cenę startową
        for (List<MarketItem> list : plugin.getMarketManager().getAllItemsValues()) {
            for (MarketItem item : list) {
                if (item.getMaterial() == mat) {
                    currentPrice = item.getBuyPrice();
                    break;
                }
            }
        }

        // Jeśli target > current -> czekamy aż urośnie (HighAlert)
        // Jeśli target < current -> czekamy aż spadnie (LowAlert)
        boolean isHigh = targetPrice > currentPrice;

        alerts.computeIfAbsent(mat, k -> new ArrayList<>()).add(new PriceAlert(uuid, targetPrice, isHigh));
    }

    public void checkAlerts(Material mat, double newPrice) {
        List<PriceAlert> list = alerts.get(mat);
        if (list == null) return;

        Iterator<PriceAlert> it = list.iterator();
        while (it.hasNext()) {
            PriceAlert alert = it.next();
            boolean triggered = false;

            // Logika sprawdzania
            if (alert.isHighAlert) {
                // Czekaliśmy na wzrost. Czy cena przebiła sufit?
                if (newPrice >= alert.targetPrice) triggered = true;
            } else {
                // Czekaliśmy na spadek. Czy cena przebiła podłogę?
                if (newPrice <= alert.targetPrice) triggered = true;
            }

            if (triggered) {
                if (plugin.getDiscordManager().isLinked(alert.playerUUID)) {
                    String arrow = alert.isHighAlert ? "📈 **Wzrost!**" : "📉 **Okazja!**";

                    String msg = arrow + " **ALERT CENOWY!**\n" +
                            "Przedmiot: **" + mat.name() + "**\n" +
                            "Aktualna cena: **" + String.format("%.2f", newPrice) + "$**\n" +
                            "Twój cel: " + String.format("%.2f", alert.targetPrice) + "$";

                    plugin.getDiscordManager().sendPrivateMessage(alert.playerUUID, msg);
                }
                it.remove(); // Usuwamy alert po wykonaniu
            }
        }
    }
}