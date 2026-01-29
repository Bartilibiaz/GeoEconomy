package pl.bartilibiaz.market;

import org.bukkit.Material;
import java.util.LinkedList;
import java.util.List;

public class MarketItem {
    private final Material material;
    private double price;
    private final double changePercent;
    private final double sellRatio;

    // Lista cen historycznych.
    // Ostatni element = cena sprzed 1h.
    // Pierwszy element = cena sprzed 24h.
    private final LinkedList<Double> priceHistory = new LinkedList<>();

    public MarketItem(Material material, double startPrice, double changePercent, double sellRatio) {
        this.material = material;
        this.price = startPrice;
        this.changePercent = changePercent;
        this.sellRatio = sellRatio;
    }

    // --- LOGIKA HISTORII ---

    public void addHistoryPoint() {
        // Dodajemy obecną cenę na KONIEC listy
        priceHistory.add(price);
        // Jeśli lista jest za długa (powyżej 24h), usuwamy najstarszy wpis (początek listy)
        if (priceHistory.size() > 24) {
            priceHistory.removeFirst();
        }
    }

    // Cena sprzed 24 godzin (lub najstarsza znana)
    public double getPrice24hAgo() {
        if (priceHistory.isEmpty()) return price;
        return priceHistory.getFirst(); // Pierwszy element = najstarszy
    }

    // Cena sprzed 1 godziny
    public double getPrice1hAgo() {
        if (priceHistory.isEmpty()) return price;
        return priceHistory.getLast(); // Ostatni element = dodany godzinę temu
    }

    // Gettery do obliczeń
    public double getChangeValue(boolean mode1h) {
        double oldPrice = mode1h ? getPrice1hAgo() : getPrice24hAgo();
        return price - oldPrice;
    }

    public double getChangePercent(boolean mode1h) {
        double oldPrice = mode1h ? getPrice1hAgo() : getPrice24hAgo();
        if (oldPrice == 0) return 0.0;
        return ((price - oldPrice) / oldPrice) * 100.0;
    }

    // --- RESZTA KLASY (Gettery, Settery, Transakcje) ---

    public Material getMaterial() { return material; }
    public double getBuyPrice() { return price; }
    public double getSellPrice() { return price * sellRatio; }
    public double getSellRatio() { return sellRatio; }
    public double getBasePrice() { return price; } // Cena surowa do zapisu

    public List<Double> getHistory() { return priceHistory; }

    public void setHistory(List<Double> history) {
        priceHistory.clear();
        priceHistory.addAll(history);
    }

    public void onBuy(int amount) {
        double multiplier = 1.0 + ((changePercent / 100.0) * amount);
        this.price = this.price * multiplier;
    }

    public void onSell(int amount) {
        double multiplier = 1.0 - ((changePercent / 100.0) * amount);
        this.price = Math.max(0.01, this.price * multiplier);
    }
}