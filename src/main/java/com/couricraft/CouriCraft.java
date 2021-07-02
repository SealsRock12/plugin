package com.couricraft;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.couricraft.jda.JDAEvents;
import dev.thejakwe.tuinity.event.AnvilRenameEvent;
import dev.thejakwe.tuinity.event.MsgCommandEvent;
import dev.thejakwe.tuinity.event.SignTextEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.slf4j.Logger;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.List;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.logging.Level;

public final class CouriCraft extends JavaPlugin implements Listener {

    public static CouriCraft instance;
    public FileConfiguration config;
    public YamlConfiguration whitelist;
    public ProtocolManager protocolManager;
    public JDA jda;
    public Map<UUID, BukkitTask> tasks = new HashMap<>();
    private Logger logger;

    public final Function<String, String> automod = s -> {
        s = s.replaceAll("[^ -~]", ""); // all non ascii chars
        for (String regex : config.getStringList("automod")) {
            s = s.replaceAll(regex, "#"); // go thru each regex and replace with # to censor | regex not public dont bypass it Okayge
        }
        return s;
    };

    @Override
    public void onEnable() {
        instance = this;
        config = getConfig();
        protocolManager = ProtocolLibrary.getProtocolManager();
        whitelist = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "whitelist.yml"));
        logger = getSLF4JLogger();

        getServer().getPluginManager().registerEvents(this, this);

        try {
            jda = JDABuilder.createLight(config.getString("token"), GatewayIntent.GUILD_MESSAGES).addEventListeners(new JDAEvents(this)).build().awaitReady();
        } catch (Exception ex) {
            logger.error("JDA Build Exception", ex);
        }
        logger.info("CouriCraft Plugin Enabled!");
    }

    @Override
    public void onDisable() {
        jda.shutdown();
        getLogger().info("CouriCraft Plugin Disabled!");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void playerListPing(ServerListPingEvent event) {
        String motdString = "&5&lCouri&6&lCraft &7| &bdiscord&7.&egg&7/&dcouriway&r\n&a";
        List<String> suffix = config.getStringList("motds");
        motdString += suffix.get(ThreadLocalRandom.current().nextInt(suffix.size()));
        event.motd(LegacyComponentSerializer.legacyAmpersand().deserialize(motdString));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void incomingChatMessage(AsyncChatEvent event) {
        event.getPlayer().sendMessage(Component.text("In game chat is disabled. You can /msg a player or talk to them via Discord.", NamedTextColor.RED));
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void playerJoin(PlayerJoinEvent event) {
        event.joinMessage(null);
        tasks.put(event.getPlayer().getUniqueId(), Bukkit.getScheduler().runTaskTimer(this, () -> {
            event.getPlayer().sendPlayerListHeaderAndFooter(
                Component.text("Couri", NamedTextColor.GOLD, TextDecoration.BOLD).append(Component.text("Craft", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)),
                LegacyComponentSerializer.legacyAmpersand().deserialize("&6TPS: %s".formatted(Bukkit.getTPS()[0]))
            );
        }, 20, 20));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void playerLeave(PlayerQuitEvent event) {
        event.quitMessage(null);
        tasks.get(event.getPlayer().getUniqueId()).cancel();
    }

    @EventHandler
    public void msgCommand(MsgCommandEvent event) {
        UUID uuid = new UUID(0L, 0L);
        if (event.getSender() instanceof Player p) uuid = p.getUniqueId();

        jda.getTextChannelById(getConfig().getString("channels.messages")).sendMessageEmbeds(new EmbedBuilder()
            .setTitle("Message Sent")
            .setColor(Color.CYAN)
            .setAuthor(event.getSender().getName())
            .setFooter(event.getTarget().getName())
            .setDescription(((TextComponent) event.getMessage()).content())
            .setTimestamp(Instant.now())
            .addField("Author", "`%s`\n`%s`".formatted(event.getSender().getName(), uuid), false)
            .addField("Recipient", "`%s`\n`%s`".formatted(event.getTarget().getName(), event.getTarget().getUniqueId()), false)
            .build()
        ).complete();
    }

    @EventHandler
    public void itemRename(AnvilRenameEvent event) {
        event.name(automod.apply(event.name()));
    }

    @EventHandler
    public void signCreate(SignTextEvent event) {
        event.lines(Arrays.stream(event.lines()).map(automod).toArray(String[]::new));
    }
}
