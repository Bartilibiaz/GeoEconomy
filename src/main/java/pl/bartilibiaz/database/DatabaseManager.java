package pl.bartilibiaz.database;

import pl.bartilibiaz.GeoEconomyPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private final GeoEconomyPlugin plugin;
    private Connection connection;

    public DatabaseManager(GeoEconomyPlugin plugin) {
        this.plugin = plugin;
        connect();
        createTables();
    }

    private void connect() {
        try {
            File dataFolder = new File(plugin.getDataFolder(), "database.db");
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dataFolder.getAbsolutePath());
            plugin.getLogger().info("Połączono z bazą danych SQLite!");
        } catch (Exception e) {
            plugin.getLogger().severe("BŁĄD KRYTYCZNY: Nie można połączyć z bazą danych!");
            e.printStackTrace();
        }
    }

    private void createTables() {
        try (Statement st = connection.createStatement()) {
            // Tabela 1: Ekonomia (UUID, Kasa)
            st.execute("CREATE TABLE IF NOT EXISTS economy (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "balance DOUBLE NOT NULL DEFAULT 0)");

            // Tabela 2: Discord (UUID, DiscordID)
            st.execute("CREATE TABLE IF NOT EXISTS discord_links (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "discord_id VARCHAR(20) NOT NULL)");

            // Tabela 3: Portfel Inwestycyjny (UUID, Materiał, Ilość)
            // Używamy klucza złożonego (UUID + Materiał), żeby jeden gracz miał jeden wpis dla danego surowca
            st.execute("CREATE TABLE IF NOT EXISTS portfolios (" +
                    "uuid VARCHAR(36), " +
                    "material VARCHAR(50), " +
                    "amount INT, " +
                    "PRIMARY KEY (uuid, material))");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connect(); // Reconnect
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return connection;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}