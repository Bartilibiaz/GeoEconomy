package pl.bartilibiaz.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.bartilibiaz.GeoEconomyPlugin;
import pl.bartilibiaz.market.MarketItem;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class WebManager {

    private final GeoEconomyPlugin plugin;
    private HttpServer server;
    private int port = 8081; // Domyślny port

    public WebManager(GeoEconomyPlugin plugin) {
        this.plugin = plugin;
    }



    public void start() {
        if (!plugin.getConfig().getBoolean("web.enabled", true)) {
            return; // Jeśli wyłączone w configu, nie startuj
        }
        // Zapisz index.html z resources na dysk (żeby użytkownik mógł go edytować)
        saveDefaultWebFiles();

        // Wczytaj port z configu (warto dodać web_port: 8081 do config.yml)
        // Tutaj dla uproszczenia wpisane na sztywno lub pobierz z pluginu
        this.port = plugin.getConfig().getInt("web.port", 8081);

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);

            // Endpoint 1: Strona główna (HTML)
            server.createContext("/", new StaticFileHandler());

            // Endpoint 2: Dane JSON (API)
            server.createContext("/api/data", new ApiHandler());

            server.setExecutor(null);
            server.start();
            plugin.getLogger().info("Serwer WWW wystartowal na porcie: " + port);
            plugin.getLogger().info("Dostepny pod adresem: http://localhost:" + port);
        } catch (IOException e) {
            plugin.getLogger().severe("Nie udalo sie uruchomic serwera WWW! Port " + port + " moze byc zajety.");
            e.printStackTrace();
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            plugin.getLogger().info("Serwer WWW zatrzymany.");
        }
    }

    private void saveDefaultWebFiles() {
        File webFolder = new File(plugin.getDataFolder(), "web");
        if (!webFolder.exists()) {
            webFolder.mkdirs();
        }

        File index = new File(webFolder, "index.html");
        if (!index.exists()) {
            try {
                // Próba nr 1: Skopiuj z pliku .jar (z src/main/resources/web/index.html)
                plugin.saveResource("web/index.html", false);
            } catch (IllegalArgumentException e) {
                // Próba nr 2: Jeśli nie ma w .jar, stwórz nowy, domyślny plik
                plugin.getLogger().warning("Nie znaleziono web/index.html w pliku JAR. Tworzenie domyslnego pliku...");
                createFallbackIndex(index);
            }
        }
    }
    private void createFallbackIndex(File file) {
        try (FileWriter writer = new FileWriter(file)) {
            String defaultHtml = "<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "<head><title>GeoEconomy</title></head>\n" +
                    "<body style='background:#121212;color:white;font-family:sans-serif;text-align:center;padding:50px;'>\n" +
                    "<h1>GeoEconomy Web Interface</h1>\n" +
                    "<p>To jest domyslny plik generated przez plugin.</p>\n" +
                    "<p>Aby zobaczyc wykresy, podmien ten plik w folderze <code>plugins/GeoEconomy/web/index.html</code></p>\n" +
                    "</body>\n" +
                    "</html>";
            writer.write(defaultHtml);
        } catch (IOException e) {
            plugin.getLogger().severe("Nie udalo sie stworzyc awaryjnego pliku index.html!");
            e.printStackTrace();
        }
    }
    // --- HANDLER: Serwuje plik index.html ---
    class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            File file = new File(plugin.getDataFolder(), "web/index.html");
            if (!file.exists()) {
                String response = "Brak pliku index.html";
                t.sendResponseHeaders(404, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
                return;
            }

            byte[] bytes = Files.readAllBytes(file.toPath());
            t.sendResponseHeaders(200, bytes.length);
            OutputStream os = t.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    // --- HANDLER: Generuje JSON z danymi rynkowymi ---
    class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            // Budujemy JSON ręcznie (String), żeby nie dodawać zależności
            StringBuilder json = new StringBuilder();
            json.append("{");

            boolean first = true;
            for (List<MarketItem> list : plugin.getMarketManager().getAllItemsValues()) {
                for (MarketItem item : list) {
                    if (!first) json.append(",");
                    first = false;

                    json.append("\"").append(item.getMaterial().name()).append("\": {");
                    json.append("\"price\": ").append(item.getBasePrice()).append(",");

                    // Historia
                    json.append("\"history\": [");
                    List<Double> history = item.getHistory();
                    for (int i = 0; i < history.size(); i++) {
                        json.append(history.get(i));
                        if (i < history.size() - 1) json.append(",");
                    }
                    json.append("]");
                    json.append("}");
                }
            }
            json.append("}");

            byte[] response = json.toString().getBytes(StandardCharsets.UTF_8);

            // Nagłówki (JSON + CORS żeby działało z innych domen)
            t.getResponseHeaders().add("Content-Type", "application/json");
            t.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

            t.sendResponseHeaders(200, response.length);
            OutputStream os = t.getResponseBody();
            os.write(response);
            os.close();
        }
    }
}