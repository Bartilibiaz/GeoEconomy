package pl.bartilibiaz.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import pl.bartilibiaz.GeoEconomyPlugin;
import pl.bartilibiaz.market.MarketItem;
import pl.bartilibiaz.market.PlayerProfile;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ProfileGUI implements Listener {

    private final GeoEconomyPlugin plugin;

    public ProfileGUI(GeoEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void openProfile(Player player) {
        String title = plugin.getLang().getMessage("gui.profile_title");
        Inventory inv = Bukkit.createInventory(null, 45, title);

        PlayerProfile profile = plugin.getProfileManager().getProfile(player.getUniqueId());

        // --- 1. GŁOWA GRACZA (Statystyki) ---
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
        skullMeta.setOwningPlayer(player);
        skullMeta.setDisplayName(title); // Używamy tytułu jako nazwy głowy

        double currentTotalValue = 0.0;
        double oldTotalValue = 0.0;

        for (Map.Entry<Material, Integer> entry : profile.getWallet().entrySet()) {
            Material mat = entry.getKey();
            int amount = entry.getValue();

            MarketItem marketItem = findMarketItem(mat);
            if (marketItem != null) {
                currentTotalValue += marketItem.getSellPrice() * amount;
                double oldPrice = marketItem.getPrice24hAgo();
                oldTotalValue += (oldPrice * marketItem.getSellRatio()) * amount;
            }
        }

        double diffVal = currentTotalValue - oldTotalValue;
        double diffPct = (oldTotalValue == 0) ? 0.0 : (diffVal / oldTotalValue) * 100.0;
        if (Double.isNaN(diffPct) || Double.isInfinite(diffPct)) diffPct = 0.0;
        String valStr = String.format("%.2f", diffVal);
        String pctStr = String.format("%.2f", diffPct);
        if (diffVal > 0) valStr = "+" + valStr;
        if (diffPct > 0) pctStr = "+" + pctStr;
        String trendColor;
        String trendSymbol;

        if (diffVal > 0) {
            trendColor = "§a"; // Zielony
            trendSymbol = "▲";
        } else if (diffVal < 0) {
            trendColor = "§c"; // Czerwony
            trendSymbol = "▼";
        } else {
            trendColor = "§7"; // Szary
            trendSymbol = "=";
        }
        // ----------------------

        String trendTxt = String.format("%s%s %s $ (%s%%)", trendColor, trendSymbol, valStr, pctStr);

        // Pobieramy lore z lang i podmieniamy zmienne ręcznie (jeśli chcesz) lub zostawiamy statyczne
        List<String> lore = plugin.getLang().getMessageList("gui.profile_lore");
        lore.add(" ");
        lore.add("§7Wartość aktywów: §6" + String.format("%.2f", currentTotalValue) + " $");
        lore.add("§7Zmiana 24h: " + trendTxt.replace("&", "§"));
        lore.add(" ");
        lore.add("§7Stan konta: §a" + String.format("%.2f", plugin.getEconomyManager().getBalance(player.getUniqueId())) + " $");

        skullMeta.setLore(lore);
        skull.setItemMeta(skullMeta);
        inv.setItem(4, skull);

        // --- 2. PRZEDMIOTY W PORTFELU ---
        int slot = 19;
        for (Map.Entry<Material, Integer> entry : profile.getWallet().entrySet()) {
            if (slot > 44) break; // Zabezpieczenie przed wyjściem poza GUI

            Material mat = entry.getKey();
            int amount = entry.getValue();

            if (amount <= 0) continue; // Nie pokazujemy zer

            MarketItem mItem = findMarketItem(mat);
            double singleValue = (mItem != null) ? mItem.getSellPrice() : 0.0;
            double totalItemValue = singleValue * amount;

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§b" + mat.name());
            meta.setLore(Arrays.asList(
                    " ",
                    "§7Ilość w portfelu: §f" + amount,
                    "§7Wartość (Sztuka): §a" + String.format("%.2f", singleValue) + " $",
                    "§7Wartość (Razem): §a" + String.format("%.2f", totalItemValue) + " $",
                    " ",
                    "§eKliknij, aby wypłacić do ekwipunku!"
            ));
            item.setItemMeta(meta);
            inv.setItem(slot, item);
            slot++;
        }

        fillBackground(inv);

        // Strzałka powrotu
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(plugin.getLang().getMessage("gui.back"));
        back.setItemMeta(backMeta);
        inv.setItem(36, back);

        // Info o wpłacaniu
        ItemStack info = new ItemStack(Material.HOPPER);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§a📥 Jak wpłacać?");
        infoMeta.setLore(Arrays.asList(
                "§7Kliknij przedmiot w swoim",
                "§7ekwipunku (na dole), aby",
                "§7wpłacić go do portfela."
        ));
        info.setItemMeta(infoMeta);
        inv.setItem(40, info);

        player.openInventory(inv);
    }

    // --- BLOKADA PRZECIĄGANIA (ANTY-KRADZIEŻ) ---
    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        String rawTitle = e.getView().getTitle();
        String expectedTitle = plugin.getLang().getMessage("gui.profile_title");

        // Jeśli tytuł pasuje (bez kolorów), blokujemy
        if (ChatColor.stripColor(rawTitle).equals(ChatColor.stripColor(expectedTitle))) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;

        // 1. Sprawdź tytuł okna (ignorując kolory)
        String rawTitle = e.getView().getTitle();
        String expectedTitle = plugin.getLang().getMessage("gui.profile_title");
        String cleanRaw = ChatColor.stripColor(rawTitle).trim();
        String cleanExp = ChatColor.stripColor(expectedTitle).trim();

        if (!cleanRaw.equals(cleanExp)) {
            return; // To nie nasze okno
        }

        // 2. BLOKADA! (Najważniejsze)
        e.setCancelled(true);

        Player p = (Player) e.getWhoClicked();
        ItemStack clickedItem = e.getCurrentItem();

        // Jeśli kliknął w puste pole -> ignoruj
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        PlayerProfile profile = plugin.getProfileManager().getProfile(p.getUniqueId());

        int clickedSlot = e.getRawSlot();
        int topSize = e.getView().getTopInventory().getSize(); // 45

        // --- SCENARIUSZ A: WPŁATA (Kliknięto w dolne okno / Ekwipunek gracza) ---
        if (clickedSlot >= topSize) {

            // Sprawdź czy przedmiot istnieje na rynku
            MarketItem mItem = findMarketItem(clickedItem.getType());
            if (mItem == null) {
                p.sendMessage("§cTego przedmiotu nie ma na giełdzie, nie możesz go wpłacić.");
                return;
            }

            int amount = clickedItem.getAmount();

            // Dodaj do bazy portfela
            profile.addItem(clickedItem.getType(), amount);

            // Usuń z ekwipunku gracza
            e.getCurrentItem().setAmount(0);

            p.sendMessage("§aWpłacono " + amount + "x " + clickedItem.getType());
            openProfile(p); // Odśwież widok
        }

        // --- SCENARIUSZ B: WYPŁATA (Kliknięto w górne okno / Portfel) ---
        else {
            // Obsługa przycisku "Wróć"
            if (clickedItem.getType() == Material.ARROW && e.getSlot() == 36) {
                new MarketGUI(plugin).openMainMenu(p);
                return;
            }

            // Obsługa wypłaty (ignorujemy tło, głowę i hopper)
            if (clickedItem.getType() == Material.GRAY_STAINED_GLASS_PANE ||
                    clickedItem.getType() == Material.PLAYER_HEAD ||
                    clickedItem.getType() == Material.HOPPER) {
                return;
            }

            Material mat = clickedItem.getType();
            int storedAmount = profile.getAmount(mat);

            if (storedAmount <= 0) return;

            // Sprawdź miejsce w ekwipunku
            if (p.getInventory().firstEmpty() == -1) {
                p.sendMessage(plugin.getLang().getMessage("messages.inventory_full"));
                return;
            }

            // Wypłać max stack (64)
            int toGive = Math.min(storedAmount, mat.getMaxStackSize());

            p.getInventory().addItem(new ItemStack(mat, toGive));
            profile.removeItem(mat, toGive);

            p.sendMessage("§eWypłacono " + toGive + "x " + mat);
            openProfile(p);
        }
    }

    private MarketItem findMarketItem(Material mat) {
        for (List<MarketItem> list : plugin.getMarketManager().getAllItemsValues()) {
            for (MarketItem item : list) {
                if (item.getMaterial() == mat) return item;
            }
        }
        return null;
    }

    private void fillBackground(Inventory inv) {
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = bg.getItemMeta();
        meta.setDisplayName(" ");
        bg.setItemMeta(meta);
        for(int i=0; i<inv.getSize(); i++) { if(inv.getItem(i) == null) inv.setItem(i, bg); }
    }
}