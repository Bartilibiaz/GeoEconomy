package pl.bartilibiaz.market;

import org.bukkit.Material;
import pl.bartilibiaz.GeoEconomyPlugin;

import java.util.*;

public class AlertManager {

    private final GeoEconomyPlugin plugin;
    private final Map<Material, List<PriceAlert>> alerts = new HashMap<>();

    public static class PriceAlert {
        public UUID playerUUID;
        public double targetPrice;
        public boolean isHighAlert;

        public PriceAlert(UUID uuid, double target, boolean isHighAlert) {
            this.playerUUID = uuid;
            this.targetPrice = target;
            this.isHighAlert = isHighAlert;
        }
    }

    public AlertManager(GeoEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void addAlert(UUID uuid, Material mat, double targetPrice) {
        double currentPrice = 0.0;

        for (List<MarketItem> list : plugin.getMarketManager().getAllItemsValues()) {
            for (MarketItem item : list) {
                if (item.getMaterial() == mat) {
                    currentPrice = item.getBuyPrice();
                    break;
                }
            }
        }

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

            if (alert.isHighAlert) {
                if (newPrice >= alert.targetPrice) triggered = true;
            } else {
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
                it.remove();
            }
        }
    }
}