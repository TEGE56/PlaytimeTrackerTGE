package org.tege56.playtimeTrackerTGE;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;

public class PlayTimesMySQLStorage {
    private final JavaPlugin plugin;
    private Connection connection;

    public PlayTimesMySQLStorage(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean connect() {
        try {
            String host = plugin.getConfig().getString("playtimes_import_mysql.host");
            int port = plugin.getConfig().getInt("playtimes_import_mysql.port");
            String db = plugin.getConfig().getString("playtimes_import_mysql.database");
            String user = plugin.getConfig().getString("playtimes_import_mysql.username");
            String pass = plugin.getConfig().getString("playtimes_import_mysql.password");

            String url = "jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=false&autoReconnect=true";

            connection = DriverManager.getConnection(url, user, pass);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Yhteys PlayTimes-tietokantaan epäonnistui: " + e.getMessage());
            return false;
        }
    }

    public ResultSet getPlaytimesData() throws SQLException {
        String sql = "SELECT uniqueId, SUM(playtime) AS totalPlaytime, SUM(afktime) AS totalAFKtime FROM playtimes GROUP BY uniqueId";
        Statement stmt = connection.createStatement();
        return stmt.executeQuery(sql);
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {}
    }
}
