package pl.bartilibiaz.utils;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.bartilibiaz.GeoEconomyPlugin;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

public class LanguageManager {

    private final GeoEconomyPlugin plugin;
    private YamlConfiguration langConfig;

    public LanguageManager(GeoEconomyPlugin plugin) {
        this.plugin = plugin;
        loadLanguage();
    }

    public void loadLanguage() {
        // 1. Upewnij się, że folder "lang" istnieje na serwerze
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs(); // Tworzy folder plugins/GeoEconomy/lang
        }

        // 2. Zapisz domyślne pliki (jeśli nie istnieją)
        // Ścieżka w saveResource musi odpowiadać ścieżce w src/main/resources
        saveDefaultLang("messages_pl.yml");
        saveDefaultLang("messages_en.yml");

        // 3. Pobierz ustawienie z config.yml
        String langCode = plugin.getConfig().getString("settings.language", "pl");
        String fileName = "messages_" + langCode + ".yml";

        // 4. Wczytaj plik z folderu "lang"
        File langFile = new File(langFolder, fileName);

        // Fallback: Jeśli plik wybranego języka nie istnieje, wczytaj polski
        if (!langFile.exists()) {
            plugin.getLogger().warning("Nie znaleziono pliku: lang/" + fileName + ". Ładowanie domyślnego (PL).");
            langFile = new File(langFolder, "messages_pl.yml");
        }

        langConfig = YamlConfiguration.loadConfiguration(langFile);
        plugin.getLogger().info("Wczytano plik językowy: lang/" + langFile.getName());
    }

    private void saveDefaultLang(String fileName) {
        // Sprawdzamy, czy plik istnieje w folderze plugins/GeoEconomy/lang/
        File langFolder = new File(plugin.getDataFolder(), "lang");
        File file = new File(langFolder, fileName);

        if (!file.exists()) {
            // saveResource szuka pliku w JARze pod ścieżką "lang/nazwa.yml"
            // i wypakowuje go do "plugins/GeoEconomy/lang/nazwa.yml"
            try {
                plugin.saveResource("lang/" + fileName, false);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().severe("Błąd: Nie znaleziono pliku 'lang/" + fileName + "' wewnątrz pliku .jar!");
            }
        }
    }

    // --- Pobieranie tekstów (Bez zmian) ---

    public String getMessage(String key) {
        if (langConfig == null) return "Error: Lang not loaded";
        String msg = langConfig.getString(key);
        if (msg == null) return "Missing key: " + key;
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    public List<String> getMessageList(String key) {
        if (langConfig == null) return List.of("Error: Lang not loaded");
        List<String> list = langConfig.getStringList(key);
        return list.stream()
                .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                .collect(Collectors.toList());
    }
}