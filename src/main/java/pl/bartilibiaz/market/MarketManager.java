package pl.bartilibiaz.market;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask; // Import Taska
import pl.bartilibiaz.GeoEconomyPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class MarketManager {

    private final GeoEconomyPlugin plugin;
    private final Map<String, List<MarketItem>> categories = new LinkedHashMap<>();
    private final Map<String, ItemStack> categoryIcons = new LinkedHashMap<>();
    private final Map<String, Integer> categorySlots = new HashMap<>();

    private BukkitTask historyTask; // Uchwyt do zadania, żeby móc je zrestartować

    public MarketManager(GeoEconomyPlugin plugin) {
        this.plugin = plugin;
        loadMarket();
        startHistoryTask();
    }

    // --- RELOAD ---
    public void reloadMarket() {
        saveMarket(); // Najpierw zapisz obecny stan (żeby nie stracić cen)
        loadMarket(); // Wczytaj config na nowo
        plugin.getLogger().info("Przeladowano rynek!");
    }

    private void startHistoryTask() {
        if (historyTask != null && !historyTask.isCancelled()) {
            historyTask.cancel();
        }

        // Pobieramy sekundy z configu (domyślnie 3600s = 1h)
        int seconds = plugin.getConfig().getInt("market.history_interval", 3600);
        long period = seconds * 20L; // Zamiana sekund na ticki (1 sekunda = 20 ticków)

        historyTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            plugin.getLogger().info("Aktualizacja historii cen rynkowych...");
            for (List<MarketItem> list : categories.values()) {
                for (MarketItem item : list) {
                    item.addHistoryPoint();
                }
            }
            saveMarket();
        }, period, period);
    }

    public void loadMarket() {
        File file = new File(plugin.getDataFolder(), "market.yml");
        if (!file.exists()) plugin.saveResource("market.yml", false);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        categories.clear();
        categoryIcons.clear();
        categorySlots.clear();

        ConfigurationSection catsSection = config.getConfigurationSection("categories");
        if (catsSection == null) return;

        for (String catKey : catsSection.getKeys(false)) {
            String name = catsSection.getString(catKey + ".name").replace("&", "§");
            String iconMat = catsSection.getString(catKey + ".icon");
            int slot = catsSection.getInt(catKey + ".slot");

            ItemStack icon = new ItemStack(Material.valueOf(iconMat));
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName(name);
            icon.setItemMeta(meta);

            categoryIcons.put(catKey, icon);
            categorySlots.put(catKey, slot);

            List<MarketItem> itemsList = new ArrayList<>();
            ConfigurationSection itemsSection = catsSection.getConfigurationSection(catKey + ".items");

            if (itemsSection != null) {
                for (String itemKey : itemsSection.getKeys(false)) {
                    double price = itemsSection.getDouble(itemKey + ".price");
                    double change = itemsSection.getDouble(itemKey + ".change_percent");
                    double defaultRatio = plugin.getConfig().getDouble("market.default_sell_ratio", 0.9);
                    double sellRatio = itemsSection.getDouble(itemKey + ".sell_ratio", defaultRatio);

                    // Wczytywanie listy Double
                    List<Double> history = itemsSection.getDoubleList(itemKey + ".history");

                    Material mat = Material.valueOf(itemKey);
                    MarketItem item = new MarketItem(mat, price, change, sellRatio);

                    // Ustawiamy historię tylko jeśli coś w niej jest
                    if (history != null && !history.isEmpty()) {
                        item.setHistory(history);
                    }

                    itemsList.add(item);
                }
            }
            categories.put(catKey, itemsList);
        }
    }

    public void saveMarket() {
        File file = new File(plugin.getDataFolder(), "market.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        for (Map.Entry<String, List<MarketItem>> entry : categories.entrySet()) {
            String catKey = entry.getKey();
            for (MarketItem item : entry.getValue()) {
                String path = "categories." + catKey + ".items." + item.getMaterial().name();

                config.set(path + ".price", item.getBasePrice());
                // Zapisujemy listę historii
                config.set(path + ".history", item.getHistory());
            }
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Blad zapisu market.yml!");
            e.printStackTrace();
        }
    }
    public String getCategoryIdByName(String guiDisplayName) {
        String cleanGuiName = org.bukkit.ChatColor.stripColor(guiDisplayName).trim();

        // Przeszukujemy wszystkie załadowane ikony kategorii
        for (Map.Entry<String, org.bukkit.inventory.ItemStack> entry : categoryIcons.entrySet()) {
            if (entry.getValue().getItemMeta() != null) {
                String iconName = entry.getValue().getItemMeta().getDisplayName();
                String cleanIconName = org.bukkit.ChatColor.stripColor(iconName).trim();

                // Jeśli nazwa z okna pasuje do nazwy ikony -> Mamy to!
                if (cleanGuiName.equalsIgnoreCase(cleanIconName)) {
                    return entry.getKey(); // Zwraca np. "food"
                }
            }
        }
        return null; // Nie znaleziono
    }
    public String getCategoryDisplayName(String categoryId) {
        if (categoryIcons.containsKey(categoryId)) {
            ItemStack icon = categoryIcons.get(categoryId);
            if (icon.getItemMeta() != null && icon.getItemMeta().hasDisplayName()) {
                return icon.getItemMeta().getDisplayName();
            }
        }
        return categoryId; // Fallback: jeśli nie ma nazwy, zwróć ID
    }
    // Gettery
    public Map<String, ItemStack> getCategoryIcons() { return categoryIcons; }
    public Integer getCategorySlot(String cat) { return categorySlots.get(cat); }
    public List<MarketItem> getItems(String category) { return categories.getOrDefault(category, new ArrayList<>()); }
    public String getCategoryIdBySlot(int slot) {
        for (Map.Entry<String, Integer> entry : categorySlots.entrySet()) {
            if (entry.getValue() == slot) return entry.getKey();
        }
        return null;
    }
    public Collection<List<MarketItem>> getAllItemsValues() { return categories.values(); }
}