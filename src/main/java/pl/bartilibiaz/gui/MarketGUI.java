package pl.bartilibiaz.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import pl.bartilibiaz.GeoEconomyPlugin;
import pl.bartilibiaz.market.MarketItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

public class MarketGUI implements Listener {

    private final GeoEconomyPlugin plugin;
    private final Map<UUID, Boolean> playerViewMode = new HashMap<>();

    public MarketGUI(GeoEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void openMainMenu(Player player) {
        String title = plugin.getLang().getMessage("gui.main_title");
        Inventory inv = Bukkit.createInventory(null, 27, title);

        ItemStack profileHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta headMeta = (SkullMeta) profileHead.getItemMeta();
        headMeta.setOwningPlayer(player);
        headMeta.setDisplayName(plugin.getLang().getMessage("gui.profile_title"));
        headMeta.setLore(plugin.getLang().getMessageList("gui.profile_lore"));
        profileHead.setItemMeta(headMeta);
        inv.setItem(4, profileHead);

        for (String catKey : plugin.getMarketManager().getCategoryIcons().keySet()) {
            ItemStack icon = plugin.getMarketManager().getCategoryIcons().get(catKey);
            int slot = plugin.getMarketManager().getCategorySlot(catKey);
            inv.setItem(slot, icon);
        }

        fillBackground(inv);
        player.openInventory(inv);
    }

    public void openCategory(Player player, String categoryId) {
        String prefix = plugin.getLang().getMessage("gui.category_prefix").replace("&1", "");

        String prettyName = plugin.getMarketManager().getCategoryDisplayName(categoryId);

        Inventory inv = Bukkit.createInventory(null, 54, prefix + prettyName);

        List<MarketItem> items = plugin.getMarketManager().getItems(categoryId);
        boolean is1hMode = playerViewMode.getOrDefault(player.getUniqueId(), false);

        int slot = 0;
        for (MarketItem item : items) {
            if (slot >= 53) break;
            ItemStack guiItem = new ItemStack(item.getMaterial());
            ItemMeta meta = guiItem.getItemMeta();
            String buyPrice = String.format("%.2f", item.getBuyPrice());
            String sellPrice = String.format("%.2f", item.getSellPrice());
            double changeVal = item.getChangeValue(is1hMode);
            double changePct = item.getChangePercent(is1hMode);
            if (Double.isNaN(changePct) || Double.isInfinite(changePct)) {
                changePct = 0.0;
            }
            String valStr = String.format("%.2f", changeVal);
            String pctStr = String.format("%.2f", changePct);
            String trendColor = changeVal >= 0 ? "&a" : "&c";
            String trendSymbol = changeVal >= 0 ? "▲" : "▼";
            if (changeVal > 0) {
                trendColor = "§a";
                trendSymbol = "▲";
            } else if (changeVal < 0) {
                trendColor = "§c";
                trendSymbol = "▼";
            } else {
                trendColor = "§7";
                trendSymbol = "=";
            }
            String trendFormat = is1hMode
                    ? plugin.getLang().getMessage("gui.item.trend_1h")
                    : plugin.getLang().getMessage("gui.item.trend_24h");
            String trendLine = trendFormat
                    .replace("%color%", trendColor)
                    .replace("%symbol%", trendSymbol)
                    .replace("%val%", valStr)
                    .replace("%pct%", pctStr);
            List<String> lore = new ArrayList<>();
            lore.add(" ");
            lore.add(plugin.getLang().getMessage("gui.item.buy_price").replace("%price%", buyPrice));
            lore.add(plugin.getLang().getMessage("gui.item.sell_price").replace("%price%", sellPrice));
            lore.add(" ");
            lore.add(trendLine);
            lore.addAll(plugin.getLang().getMessageList("gui.item.click_info"));
            meta.setLore(lore);
            guiItem.setItemMeta(meta);
            inv.setItem(slot, guiItem);
            slot++;
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta meta = back.getItemMeta();
        meta.setDisplayName(plugin.getLang().getMessage("gui.back"));
        back.setItemMeta(meta);
        inv.setItem(53, back);

        player.openInventory(inv);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        String title = ChatColor.stripColor(e.getView().getTitle());
        String mainTitle = ChatColor.stripColor(plugin.getLang().getMessage("gui.main_title"));
        String catPrefix = ChatColor.stripColor(plugin.getLang().getMessage("gui.category_prefix")).trim();

        if (title.equals(mainTitle) || title.startsWith(catPrefix)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (e.getClickedInventory() == null) return;

        Player player = (Player) e.getWhoClicked();

        String rawTitle = e.getView().getTitle();
        String cleanTitle = ChatColor.stripColor(rawTitle).trim();

        String cleanMainTitle = ChatColor.stripColor(plugin.getLang().getMessage("gui.main_title")).trim();

        String cleanPrefix = ChatColor.stripColor(plugin.getLang().getMessage("gui.category_prefix"))
                .replace("&1", "")
                .trim();

        boolean isMainMenu = cleanTitle.equals(cleanMainTitle);
        boolean isCategory = cleanTitle.startsWith(cleanPrefix);

        if (isMainMenu || isCategory) {
            e.setCancelled(true);
        } else {
            return;
        }

        if (isMainMenu) {
            if (e.getCurrentItem() == null) return;
            if (e.getSlot() == 4) {
                new ProfileGUI(plugin).openProfile(player);
                return;
            }
            String catId = plugin.getMarketManager().getCategoryIdBySlot(e.getSlot());
            if (catId != null) openCategory(player, catId);
        }
        else if (isCategory) {
            String displayCatName = cleanTitle.substring(cleanPrefix.length()).trim();

            String catId = plugin.getMarketManager().getCategoryIdByName(displayCatName);

            if (catId == null) {
                if (plugin.getMarketManager().getItems(displayCatName) != null && !plugin.getMarketManager().getItems(displayCatName).isEmpty()) {
                    catId = displayCatName; }
            }

            if (catId == null) {
                return;
            }

            if (e.getCurrentItem() == null) return;

            if (e.getSlot() == 53 && e.getCurrentItem().getType() == Material.ARROW) {
                openMainMenu(player);
                return;
            }

            if (e.getClick() == ClickType.MIDDLE) {
                boolean current = playerViewMode.getOrDefault(player.getUniqueId(), false);
                playerViewMode.put(player.getUniqueId(), !current);
                openCategory(player, catId);
                return;
            }

            List<MarketItem> items = plugin.getMarketManager().getItems(catId);
            if (e.getSlot() < items.size()) {
                MarketItem item = items.get(e.getSlot());
                handleTransaction(player, item, e.getClick());
                openCategory(player, catId);
            }
        }
    }

    private void handleTransaction(Player p, MarketItem item, ClickType click) {
        int amount = 1;
        boolean isBuying = true;
        if (click.isRightClick()) isBuying = false;
        if (click.isShiftClick()) amount = 64;

        double unitPrice = isBuying ? item.getBuyPrice() : item.getSellPrice();
        double totalPrice = unitPrice * amount;

        if (isBuying) {
            if (plugin.getEconomyManager().withdraw(p.getUniqueId(), totalPrice)) {
                HashMap<Integer, ItemStack> leftover = p.getInventory().addItem(new ItemStack(item.getMaterial(), amount));
                if (!leftover.isEmpty()) {
                    for (ItemStack drop : leftover.values()) p.getWorld().dropItemNaturally(p.getLocation(), drop);
                }
                item.onBuy(amount);
                String msg = plugin.getLang().getMessage("messages.bought")
                        .replace("%amount%", String.valueOf(amount))
                        .replace("%item%", item.getMaterial().name())
                        .replace("%price%", String.format("%.2f", totalPrice));
                p.sendMessage(msg);
            } else {
                p.sendMessage(plugin.getLang().getMessage("messages.no_money"));
            }
        } else {
            if (p.getInventory().contains(item.getMaterial(), amount)) {
                p.getInventory().removeItem(new ItemStack(item.getMaterial(), amount));
                plugin.getEconomyManager().deposit(p.getUniqueId(), totalPrice);
                item.onSell(amount);
                String msg = plugin.getLang().getMessage("messages.sold")
                        .replace("%amount%", String.valueOf(amount))
                        .replace("%item%", item.getMaterial().name())
                        .replace("%price%", String.format("%.2f", totalPrice));
                p.sendMessage(msg);
            } else {
                p.sendMessage(plugin.getLang().getMessage("messages.not_enough_items"));
            }
        }
    }

    private void fillBackground(Inventory inv) {
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = bg.getItemMeta();
        meta.setDisplayName(" ");
        bg.setItemMeta(meta);
        for(int i=0; i<inv.getSize(); i++) { if(inv.getItem(i) == null) inv.setItem(i, bg); }
    }
}