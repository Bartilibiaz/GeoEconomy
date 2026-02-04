package pl.bartilibiaz.market;

import org.bukkit.Material;
import java.util.LinkedList;
import java.util.List;

public class MarketItem {
    private final Material material;
    private double price;
    private final double changePercent;
    private final double sellRatio;

    private final LinkedList<Double> priceHistory = new LinkedList<>();

    public MarketItem(Material material, double startPrice, double changePercent, double sellRatio) {
        this.material = material;
        this.price = startPrice;
        this.changePercent = changePercent;
        this.sellRatio = sellRatio;
    }

    public void addHistoryPoint() {
        priceHistory.add(price);
        if (priceHistory.size() > 24) {
            priceHistory.removeFirst();
        }
    }

    public double getPrice24hAgo() {
        if (priceHistory.isEmpty()) return price;
        return priceHistory.getFirst();
    }

    public double getPrice1hAgo() {
        if (priceHistory.isEmpty()) return price;
        return priceHistory.getLast();
    }

    public double getChangeValue(boolean mode1h) {
        double oldPrice = mode1h ? getPrice1hAgo() : getPrice24hAgo();
        return price - oldPrice;
    }

    public double getChangePercent(boolean mode1h) {
        double oldPrice = mode1h ? getPrice1hAgo() : getPrice24hAgo();
        if (oldPrice == 0) return 0.0;
        return ((price - oldPrice) / oldPrice) * 100.0;
    }

    public Material getMaterial() { return material; }
    public double getBuyPrice() { return price; }
    public double getSellPrice() { return price * sellRatio; }
    public double getSellRatio() { return sellRatio; }
    public double getBasePrice() { return price; }

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