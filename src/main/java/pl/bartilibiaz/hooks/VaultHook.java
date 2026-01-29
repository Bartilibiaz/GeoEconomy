package pl.bartilibiaz.hooks; // Zmiana paczki, bo jest w folderze hooks

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import pl.bartilibiaz.GeoEconomyPlugin; // <--- Importujemy główny plugin

import java.util.List;

public class VaultHook implements Economy {

    private final GeoEconomyPlugin plugin;

    public VaultHook(GeoEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isEnabled() { return true; }

    @Override
    public String getName() { return "GeoEconomy"; }

    @Override
    public boolean hasBankSupport() { return false; }

    @Override
    public int fractionalDigits() { return 2; }

    @Override
    public String format(double amount) {
        return String.format("%.2f $", amount);
    }

    @Override
    public String currencyNamePlural() { return "Monety"; }

    @Override
    public String currencyNameSingular() { return "Moneta"; }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return plugin.getEconomyManager().hasAccount(player.getUniqueId());
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return plugin.getEconomyManager().getBalance(player.getUniqueId());
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (plugin.getEconomyManager().withdraw(player.getUniqueId(), amount)) {
            return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, null);
        }
        return new EconomyResponse(0, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Brak srodkow");
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        plugin.getEconomyManager().deposit(player.getUniqueId(), amount);
        return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, null);
    }

    // --- NAPRAWIONE METODY ---

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        plugin.getEconomyManager().createAccount(player.getUniqueId());
        return true; // Zwracamy true (sukces), bo tak wymaga Vault
    }

    // Ta metoda też jest wymagana przez interfejs, nawet jeśli ignorujemy nazwę świata
    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    // --- METODY DEPRECATED / NIEUŻYWANE (Ale muszą być, żeby nie było błędu "abstract") ---

    @Override public boolean hasAccount(String playerName) { return false; }
    @Override public boolean hasAccount(String playerName, String worldName) { return false; }
    @Override public double getBalance(String playerName) { return 0; }
    @Override public double getBalance(String playerName, String world) { return 0; }
    @Override public boolean has(String playerName, double amount) { return false; }
    @Override public boolean has(String playerName, String worldName, double amount) { return false; }
    @Override public EconomyResponse withdrawPlayer(String playerName, double amount) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Not implemented"); }
    @Override public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Not implemented"); }
    @Override public EconomyResponse depositPlayer(String playerName, double amount) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Not implemented"); }
    @Override public EconomyResponse depositPlayer(String playerName, String worldName, double amount) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Not implemented"); }
    @Override public EconomyResponse createBank(String name, String player) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No banks"); }
    @Override public EconomyResponse createBank(String name, OfflinePlayer player) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No banks"); }
    @Override public EconomyResponse deleteBank(String name) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No banks"); }
    @Override public EconomyResponse bankBalance(String name) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No banks"); }
    @Override public EconomyResponse bankHas(String name, double amount) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No banks"); }
    @Override public EconomyResponse bankWithdraw(String name, double amount) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No banks"); }
    @Override public EconomyResponse bankDeposit(String name, double amount) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No banks"); }
    @Override public EconomyResponse isBankOwner(String name, String playerName) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No banks"); }
    @Override public EconomyResponse isBankOwner(String name, OfflinePlayer player) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No banks"); }
    @Override public EconomyResponse isBankMember(String name, String playerName) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No banks"); }
    @Override public EconomyResponse isBankMember(String name, OfflinePlayer player) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No banks"); }
    @Override public List<String> getBanks() { return null; }
    @Override public boolean createPlayerAccount(String playerName) { return false; }
    @Override public boolean createPlayerAccount(String playerName, String worldName) { return false; }
    @Override public boolean hasAccount(OfflinePlayer player, String worldName) { return hasAccount(player); }
    @Override public double getBalance(OfflinePlayer player, String world) { return getBalance(player); }
    @Override public boolean has(OfflinePlayer player, String worldName, double amount) { return has(player, amount); }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) { return withdrawPlayer(player, amount); }
    @Override public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) { return depositPlayer(player, amount); }
}