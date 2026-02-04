package pl.bartilibiaz.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import pl.bartilibiaz.GeoEconomyPlugin;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

public class UpdateManager {

    private final GeoEconomyPlugin plugin;
    private final String REPO_OWNER = "Bartilibiaz";
    private final String REPO_NAME = "GeoEconomy";

    public UpdateManager(GeoEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void checkAndDownload() {
        if (!plugin.getConfig().getBoolean("settings.auto_update", true)) {
            return;
        }

        try {
            URL url = URI.create("https://api.github.com/repos/" + REPO_OWNER + "/" + REPO_NAME + "/releases/latest").toURL();

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "GeoEconomy-Updater");

            if (conn.getResponseCode() != 200) {
                plugin.getLogger().warning("Nie udało się sprawdzić aktualizacji. Kod: " + conn.getResponseCode());
                return;
            }

            InputStreamReader reader = new InputStreamReader(conn.getInputStream());
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

            String latestVersion = json.get("tag_name").getAsString();
            String currentVersion = plugin.getDescription().getVersion();

            latestVersion = latestVersion.replace("v", "");
            currentVersion = currentVersion.replace("v", "");

            if (latestVersion.equalsIgnoreCase(currentVersion)) {
                plugin.getLogger().info("Plugin jest aktualny (" + currentVersion + ").");
                return;
            }

            plugin.getLogger().info("Znaleziono nową wersję: " + latestVersion + " (Obecna: " + currentVersion + ")");

            String downloadUrl = json.getAsJsonArray("assets")
                    .get(0).getAsJsonObject()
                    .get("browser_download_url").getAsString();

            downloadFile(downloadUrl, latestVersion);

        } catch (Exception e) {
            plugin.getLogger().warning("Błąd podczas sprawdzania aktualizacji: " + e.getMessage());
        }
    }

    private void downloadFile(String fileUrl, String version) {
        File updateFolder = new File(plugin.getDataFolder().getParentFile(), "update");
        if (!updateFolder.exists()) {
            updateFolder.mkdirs();
        }

        File currentJar = plugin.getPluginFile();
        File targetFile = new File(updateFolder, currentJar.getName());

        plugin.getLogger().info("Pobieranie aktualizacji (" + version + ")...");

        try (BufferedInputStream in = new BufferedInputStream(URI.create(fileUrl).toURL().openStream());
             FileOutputStream fileOutputStream = new FileOutputStream(targetFile)) {

            byte[] dataBuffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }

            plugin.getLogger().info("============================================");
            plugin.getLogger().info(" SUKCES! Pobrano nową wersję GeoEconomy: " + version);
            plugin.getLogger().info(" Zrestartuj serwer, aby zainstalować.");
            plugin.getLogger().info("============================================");

        } catch (Exception e) {
            plugin.getLogger().severe("Błąd podczas pobierania pliku: " + e.getMessage());
        }
    }
}