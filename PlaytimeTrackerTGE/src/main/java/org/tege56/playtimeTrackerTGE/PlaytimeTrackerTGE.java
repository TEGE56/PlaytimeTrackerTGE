package org.tege56.playtimeTrackerTGE;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import me.clip.placeholderapi.PlaceholderAPI;
import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.tege56.playtimeTrackerTGE.storage.MySQLStorage;
import org.tege56.playtimeTrackerTGE.storage.SQLiteStorage;
import org.tege56.playtimeTrackerTGE.storage.StorageProvider;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PlaytimeTrackerTGE extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private final HashMap<UUID, Long> joinTimes = new HashMap<>();
    private final HashMap<UUID, Boolean> isAFK = new HashMap<>();
    private final HashMap<UUID, Long> lastMoveTime = new HashMap<>();
    private long afkTimeoutSeconds;
    private List<String> playtimeFormat;
    private String afkEnterMessage;
    private String afkExitMessage;
    private String firstJoinMessage;
    private final Set<UUID> knownPlayers = new HashSet<>();
    private AutoRankManager autoRankManager;
    private LuckPerms luckPerms;
    private StorageProvider storage;
    private boolean afkNotificationsEnabled;
    private int playtimeUpdateIntervalSeconds;
    private double afkMoveThreshold;
    private boolean useBungee;
    public static PlaytimeTrackerTGE instance;
    private final Set<String> recentlySentMessages = ConcurrentHashMap.newKeySet();
    private PluginMessageSender messageSender;
    private boolean firstJoinMessageEnabled;

    private final Set<String> knownUsernames = new HashSet<>();

    private void cacheKnownPlayers() {
        Set<UUID> uuids = storage.getAllPlayerUUIDs();
        knownPlayers.addAll(uuids);
    }

    private void cacheKnownUsernames() {
        Set<String> usernames = storage.getAllUsernames();
        knownUsernames.addAll(usernames);
    }

    @Override
    public void onEnable() {
        instance = this;

        loadPlugin();

        if (!setupDatabase()) return;
        if (!setupLuckPerms()) return;
        this.messageSender = new PluginMessageSender(this);
        this.autoRankManager = new AutoRankManager(this, luckPerms, this.storage, this.messageSender);

        getServer().getMessenger().registerOutgoingPluginChannel(this, "tege56:playtimetrackertgebungee");

        afkMoveThreshold = getConfig().getDouble("afk.moveThreshold", 0.2);
        useBungee = getConfig().getBoolean("use_bungee", true);
        this.firstJoinMessageEnabled = getConfig().getBoolean("enable_first_join_message", true);
        this.firstJoinMessage = getConfig().getString("first_join_message", "&e%player% joined the server for the first time!!");

        initializeManagers();
        setupPlaceholderAPI();
        registerEvents();
        setupCommands();
        startSchedulers();

        getLogger().info("\u001B[32mThe plugin started successfully!\u001B[0m");
    }

    private void loadPlugin() {
        saveDefaultConfig();

        File configFile = new File(getDataFolder(), "config.yml");
        ConfigUpdater.update(this, "config.yml", configFile);

        reloadConfig();
        getLogger().info("Config loaded.");

        loadConfiguration();
        getLogger().info("Settings loaded.");
    }

    private boolean setupDatabase() {
        FileConfiguration config = getConfig();

        if (config.getBoolean("database.use-mysql", false)) {
            MySQLStorage mysqlStorage = new MySQLStorage(this);
            storage = mysqlStorage;
            mysqlStorage.connect();
            getLogger().info("MySQL selected as the database.");
        } else {
            String sqliteFile = config.getString("sqlite.file", "playtimedata.db");
            SQLiteStorage sqliteStorage = new SQLiteStorage(this, sqliteFile);
            storage = sqliteStorage;
            sqliteStorage.connect();
            getLogger().info("SQLite selected as the database.");
        }

        if (!storage.ensureConnection()) {
            getLogger().severe("Database connection failed! Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }

        getLogger().info("Database connection successfully opened.");
        return true;
    }

    private boolean setupLuckPerms() {
        RegisteredServiceProvider<LuckPerms> provider = getServer().getServicesManager().getRegistration(LuckPerms.class);

        if (provider != null) {
            luckPerms = provider.getProvider();
            getLogger().info("LuckPerms found and initialized.");
            return true;
        } else {
            getLogger().severe("LuckPerms not found! Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }

    private void initializeManagers() {
        PluginMessageSender messageSender = new PluginMessageSender(this);
        this.autoRankManager = new AutoRankManager(this, luckPerms, storage, messageSender);
        getLogger().info("AutoRankManager initialized.");
    }

    private void setupPlaceholderAPI() {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PlaytimeExpansion(this, storage).register();
            getLogger().info("PlaceholderAPI found and expansion registered.");
        } else {
            getLogger().warning("PlaceholderAPI not found. Placeholders will not work.");
        }
    }

    private void registerEvents() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("Events registered.");
    }

    private void setupCommands() {
        getCommand("playtime").setExecutor(this);
        getCommand("playtime").setTabCompleter(this);
        getCommand("playtimetracker").setExecutor(this);
        getCommand("playtimetop").setExecutor(this);

        if (getCommand("importplaytimes") != null) {
            getCommand("importplaytimes").setExecutor(
                    new ImportPlayTimesCommand(this, autoRankManager, storage)
            );
        }
    }

    private void registerCommand(String name, CommandExecutor executor, TabCompleter tabCompleter) {
        PluginCommand command = Objects.requireNonNull(getCommand(name), "Command '" + name + "' not found in plugin.yml!");
        command.setExecutor(executor);
        if (tabCompleter != null) {
            command.setTabCompleter(tabCompleter);
        }
    }

    private void startSchedulers() {
        startAFKChecker();
        afkMoveThreshold = getConfig().getDouble("afk.moveThreshold", 0.2);
        startPlaytimeUpdater();
        cacheKnownPlayers();
        cacheKnownUsernames();
        getLogger().info("AFK checker, PlaytimeUpdater, and players cached.");

        startRankCheckTimer();
        getLogger().info("Rank check timer started.");
    }

    private void startRankCheckTimer() {
        long intervalMinutes = getConfig().getLong("rank_check_interval_minutes", 5);
        long intervalTicks = intervalMinutes * 60L * 20L;

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                autoRankManager.checkAndApplyRank(player.getUniqueId());
            }
        }, 0L, intervalTicks);
    }

    public boolean isUseBungee() {
        return useBungee;
    }

    @Override
    public void onDisable() {
        if (storage != null) {
            storage.closeConnection();
        }
    }

    private void loadConfiguration() {
        getConfig().options().copyDefaults(true);
        saveConfig();
        FileConfiguration config = getConfig();
        afkNotificationsEnabled = config.getBoolean("afk_notifications_enabled", true);
        afkTimeoutSeconds = config.getLong("afk_timeout_seconds", 300);
        playtimeFormat = config.getStringList("playtime_format");
        afkEnterMessage = config.getString("afk_enter_message", "&7%player% has gone AFK.");
        afkExitMessage = config.getString("afk_exit_message", "&7%player% is no longer AFK.");
        firstJoinMessage = config.getString("first_join_message", "&e%player% joined the server for the first time!");
        playtimeUpdateIntervalSeconds = config.getInt("playtime_update_interval_seconds", 60);
    }

    public AutoRankManager getAutoRankManager() {
        return autoRankManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        long now = Instant.now().getEpochSecond();

        lastMoveTime.put(uuid, now);
        isAFK.put(uuid, false);
        joinTimes.put(uuid, now);

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            if (storage.isFirstJoin(uuid)) {
                if (firstJoinMessageEnabled) {
                    String message = firstJoinMessage.replace("%player%", player.getName()).replace("&", "§");
                    sendMessageToServers(message);
                }

                try {
                    PreparedStatement ps = storage.prepareFirstJoinStatement(uuid, player.getName());
                    ps.executeUpdate();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void sendMessageToServers(String message) {
        if (recentlySentMessages.contains(message)) {
            return;
        }

        if (Bukkit.getOnlinePlayers().isEmpty()) {
            Bukkit.getLogger().info("[PlaytimeTrackerTGE] No online players – message not sent.");
            return;
        }

        if (PlaytimeTrackerTGE.instance.isUseBungee()) {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Message");
            out.writeUTF(message);

            Player player = Bukkit.getOnlinePlayers().iterator().next();
            player.sendPluginMessage(PlaytimeTrackerTGE.instance, "tege56:playtimetrackertgebungee", out.toByteArray());

            Bukkit.getLogger().info("[PlaytimeTrackerTGE] Sent plugin message to BungeeCord: " + message);
        } else {
            String colored = ChatColor.translateAlternateColorCodes('&', message);
            Bukkit.broadcastMessage(colored);
            Bukkit.getLogger().info("[PlaytimeTrackerTGE] Bungee is disabled – sent chat message only to this server: " + message);
        }

        recentlySentMessages.add(message);
        Bukkit.getScheduler().runTaskLater(PlaytimeTrackerTGE.instance, () -> recentlySentMessages.remove(message), 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!isAFK.getOrDefault(uuid, false)) {
            long joinTime = joinTimes.getOrDefault(uuid, Instant.now().getEpochSecond());
            long sessionTime = Instant.now().getEpochSecond() - joinTime;
            long minutes = sessionTime / 60;
            if (minutes > 0) {
                updatePlaytime(uuid, player.getName(), minutes);
            }
        }

        try (PreparedStatement ps = storage.getMainConnection().prepareStatement(
                "UPDATE playtime SET last_seen = ? WHERE uuid = ?"
        )) {
            long lastSeen = Instant.now().getEpochSecond();
            ps.setLong(1, lastSeen);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();

        } catch (SQLException e) {
            getLogger().severe("Error for update last_seen: " + e.getMessage());
            e.printStackTrace();
        }

        joinTimes.remove(uuid);
        isAFK.remove(uuid);
        lastMoveTime.remove(uuid);
    }

    private void exitAFK(Player player) {
        UUID uuid = player.getUniqueId();
        long now = Instant.now().getEpochSecond();

        lastMoveTime.put(uuid, now);

        if (isAFK.getOrDefault(uuid, false)) {
            isAFK.put(uuid, false);
            joinTimes.put(uuid, now);

            if (afkNotificationsEnabled) {
                String message = afkExitMessage.replace("%player%", player.getName()).replace("&", "§");
                player.sendMessage(message);
            }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (afkMoveThreshold <= 0) {
            getLogger().severe("Error: afkMoveThreshold on 0! Check your config.yml");
            return;
        }

        if (event.getFrom().getX() == event.getTo().getX() &&
                event.getFrom().getY() == event.getTo().getY() &&
                event.getFrom().getZ() == event.getTo().getZ()) {
            return;
        }

        double distanceSquared =
                Math.pow(event.getTo().getX() - event.getFrom().getX(), 2) +
                        Math.pow(event.getTo().getY() - event.getFrom().getY(), 2) +
                        Math.pow(event.getTo().getZ() - event.getFrom().getZ(), 2);

        if (distanceSquared > Math.pow(afkMoveThreshold, 2)) {
            exitAFK(player);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Bukkit.getScheduler().runTask(this, () -> exitAFK(event.getPlayer()));
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        exitAFK(event.getPlayer());
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        exitAFK(event.getPlayer());
    }

    private void startAFKChecker() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            long now = Instant.now().getEpochSecond();
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                long lastMove = lastMoveTime.getOrDefault(uuid, now);

                if (!isAFK.getOrDefault(uuid, false) && (now - lastMove) >= afkTimeoutSeconds) {
                    isAFK.put(uuid, true);

                    long join = joinTimes.getOrDefault(uuid, now);
                    long session = (now - join) / 60;
                    if (session > 0) {
                        updatePlaytime(uuid, player.getName(), session);
                    }

                    joinTimes.put(uuid, now);

                    if (afkNotificationsEnabled) {
                        String message = afkEnterMessage.replace("%player%", player.getName()).replace("&", "§");
                        player.sendMessage(message);
                    }
                }
            }
        }, 20L * 5, 20L * 5);
    }

    private void startPlaytimeUpdater() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            long now = Instant.now().getEpochSecond();
            Map<UUID, Long> updates = new HashMap<>();

            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                if (!isAFK.getOrDefault(uuid, false)) {
                    long join = joinTimes.getOrDefault(uuid, now);
                    long session = (now - join) / 60;
                    if (session > 0) {
                        updates.put(uuid, session);
                        joinTimes.put(uuid, now);
                    }
                }
            }

            updates.forEach((uuid, minutes) -> {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    updatePlaytime(uuid, player.getName(), minutes);
                }
            });

        }, 20L * playtimeUpdateIntervalSeconds, 20L * playtimeUpdateIntervalSeconds);
    }

    private void updatePlaytime(UUID uuid, String username, long minutes) {
        if (!storage.ensureConnection()) return;

        try {
            PreparedStatement ps = storage.preparePlaytimeStatement(uuid, username, minutes);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void resetPlaytime(String targetName) {
        if (!storage.ensureConnection()) return;
        try {
            PreparedStatement ps = storage.prepareMainStatement("UPDATE playtime SET play_minutes = 0 WHERE username = ?");
            ps.setString(1, targetName);
            int updated = ps.executeUpdate();
            if (updated > 0) {
                Bukkit.getConsoleSender().sendMessage("§aPlaytime has been reset for player " + targetName);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void showPlaytime(CommandSender sender, String targetName) {
        if (!storage.ensureConnection()) return;

        try {
            PreparedStatement ps = storage.prepareMainStatement(
                    "SELECT username, play_minutes, first_join, last_seen FROM playtime WHERE username = ?"
            );
            ps.setString(1, targetName);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                long minutes = rs.getLong("play_minutes");
                long hours = minutes / 60;
                long mins = minutes % 60;
                long firstJoin = rs.getLong("first_join");
                long lastSeen = rs.getLong("last_seen");

                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetName);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault());

                String firstJoinFormatted;
                if (firstJoin > 0) {
                    firstJoinFormatted = formatter.format(Instant.ofEpochSecond(firstJoin));
                } else if (offlinePlayer.hasPlayedBefore()) {
                    firstJoinFormatted = formatter.format(Instant.ofEpochMilli(offlinePlayer.getFirstPlayed()));
                } else {
                    firstJoinFormatted = PlaceholderAPI.setPlaceholders(offlinePlayer, "%player_first_join_date%");
                }

                String lastSeenFormatted;
                if (lastSeen > 0) {
                    lastSeenFormatted = formatter.format(Instant.ofEpochSecond(lastSeen));
                } else if (offlinePlayer.hasPlayedBefore()) {
                    lastSeenFormatted = formatter.format(Instant.ofEpochMilli(offlinePlayer.getLastPlayed()));
                } else {
                    lastSeenFormatted = PlaceholderAPI.setPlaceholders(offlinePlayer, "%player_last_join_date%");
                }

                for (String line : playtimeFormat) {
                    String msg = line.replace("%player%", targetName)
                            .replace("%hours%", String.valueOf(hours))
                            .replace("%minutes%", String.valueOf(mins))
                            .replace("%first_join_date%", firstJoinFormatted)
                            .replace("%last_seen_date%", lastSeenFormatted)
                            .replace("&", "§");
                    sender.sendMessage(msg);
                }
            } else {
                sender.sendMessage("§cPlayer was not found in the database.");
            }
        } catch (SQLException e) {
            sender.sendMessage("§cError while retrieving playtime.");
            e.printStackTrace();
        }
    }

    private void showTopPlaytime(CommandSender sender) {
        if (!storage.ensureConnection()) {
            sender.sendMessage(ChatColor.RED + "Could not verify database connection!");
            return;
        }

        int count = getConfig().getInt("top_playtime_count", 10);
        String format = getConfig().getString("top_playtime_format", "&e%rank%. &a%player%: %hours%h %minutes%min");
        String header = getConfig().getString("top_playtime_header", null);
        String footer = getConfig().getString("top_playtime_footer", null);

        List<String> lines = storage.getTopPlaytimeLines(count, format);

        if (lines.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No players found on the top list!");
            return;
        }

        if (header != null && !header.isEmpty()) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', header));
        }

        for (String line : lines) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', line));
        }

        if (footer != null && !footer.isEmpty()) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', footer));
        }
    }

    private void addPlaytime(String targetName, long minutesToAdd) {
        if (!storage.ensureConnection()) return;
        try {
            storage.addPlaytime(targetName, minutesToAdd);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void setPlaytime(String targetName, long newMinutes) {
        if (!storage.ensureConnection()) return;
        try {
            storage.setPlaytime(targetName, newMinutes);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            switch (command.getName().toLowerCase()) {
                case "playtime":
                    return handlePlaytimeCommand(sender, args);
                case "playtimetop":
                    showTopPlaytime(sender);
                    return true;
                case "playtimeadd":
                    return handlePlaytimeAddCommand(sender, args);
                case "playtimeset":
                    return handlePlaytimeSetCommand(sender, args);
                case "playtimereset":
                    return handlePlaytimeResetCommand(sender, args);
                case "playtimetracker":
                    return handlePlaytimeTrackerCommand(sender, args);
                case "importplaytimes":
                    return handleImportPlaytimesCommand(sender);
                default:
                    return false;
            }
        } catch (Exception e) {
            sender.sendMessage("§cAn error occurred while executing the command. Check the console!");
            Bukkit.getLogger().severe("Error executing command '" + command.getName() + "':");
            e.printStackTrace();
            return true;
        }
    }

    private boolean handlePlaytimeAddCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("playtime.add")) {
            sender.sendMessage("§cYou do not have permission to add playtime.");
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage("§cUsage: /playtimeadd <player> <minutes>");
            return true;
        }
        String playerName = args[0];
        try {
            long minutes = Long.parseLong(args[1]);
            addPlaytime(playerName, minutes);
            sender.sendMessage("§aAdded " + minutes + " minutes to player " + playerName);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cMinutes must be a number.");
        }
        return true;
    }

    private boolean handlePlaytimeSetCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("playtime.set")) {
            sender.sendMessage("§cYou do not have permission to set playtime.");
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage("§cUsage: /playtimeset <player> <minutes>");
            return true;
        }
        String playerName = args[0];
        try {
            long minutes = Long.parseLong(args[1]);
            setPlaytime(playerName, minutes);
            sender.sendMessage("§aSet playtime to " + minutes + " minutes for player " + playerName);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cMinutes must be a number.");
        }
        return true;
    }

    private boolean handlePlaytimeResetCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("playtime.reset")) {
            sender.sendMessage("§cYou do not have permission to reset playtime.");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage("§cUsage: /playtimereset <player>");
            return true;
        }
        String playerName = args[0];
        resetPlaytime(playerName);
        sender.sendMessage("§aPlaytime reset for player " + playerName);
        return true;
    }

    private boolean handlePlaytimeTrackerCommand(CommandSender sender, String[] args) {
        try {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("playtimetracker.reload")) {
                    sender.sendMessage("§cYou do not have permission to use this command.");
                    return true;
                }
                reloadConfig();
                loadConfiguration();
                sender.sendMessage("§aPlaytimeTracker configuration reloaded.");
                return true;
            }
            sender.sendMessage("§cUsage: /playtimetracker reload");
            return true;
        } catch (Exception e) {
            sender.sendMessage("§cAn error occurred during the reload command.");
            Bukkit.getLogger().severe("Error processing /playtimetracker reload command:");
            e.printStackTrace();
            return true;
        }
    }

    private boolean handlePlaytimeCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                showPlaytime((Player) sender, sender.getName());
            } else {
                sender.sendMessage("§cOnly players can use /playtime without arguments.");
            }
            return true;
        }
        if (args.length == 1) {
            showPlaytime(sender, args[0]);
            return true;
        }
        sender.sendMessage("§cInvalid usage! /playtime [player]");
        return true;
    }

    private boolean handleImportPlaytimesCommand(CommandSender sender) {
        try {
            if (!sender.hasPermission("playtimetracker.import")) {
                sender.sendMessage("§cYou do not have permission to import.");
                return true;
            }

            if (storage == null || storage.getPlayTimesConnection() == null) {
                sender.sendMessage("§cDatabase connection missing, cannot import PlayTimes data.");
                return true;
            }

            if (!(storage instanceof MySQLStorage)) {
                sender.sendMessage("§cImport is only supported when using MySQL storage.");
                return true;
            }

            PlayTimesMySQLStorage importStorage = new PlayTimesMySQLStorage(this);
            if (!importStorage.connect()) {
                sender.sendMessage("§cFailed to connect to import database.");
                return true;
            }

            autoRankManager.importPlayTimesDataWithAfk(importStorage, (MySQLStorage) storage);

            sender.sendMessage("§aPlayTimes data import started successfully!");
            return true;
        } catch (Exception e) {
            sender.sendMessage("§cAn error occurred during the import command.");
            Bukkit.getLogger().severe("Error processing /importplaytimes command:");
            e.printStackTrace();
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase();

        if (cmd.equals("playtimetracker") && args.length == 1) {
            return partialMatch(args[0], Collections.singletonList("reload"));
        }

        if (cmd.equals("playtime") && args.length == 1) {
            return partialMatch(args[0], getOnlinePlayerNames());
        }

        if (cmd.equals("playtimereset") && args.length == 1) {
            if (sender.hasPermission("playtime.reset")) {
                return partialMatch(args[0], getOnlinePlayerNames());
            }
        }

        if (cmd.equals("playtimeadd") && args.length == 1) {
            if (sender.hasPermission("playtime.add")) {
                return partialMatch(args[0], getOnlinePlayerNames());
            }
        }

        if (cmd.equals("playtimeadd") && args.length == 2) {
            if (sender.hasPermission("playtime.add")) {
                return partialMatch(args[1], Arrays.asList("60", "120", "300")); // Esimerkkiminuutteja
            }
        }

        if (cmd.equals("playtimeset") && args.length == 1) {
            if (sender.hasPermission("playtime.set")) {
                return partialMatch(args[0], getOnlinePlayerNames());
            }
        }

        if (cmd.equals("playtimeset") && args.length == 2) {
            if (sender.hasPermission("playtime.set")) {
                return partialMatch(args[1], Arrays.asList("60", "120", "300"));
            }
        }

        return Collections.emptyList();
    }

    private List<String> getOnlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .sorted()
                .collect(Collectors.toList());
    }

    private List<String> partialMatch(String arg, Collection<String> options) {
        if (arg == null || options == null) return Collections.emptyList();

        return options.stream()
                .filter(opt -> opt != null && opt.toLowerCase().startsWith(arg.toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
    }

    public boolean isAFK(UUID uuid) {
        return isAFK.getOrDefault(uuid, false);
    }
}
