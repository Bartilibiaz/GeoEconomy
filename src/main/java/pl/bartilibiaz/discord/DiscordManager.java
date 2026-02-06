package pl.bartilibiaz.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import pl.bartilibiaz.GeoEconomyPlugin;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class DiscordManager extends ListenerAdapter {

    private final GeoEconomyPlugin plugin;
    private JDA jda;

    private final Map<String, UUID> pendingCodes = new ConcurrentHashMap<>();

    private final Map<String, Long> requestCooldowns = new ConcurrentHashMap<>();

    public DiscordManager(GeoEconomyPlugin plugin) {
        this.plugin = plugin;
        startBot();

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, pendingCodes::clear, 6000L, 6000L);
    }

    private void startBot() {
        File file = new File(plugin.getDataFolder(), "discord.yml");
        if (!file.exists()) plugin.saveResource("discord.yml", false);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        String token = config.getString("bot_token");

        if (token == null || token.contains("TU_WKLEJ") || token.isEmpty()) {
            plugin.getLogger().warning("Brak tokenu Discord!");
            return;
        }

        try {
            jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .setChunkingFilter(ChunkingFilter.ALL)
                    .setActivity(Activity.playing("GeoEconomy"))
                    .addEventListeners(this)
                    .build();

            jda.updateCommands().addCommands(
                    Commands.slash("link", "Połącz swoje konto z Minecraft")
                            .addOption(OptionType.STRING, "kod", "Kod z gry (/market link)", true),

                    Commands.slash("profil", "Sprawdź stan konta gracza")
                            .addOption(OptionType.USER, "uzytkownik", "Wybierz użytkownika", false),

                    Commands.slash("alert", "Ustaw powiadomienie o cenie przedmiotu")
                            .addOption(OptionType.STRING, "przedmiot", "Np. DIAMOND, GOLD_INGOT", true)
                            .addOption(OptionType.NUMBER, "cena", "Powiadom, gdy cena spadnie poniżej tej kwoty", true)
            ).queue();

            plugin.getLogger().info("Bot Discord wystartowal!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopBot() {
        if (jda != null) jda.shutdown();
    }

    public String generateLinkCode(Player player) {
        if (isLinked(player.getUniqueId())) return "LINKED";
        String code = String.valueOf(new Random().nextInt(9000) + 1000);
        pendingCodes.put(code, player.getUniqueId());
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> pendingCodes.remove(code), 20 * 120L);
        return code;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        if (!event.isFromType(ChannelType.PRIVATE)) return;

        String msg = event.getMessage().getContentRaw().trim();

        if (pendingCodes.containsKey(msg)) {
            UUID uuid = pendingCodes.remove(msg);
            String discordId = event.getAuthor().getId();

            if (isDiscordAccountLinked(discordId)) {
                event.getChannel().sendMessage("❌ **Błąd!** To konto Discord jest już połączone z innym graczem.").queue();
                return;
            }

            saveLinkToDb(uuid, discordId);

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

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {

        if (event.getName().equals("link")) {
            String code = event.getOption("kod").getAsString();
            UUID playerUUID = pendingCodes.get(code);

            if (playerUUID == null) {
                event.reply("❌ Kod jest nieprawidłowy lub wygasł!").setEphemeral(true).queue();
                return;
            }

            if (isDiscordAccountLinked(event.getUser().getId())) {
                event.reply("❌ Twoje konto Discord jest już połączone!").setEphemeral(true).queue();
                return;
            }

            saveLinkToDb(playerUUID, event.getUser().getId());
            pendingCodes.remove(code);

            OfflinePlayer p = Bukkit.getOfflinePlayer(playerUUID);
            String playerName = (p.getName() != null) ? p.getName() : "Gracz";

            event.reply("✅ Sukces! Połączono konto z graczem **" + playerName + "**.").setEphemeral(true).queue();

            event.getUser().openPrivateChannel().queue(channel -> {
                channel.sendMessage("👋 **Cześć " + event.getUser().getName() + "!**\n" +
                        "Połączono konto! Możesz teraz używać komendy `/alert` na serwerze.").queue();
            }, error -> {});

            Player onlineP = p.getPlayer();
            if (onlineP != null) {
                onlineP.sendMessage("§a§lSUKCES! §7Połączono z kontem Discord: §e" + event.getUser().getName());
            }
            return;
        }

        if (event.getName().equals("alert")) {
            UUID uuid = getUuidByDiscordId(event.getUser().getId());
            if (uuid == null) {
                event.reply("❌ Musisz najpierw połączyć konto! Użyj `/market link` w grze.").setEphemeral(true).queue();
                return;
            }

            String itemInput = event.getOption("przedmiot").getAsString().toUpperCase();
            double price = event.getOption("cena").getAsDouble();

            Material mat = Material.matchMaterial(itemInput);
            if (mat == null) {
                event.reply("❌ Nie znaleziono przedmiotu o nazwie: **" + itemInput + "**").setEphemeral(true).queue();
                return;
            }

            plugin.getAlertManager().addAlert(uuid, mat, price);

            event.reply("🔔 Ustawiono alert dla **" + mat.name() + "**! Powiadomię Cię, gdy cena osiągnie **" + price + " $**.").queue();
            return;
        }

        if (event.getName().equals("profil")) {
            User targetUser;
            OptionMapping option = event.getOption("uzytkownik");
            if (option != null) targetUser = option.getAsUser();
            else targetUser = event.getUser();

            UUID uuid = getUuidByDiscordId(targetUser.getId());
            if (uuid == null) {
                event.reply("❌ Użytkownik **" + targetUser.getName() + "** nie połączył konta.").queue();
                return;
            }

            double balance = plugin.getEconomyManager().getBalance(uuid);
            String mcName = Bukkit.getOfflinePlayer(uuid).getName();

            event.reply("📊 **Profil gracza " + (mcName != null ? mcName : "Nieznany") + "**\n" +
                    "👤 Discord: " + targetUser.getAsMention() + "\n" +
                    "💰 Stan konta: **" + String.format("%.2f", balance) + " $**").queue();
        }
    }

    private void saveAlertToDb(UUID uuid, String material, double price) {
        String sql = "INSERT INTO market_alerts (uuid, material, target_price) VALUES (?, ?, ?)";
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, material);
                ps.setDouble(3, price);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
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

    public boolean isLinked(UUID uuid) {
        String sql = "SELECT discord_id FROM discord_links WHERE uuid = ?";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            return ps.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean isDiscordAccountLinked(String discordId) {
        String sql = "SELECT uuid FROM discord_links WHERE discord_id = ?";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, discordId);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }

    public String getDiscordId(UUID uuid) {
        String sql = "SELECT discord_id FROM discord_links WHERE uuid = ?";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("discord_id");
        } catch (SQLException e) {}
        return null;
    }

    public UUID getUuidByDiscordId(String discordId) {
        String sql = "SELECT uuid FROM discord_links WHERE discord_id = ?";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, discordId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return UUID.fromString(rs.getString("uuid"));
        } catch (SQLException e) {}
        return null;
    }

    public void sendPrivateMessage(UUID uuid, String message) {
        String discordId = getDiscordId(uuid);
        if (discordId == null || jda == null) return;
        jda.retrieveUserById(discordId).queue(user -> {
            user.openPrivateChannel().queue(channel -> channel.sendMessage(message).queue());
        }, error -> {});
    }

    public String sendLinkRequest(Player player, String discordName) {
        if (jda == null) return "ERR_NO_BOT";

        File file = new File(plugin.getDataFolder(), "discord.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String guildId = config.getString("guild_id");

        if (guildId == null || guildId.isEmpty()) return "ERR_CONFIG";

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) return "ERR_NO_GUILD";

        plugin.getLogger().info("DEBUG: Szukam użytkownika: " + discordName);

        List<Member> foundMembers = new java.util.ArrayList<>();

        foundMembers.addAll(guild.getMembersByName(discordName, true));
        if (foundMembers.isEmpty()) foundMembers.addAll(guild.getMembersByNickname(discordName, true));

        if (foundMembers.isEmpty()) {
            try {
                plugin.getLogger().info("DEBUG: Pytam API Discorda...");
                foundMembers = guild.retrieveMembersByPrefix(discordName, 10).get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (foundMembers == null || foundMembers.isEmpty()) {
            plugin.getLogger().info("DEBUG: Nikogo nie znaleziono.");
            return null;
        }

        Member member = foundMembers.get(0);
        String discordId = member.getId();

        if (isDiscordAccountLinked(discordId)) return "ERR_ALREADY_LINKED";

        if (requestCooldowns.containsKey(discordId)) {
            long lastRequest = requestCooldowns.get(discordId);
            if (System.currentTimeMillis() - lastRequest < 120000) return "ERR_COOLDOWN";
        }
        requestCooldowns.put(discordId, System.currentTimeMillis());

        String code = String.valueOf(new Random().nextInt(9000) + 1000);
        pendingCodes.put(code, player.getUniqueId());

        member.getUser().openPrivateChannel().queue(channel -> {
            channel.sendMessage("🔒 **Weryfikacja Konta**\n" +
                    "Gracz **" + player.getName() + "** chce połączyć to konto z serwerem Minecraft.\n" +
                    "Aby potwierdzić, **przepisz tutaj kod**, który wyświetlił Ci się na czacie w grze!").queue(
                    s -> plugin.getLogger().info("DEBUG: Wysłano kod do " + discordName),
                    e -> plugin.getLogger().warning("DEBUG: Nie można wysłać PW (DM zablokowane?)")
            );
        }, error -> plugin.getLogger().warning("DEBUG: Błąd otwierania kanału PW."));

        return code;
    }
}