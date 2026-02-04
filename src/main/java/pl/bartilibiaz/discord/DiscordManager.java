package pl.bartilibiaz.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import pl.bartilibiaz.GeoEconomyPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class DiscordManager extends ListenerAdapter {

    private final GeoEconomyPlugin plugin;
    private JDA jda;
    private String guildId;

    private final Map<String, UUID> pendingLinks = new ConcurrentHashMap<>();
    private final Map<UUID, String> linkedAccounts = new ConcurrentHashMap<>();
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
                    .enableIntents(GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .setChunkingFilter(ChunkingFilter.ALL)
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

    public String sendLinkRequest(Player player, String discordName) {
        if (jda == null) return "ERR_NO_BOT";
        if (guildId == null || guildId.isEmpty()) return "ERR_CONFIG";

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) return "ERR_NO_GUILD";

        List<Member> foundMembers = guild.getMembersByName(discordName, true);
        if (foundMembers.isEmpty()) foundMembers = guild.getMembersByNickname(discordName, true);

        if (foundMembers.isEmpty()) {
            try {
                foundMembers = guild.retrieveMembersByPrefix(discordName, 1).get();
            } catch (Exception e) {
            }
        }

        if (foundMembers.isEmpty()) return null;

        Member member = foundMembers.get(0);
        String discordId = member.getId();

        if (isDiscordAccountLinked(discordId)) {
            return "ERR_ALREADY_LINKED";
        }

        if (requestCooldowns.containsKey(discordId)) {
            long lastRequest = requestCooldowns.get(discordId);
            if (System.currentTimeMillis() - lastRequest < 120000) {
                return "ERR_COOLDOWN";
            }
        }
        requestCooldowns.put(discordId, System.currentTimeMillis());

        String code = String.valueOf(new Random().nextInt(9000) + 1000);
        pendingLinks.put(code, player.getUniqueId());

        member.getUser().openPrivateChannel().queue(channel -> {
            channel.sendMessage("🔒 **Weryfikacja Konta**\n" +
                    "Gracz **" + player.getName() + "** chce połączyć to konto z serwerem Minecraft.\n" +
                    "Aby potwierdzić, **przepisz tutaj kod**, który wyświetlił Ci się na czacie w grze!").queue();
        }, error -> {
            plugin.getLogger().info("Nie udalo sie wyslac PW do: " + discordName + " (Moze ma zablokowane DM?)");
        });

        return code;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (!event.isFromType(net.dv8tion.jda.api.entities.channel.ChannelType.PRIVATE)) return;

        String msg = event.getMessage().getContentRaw().trim();

        if (pendingLinks.containsKey(msg)) {
            UUID uuid = pendingLinks.remove(msg);
            String discordId = event.getAuthor().getId();

            if (isDiscordAccountLinked(discordId)) {
                event.getChannel().sendMessage("❌ **Błąd!** To konto Discord jest już połączone z innym graczem.").queue();
                return;
            }

            saveLinkToDb(uuid, discordId);
            saveLinks();

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
        String discordId = getDiscordId(uuid);
        if (discordId == null && linkedAccounts.containsKey(uuid)) discordId = linkedAccounts.get(uuid);

        if (discordId == null || jda == null) return;

        jda.retrieveUserById(discordId).queue(user -> {
            user.openPrivateChannel().queue(channel -> channel.sendMessage(message).queue());
        }, error -> {
        });
    }

    public boolean isLinked(UUID uuid) {
        String sql = "SELECT discord_id FROM discord_links WHERE uuid = ?";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            if (ps.executeQuery().next()) return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return linkedAccounts.containsKey(uuid);
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

            linkedAccounts.put(uuid, discordId);
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
        return linkedAccounts.get(uuid);
    }
}