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

    private static final List<Integer> DEPOSIT_SLOTS = Arrays.asList(37, 38, 39, 41, 42, 43);

    private static final List<Integer> WALLET_SLOTS = Arrays.asList(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    );

    public ProfileGUI(GeoEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void openProfile(Player player) {
        String title = plugin.getLang().getMessage("gui.profile_title");
        Inventory inv = Bukkit.createInventory(null, 45, title);

        PlayerProfile profile = plugin.getProfileManager().getProfile(player.getUniqueId());

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
        skullMeta.setOwningPlayer(player);
        skullMeta.setDisplayName(title);

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
        if (diffVal > 0) { trendColor = "§a"; trendSymbol = "▲"; }
        else if (diffVal < 0) { trendColor = "§c"; trendSymbol = "▼"; }
        else { trendColor = "§7"; trendSymbol = "="; }

        String trendTxt = String.format("%s%s %s $ (%s%%)", trendColor, trendSymbol, valStr, pctStr);

        List<String> lore = plugin.getLang().getMessageList("gui.profile_lore");
        lore.add(" ");
        lore.add("§7Wartość aktywów: §6" + String.format("%.2f", currentTotalValue) + " $");
        lore.add("§7Zmiana 24h: " + trendTxt.replace("&", "§"));
        lore.add(" ");
        lore.add("§7Stan konta: §a" + String.format("%.2f", plugin.getEconomyManager().getBalance(player.getUniqueId())) + " $");

        skullMeta.setLore(lore);
        skull.setItemMeta(skullMeta);
        inv.setItem(4, skull);

        inv.setItem(8, getDiscordIcon());

        int index = 0;
        for (Map.Entry<Material, Integer> entry : profile.getWallet().entrySet()) {
            if (index >= WALLET_SLOTS.size()) break;

            Material mat = entry.getKey();
            int amount = entry.getValue();
            if (amount <= 0) continue;

            MarketItem mItem = findMarketItem(mat);
            double singleValue = (mItem != null) ? mItem.getSellPrice() : 0.0;
            double totalItemValue = singleValue * amount;

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§b" + mat.name());
            meta.setLore(Arrays.asList(
                    " ",
                    "§7Ilość w portfelu: §f" + amount,
                    "§7Wartość: §a" + String.format("%.2f", totalItemValue) + " $",
                    " ",
                    "§eKliknij, aby wypłacić!"
            ));
            item.setItemMeta(meta);

            int targetSlot = WALLET_SLOTS.get(index);
            inv.setItem(targetSlot, item);
            index++;
        }

        fillBackground(inv);

        ItemStack depositGlass = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta depMeta = depositGlass.getItemMeta();
        depMeta.setDisplayName("§a⬇ WRZUĆ TUTAJ PRZEDMIOT ⬇");
        depositGlass.setItemMeta(depMeta);

        for (int depositSlot : DEPOSIT_SLOTS) {
            if (inv.getItem(depositSlot) == null || inv.getItem(depositSlot).getType() == Material.GRAY_STAINED_GLASS_PANE) {
                inv.setItem(depositSlot, depositGlass);
            }
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(plugin.getLang().getMessage("gui.back"));
        back.setItemMeta(backMeta);
        inv.setItem(36, back);

        ItemStack info = new ItemStack(Material.HOPPER);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§a📥 Depozyt");
        infoMeta.setLore(Arrays.asList(
                "§7Umieść przedmiot w",
                "§azielonych polach§7 obok,",
                "§7aby wpłacić go do portfela."
        ));
        info.setItemMeta(infoMeta);
        inv.setItem(40, info);

        player.openInventory(inv);
    }

    private ItemStack getDiscordIcon() {
        ItemStack item = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS);
        ItemMeta meta = item.getItemMeta();

        String name = plugin.getLang().getMessage("gui.discord_icon.name");
        if (name == null || name.startsWith("Missing key")) name = "§9Discord";

        meta.setDisplayName(name);

        List<String> lore = plugin.getLang().getMessageList("gui.discord_icon.lore");
        if (lore != null && !lore.isEmpty() && !lore.get(0).startsWith("Error")) {
            meta.setLore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        String rawTitle = e.getView().getTitle();
        String expectedTitle = plugin.getLang().getMessage("gui.profile_title");
        if (ChatColor.stripColor(rawTitle).equals(ChatColor.stripColor(expectedTitle))) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;

        String rawTitle = e.getView().getTitle();
        String expectedTitle = plugin.getLang().getMessage("gui.profile_title");
        String cleanRaw = ChatColor.stripColor(rawTitle).trim();
        String cleanExp = ChatColor.stripColor(expectedTitle).trim();

        if (!cleanRaw.equals(cleanExp)) return;

        e.setCancelled(true);

        Player p = (Player) e.getWhoClicked();
        ItemStack clickedItem = e.getCurrentItem();
        ItemStack cursorItem = e.getCursor();

        PlayerProfile profile = plugin.getProfileManager().getProfile(p.getUniqueId());
        int clickedSlot = e.getRawSlot();
        int topSize = e.getView().getTopInventory().getSize();

        if (clickedSlot == 8) {
            p.closeInventory();
            p.sendMessage("§8[§bDiscord§8] §7Generowanie kodu...");
            p.performCommand("market link");
            return;
        }

        if (DEPOSIT_SLOTS.contains(clickedSlot) && cursorItem != null && cursorItem.getType() != Material.AIR) {
            Material mat = cursorItem.getType();
            int amount = cursorItem.getAmount();

            MarketItem mItem = findMarketItem(mat);
            if (mItem == null) {
                p.sendMessage("§cTego przedmiotu nie ma na giełdzie.");
                return;
            }

            profile.addItem(mat, amount);
            p.setItemOnCursor(new ItemStack(Material.AIR));
            p.sendMessage("§aWpłacono " + amount + "x " + mat);
            openProfile(p);
            return;
        }

        if (clickedSlot >= topSize) {
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
            Material mat = clickedItem.getType();
            int amount = clickedItem.getAmount();

            MarketItem mItem = findMarketItem(mat);
            if (mItem != null) {
                profile.addItem(mat, amount);
                e.getCurrentItem().setAmount(0);
                p.sendMessage("§aWpłacono " + amount + "x " + mat);
                openProfile(p);
            }
            return;
        }

        if (clickedItem != null && WALLET_SLOTS.contains(clickedSlot)) {
            Material mat = clickedItem.getType();
            int storedAmount = profile.getAmount(mat);
            if (storedAmount <= 0) return;

            if (p.getInventory().firstEmpty() == -1) {
                p.sendMessage(plugin.getLang().getMessage("messages.inventory_full"));
                return;
            }

            int toGive = Math.min(storedAmount, mat.getMaxStackSize());
            p.getInventory().addItem(new ItemStack(mat, toGive));
            profile.removeItem(mat, toGive);

            p.sendMessage("§eWypłacono " + toGive + "x " + mat);
            openProfile(p);
        }

        if (clickedSlot == 36 && clickedItem != null && clickedItem.getType() == Material.ARROW) {
            new MarketGUI(plugin).openMainMenu(p);
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