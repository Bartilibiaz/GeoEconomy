package pl.bartilibiaz.utils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.bartilibiaz.GeoEconomyPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class LanguageManager {

    private final GeoEconomyPlugin plugin;
    private FileConfiguration langConfig;
    private File langFile;

    public LanguageManager(GeoEconomyPlugin plugin) {
        this.plugin = plugin;
        loadLanguage();
    }

    public void loadLanguage() {
        String lang = plugin.getConfig().getString("settings.language", "pl");
        String fileName = "messages_" + lang + ".yml";

        langFile = new File(plugin.getDataFolder(), "lang/" + fileName);
        if (!langFile.exists()) {
            langFile.getParentFile().mkdirs();
            plugin.saveResource("lang/" + fileName, false);
        }

        langConfig = YamlConfiguration.loadConfiguration(langFile);

        updateFile(fileName);
    }

    private void updateFile(String resourceName) {
        InputStream defStream = plugin.getResource("lang/" + resourceName);
        if (defStream == null) return;

        YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
        boolean changesMade = false;

        Set<String> internalKeys = defConfig.getKeys(true);

        for (String key : internalKeys) {
            if (!langConfig.contains(key)) {
                langConfig.set(key, defConfig.get(key));
                changesMade = true;
                plugin.getLogger().info("Dodano nowy klucz do " + resourceName + ": " + key);
            }
        }

        if (changesMade) {
            try {
                langConfig.save(langFile);
                plugin.getLogger().info("Zaktualizowano plik językowy o nowe wpisy.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public String getMessage(String path) {
        if (!langConfig.contains(path)) {
            return "§cMissing key: " + path;
        }
        return listToString(langConfig.getStringList(path), langConfig.getString(path));
    }

    public List<String> getMessageList(String path) {
        if (!langConfig.contains(path)) {
            List<String> err = new ArrayList<>();
            err.add("§cMissing key: " + path);
            return err;
        }
        List<String> list = langConfig.getStringList(path);
        List<String> colored = new ArrayList<>();
        for (String s : list) {
            colored.add(s.replace("&", "§"));
        }
        return colored;
    }

    private String listToString(List<String> list, String single) {
        if (list != null && !list.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String s : list) {
                sb.append(s.replace("&", "§")).append("\n");
            }
            return sb.toString().trim();
        }
        return single != null ? single.replace("&", "§") : "§cError: " + single;
    }
}