package pl.bartilibiaz.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import pl.bartilibiaz.GeoEconomyPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.sql.PreparedStatement;
import java.sql.ResultSet;


public class DiscordManager extends ListenerAdapter {

    private final GeoEconomyPlugin plugin;
    private JDA jda;
    private String guildId;

    // Mapa: KodLinkowania -> UUID Gracza
    private final Map<String, UUID> pendingLinks = new ConcurrentHashMap<>();
    // Mapa: UUID Gracza -> Discord ID (Baza danych)
    private final Map<UUID, String> linkedAccounts = new ConcurrentHashMap<>();

    // ANTY-SPAM: Discord ID -> Czas ostatniej prośby (milisekundy)
    private final Map<String, Long> requestCooldowns = new ConcurrentHashMap<>();

    public DiscordManager(GeoEconomyPlugin plugin) {
        this.plugin = plugin;
        loadLinks();
        startBot();
    }

    private void startBot() {
        File file = new File(plugin.getDataFolder(), "discord.yml");
        if (!file.exists()) plugin.saveResource("discord.yml", false);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        String token = config.getString("bot_token");
        this.guildId = config.getString("guild_id");

        if (token == null || token.contains("TU_WKLEJ")) {
            plugin.getLogger().warning("Brak tokenu Discord!");
            return;
        }

        try {
            jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_MEMBERS)
                    .addEventListeners(this)
                    .build();
            plugin.getLogger().info("Bot Discord wystartowal!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopBot() {
        if (jda != null) jda.shutdown();
    }

    // --- LOGIKA WYSYŁANIA PROŚBY ---
    public String sendLinkRequest(Player player, String discordName) {
        if (jda == null) return "ERR_NO_BOT";
        if (guildId == null || guildId.isEmpty()) return "ERR_CONFIG";

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) return "ERR_NO_GUILD";

        // 1. Znajdź użytkownika
        List<Member> foundMembers = guild.getMembersByName(discordName, true);
        if (foundMembers.isEmpty()) foundMembers = guild.getMembersByNickname(discordName, true);
        if (foundMembers.isEmpty()) return null; // Nie znaleziono

        Member member = foundMembers.get(0);
        String discordId = member.getId();

        // 2. SPRAWDŹ CZY KONTO DISCORD JEST JUŻ ZAJĘTE (Relacja 1:1)
        if (isDiscordAccountLinked(discordId)) {
            return "ERR_ALREADY_LINKED"; // To konto jest już połączone z kimś innym!
        }

        // 3. ANTY-SPAM (Cooldown 2 minuty na tego użytkownika Discord)
        if (requestCooldowns.containsKey(discordId)) {
            long lastRequest = requestCooldowns.get(discordId);
            if (System.currentTimeMillis() - lastRequest < 120000) { // 120000 ms = 2 minuty
                return "ERR_COOLDOWN";
            }
        }
        requestCooldowns.put(discordId, System.currentTimeMillis());

        // 4. Wyślij prośbę
        String code = String.valueOf(new Random().nextInt(9000) + 1000);
        pendingLinks.put(code, player.getUniqueId());

        member.getUser().openPrivateChannel().queue(channel -> {
            channel.sendMessage("🔒 **Weryfikacja Konta**\n" +
                    "Gracz **" + player.getName() + "** chce połączyć to konto z serwerem Minecraft.\n" +
                    "Aby potwierdzić, **przepisz tutaj kod**, który wyświetlił Ci się na czacie w grze!").queue();
        }, error -> {
            plugin.getLogger().info("Nie udalo sie wyslac PW do: " + discordName);
        });

        return code;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (!event.isFromType(net.dv8tion.jda.api.entities.channel.ChannelType.PRIVATE)) return;

        String msg = event.getMessage().getContentRaw().trim();

        // 1. Sprawdzamy czy kod jest w mapie tymczasowej (RAM)
        if (pendingLinks.containsKey(msg)) {
            UUID uuid = pendingLinks.remove(msg);
            String discordId = event.getAuthor().getId();

            // 2. Sprawdzamy czy konto Discord nie jest już zajęte (w Bazie Danych)
            if (isDiscordAccountLinked(discordId)) {
                event.getChannel().sendMessage("❌ **Błąd!** To konto Discord jest już połączone z innym graczem.").queue();
                return;
            }

            // 3. Zapisujemy do Bazy Danych SQL
            saveLinkToDb(uuid, discordId);

            // 4. Sukces
            event.getChannel().sendMessage("✅ **Sukces!** Twoje konto Minecraft zostało połączone.").queue();

            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    p.sendMessage("§a§lSUKCES! §7Połączono z kontem: " + event.getAuthor().getName());
                    requestCooldowns.remove(discordId);
                }
            });
        }
    }

    // --- POMOCNICZE ---

    // Sprawdza, czy dany Discord ID jest już w bazie (odwrócone przeszukiwanie)
    public boolean isDiscordAccountLinked(String discordId) {
        String sql = "SELECT uuid FROM discord_links WHERE discord_id = ?";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, discordId);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void sendPrivateMessage(UUID uuid, String message) {
        String discordId = linkedAccounts.get(uuid);
        if (discordId == null || jda == null) return;
        jda.retrieveUserById(discordId).queue(user -> {
            user.openPrivateChannel().queue(channel -> channel.sendMessage(message).queue());
        });
    }

    public boolean isLinked(UUID uuid) {
        String sql = "SELECT discord_id FROM discord_links WHERE uuid = ?";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            return ps.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public String getBotId() {
        if (jda != null && jda.getSelfUser() != null) return jda.getSelfUser().getId();
        return null;
    }

    private void saveLinks() {
        File file = new File(plugin.getDataFolder(), "linked_accounts.yml");
        YamlConfiguration config = new YamlConfiguration();
        linkedAccounts.forEach((uuid, id) -> config.set(uuid.toString(), id));
        try { config.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    private void loadLinks() {
        File file = new File(plugin.getDataFolder(), "linked_accounts.yml");
        if (!file.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            linkedAccounts.put(UUID.fromString(key), config.getString(key));
        }
    }
    private void saveLinkToDb(UUID uuid, String discordId) {
        String sql = "INSERT OR REPLACE INTO discord_links (uuid, discord_id) VALUES (?, ?)";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, discordId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    public String getDiscordId(UUID uuid) {
        String sql = "SELECT discord_id FROM discord_links WHERE uuid = ?";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("discord_id");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}