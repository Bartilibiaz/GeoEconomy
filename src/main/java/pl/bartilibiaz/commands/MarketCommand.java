package pl.bartilibiaz.commands;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.bartilibiaz.GeoEconomyPlugin;
import pl.bartilibiaz.gui.MarketGUI;

public class MarketCommand implements CommandExecutor {

    private final GeoEconomyPlugin plugin;

    public MarketCommand(GeoEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;

        if (args.length == 0) {
            new MarketGUI(plugin).openMainMenu(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("link")) {
            if (!player.hasPermission("geoeconomy.link")) {
                player.sendMessage("§cBrak uprawnień!");
                return true;
            }
            if (plugin.getDiscordManager().isLinked(player.getUniqueId())) {
                player.sendMessage("§cTwoje konto jest już połączone!");
                return true;
            }

            if (args.length < 2) {
                player.sendMessage("§cUżycie: /market link <TwojaNazwaNaDiscordzie>");
                player.sendMessage("§7Przykład: /market link bartek123");
                return true;
            }

            String discordName = args[1];
            player.sendMessage("§7Szukam użytkownika §e" + discordName + "§7 na serwerze Discord...");

            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                String result = plugin.getDiscordManager().sendLinkRequest(player, discordName);

                if (result == null) {
                    player.sendMessage("§cNie znaleziono użytkownika §e" + discordName + "§c na naszym Discordzie.");
                } else if (result.equals("ERR_ALREADY_LINKED")) {
                    player.sendMessage("§cTo konto Discord jest już połączone z innym graczem Minecraft!");
                    player.sendMessage("§7Jeśli to Twoje konto i chcesz je odłączyć, skontaktuj się z administracją.");
                } else if (result.equals("ERR_COOLDOWN")) {
                    player.sendMessage("§cZwolnij! Niedawno wysłano prośbę do tego użytkownika.");
                    player.sendMessage("§7Poczekaj 2 minuty przed kolejną próbą.");
                } else if (result.startsWith("ERR_")) {
                    player.sendMessage("§cWystąpił błąd techniczny: " + result);
                } else {
                    player.sendMessage(" ");
                    player.sendMessage("§8§m--------------------------------");
                    player.sendMessage("§a§lZNALEZIONO UŻYTKOWNIKA!");
                    player.sendMessage("§7Bot wysłał do Ciebie wiadomość prywatną.");
                    player.sendMessage(" ");
                    player.sendMessage("§7Twój tajny kod weryfikacyjny to:");
                    player.sendMessage("§e§l" + result);
                    player.sendMessage(" ");
                    player.sendMessage("§7Przepisz ten kod w wiadomości do bota!");
                    player.sendMessage("§8§m--------------------------------");
                }
            });

            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!player.hasPermission("geoeconomy.admin")) {
                player.sendMessage("§cBrak uprawnień!");
                return true;
            }

            player.sendMessage("§7Przeładowywanie konfiguracji...");

            plugin.reloadConfig();

            plugin.getMarketManager().reloadMarket();

            player.sendMessage("§aGeoEconomy został przeładowany!");
            return true;
        }
        if (args[0].equalsIgnoreCase("alert")) {
            if (!player.hasPermission("geoeconomy.alert")) {
                player.sendMessage("§cBrak uprawnień!");
                return true;
            }
            if (args.length < 3) {
                player.sendMessage("§cUżycie: /market alert <Przedmiot> <Cena>");
                return true;
            }

            if (!plugin.getDiscordManager().isLinked(player.getUniqueId())) {
                player.sendMessage("§cMusisz najpierw połączyć konto Discord! Wpisz /market link");
                return true;
            }

            try {
                Material mat = Material.valueOf(args[1].toUpperCase());
                double price = Double.parseDouble(args[2]);

                plugin.getAlertManager().addAlert(player.getUniqueId(), mat, price);
                player.sendMessage("§aUstawiono alert dla " + mat.name() + " na poziomie " + price + "$");

            } catch (Exception e) {
                player.sendMessage("§cBłędna nazwa przedmiotu lub cena.");
            }
            return true;
        }

        return true;
    }
}